#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "android/AD-Glasses/app/src/main/java/com/ad_glasses/localagent/LocalAgentService.kt"
BUILD = ROOT / "android/AD-Glasses/app/build.gradle"

service = SERVICE.read_text(encoding="utf-8")
original_service = service

service = service.replace("import android.speech.tts.TextToSpeech\n", "")
service = service.replace("import android.speech.tts.UtteranceProgressListener\n", "")
service = service.replace("import java.util.Locale\n", "")

import_anchor = "import com.ad_glasses.R\n"
voice_imports = (
    "import com.ad_glasses.ai.voice.KokoroSpeechService\n"
    "import com.ad_glasses.ai.voice.SpeechCallbacks\n"
    "import com.ad_glasses.ai.voice.SpeechQueueMode\n"
)
if voice_imports not in service:
    if import_anchor not in service:
        raise SystemExit("Could not find LocalAgentService import anchor")
    service = service.replace(import_anchor, import_anchor + voice_imports, 1)

service = service.replace(
    "    private var tts: TextToSpeech? = null\n"
    "    private var ttsReady: CompletableDeferred<Boolean>? = null\n",
    "",
    1,
)

old_destroy = '''        serviceScope.cancel()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = null
        super.onDestroy()
'''
new_destroy = '''        serviceScope.cancel()
        KokoroSpeechService.get(applicationContext).stop()
        AudioSessionCoordinator.markIdle()
        super.onDestroy()
'''
if old_destroy not in service:
    raise SystemExit("Could not find LocalAgentService Android speech teardown")
service = service.replace(old_destroy, new_destroy, 1)

old_init_call = '''        runtimeInitialized = true
        initTts()
        LocalAgentMemoryStore.ensureSeedFiles(applicationContext)
'''
new_init_call = '''        runtimeInitialized = true
        KokoroSpeechService.get(applicationContext).prepare(
            onError = { error -> Log.w(TAG, "Kokoro preparation failed; speech will retry on demand", error) },
        )
        LocalAgentMemoryStore.ensureSeedFiles(applicationContext)
'''
if old_init_call not in service:
    raise SystemExit("Could not find LocalAgentService speech init call")
service = service.replace(old_init_call, new_init_call, 1)

speech_block = re.compile(
    r"    private fun initTts\(\) \{.*?\n    private fun buildNotification\(content: String\): Notification \{",
    re.DOTALL,
)
new_speech_block = '''    private suspend fun speakBestEffort(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || !LocalAgentDeviceState.isReady(applicationContext)) return

        KokoroSpeechService.get(applicationContext).speak(
            text = clean,
            queueMode = SpeechQueueMode.FLUSH,
            utteranceId = "local_agent_demo_${System.currentTimeMillis()}",
            callbacks = SpeechCallbacks(
                onError = { error -> Log.w(TAG, "Kokoro speech failed", error) },
            ),
        )
    }

    private suspend fun speakAndWaitBestEffort(text: String, maxLength: Int = 160) {
        val clean = text.trim().take(maxLength)
        if (clean.isBlank() || !LocalAgentDeviceState.isReady(applicationContext)) return

        val engine = KokoroSpeechService.get(applicationContext)
        val utteranceId = "local_agent_result_${System.currentTimeMillis()}"
        val completed = CompletableDeferred<Unit>()
        AudioSessionCoordinator.markBusy()
        engine.speak(
            text = clean,
            queueMode = SpeechQueueMode.FLUSH,
            utteranceId = utteranceId,
            callbacks = SpeechCallbacks(
                onDone = { if (!completed.isCompleted) completed.complete(Unit) },
                onStopped = { if (!completed.isCompleted) completed.complete(Unit) },
                onError = { error ->
                    Log.w(TAG, "Kokoro speech failed", error)
                    if (!completed.isCompleted) completed.complete(Unit)
                },
            ),
        )

        try {
            repeat(32) {
                if (!LocalAgentDeviceState.isReady(applicationContext)) {
                    engine.stop()
                    return
                }
                if (withTimeoutOrNull(250L) { completed.await() } != null) return
            }
            Log.w(TAG, "Kokoro speech exceeded local-agent wait window; continuing")
        } finally {
            AudioSessionCoordinator.markIdle()
        }
    }

    private fun buildNotification(content: String): Notification {'''
service, count = speech_block.subn(new_speech_block, service, count=1)
if count != 1:
    raise SystemExit(f"Expected one LocalAgentService Android speech block, replaced {count}")

forbidden = [
    r"android\.speech\.tts",
    r"\bTextToSpeech\b",
    r"\bUtteranceProgressListener\b",
    r"\btts\b",
    r"\bttsReady\b",
    r"\binitTts\b",
]
leftovers = []
for lineno, line in enumerate(service.splitlines(), start=1):
    if any(re.search(pattern, line) for pattern in forbidden):
        leftovers.append(f"{lineno}: {line}")
if leftovers:
    print("Residual Android platform speech references in LocalAgentService:")
    print("\n".join(leftovers[:120]))
    raise SystemExit(2)

if service != original_service:
    SERVICE.write_text(service, encoding="utf-8")
    print(f"Migrated {SERVICE.relative_to(ROOT)} to Kokoro speech")
else:
    print("LocalAgentService already migrated")

# The MYVU submodule is kept pristine, but we compile a generated copy of its Java source with the
# upstream platform-TTS player omitted. AD Glasses supplies a protocol-compatible TtsPlayer in
# src/main/java/com/myvu/client/ai that delegates local speech to Kokoro.
build = BUILD.read_text(encoding="utf-8")
original_build = build

vars_anchor = '''def myvuJavaSourceDir = file("src/main/myvu-upstream/android/app/src/main/java")
def myvuAssetsSourceDir = file("src/main/myvu-upstream/android/app/src/main/assets")
def myvuEnabled = new File(myvuJavaSourceDir, "com/myvu/client/service/ConnectionManager.java").exists()
'''
vars_replacement = '''def myvuJavaSourceDir = file("src/main/myvu-upstream/android/app/src/main/java")
def myvuAssetsSourceDir = file("src/main/myvu-upstream/android/app/src/main/assets")
def myvuGeneratedJavaSourceDir = layout.buildDirectory.dir("generated/myvu-upstream/java")
def myvuEnabled = new File(myvuJavaSourceDir, "com/myvu/client/service/ConnectionManager.java").exists()

def syncMyvuSources = tasks.register("syncMyvuSources", Sync) {
    onlyIf { myvuEnabled }
    from(myvuJavaSourceDir) {
        exclude("com/myvu/client/ui/**")
        exclude("com/myvu/client/service/MyvuService.java")
        exclude("com/myvu/client/service/MirrorNotificationListener.java")
        exclude("com/myvu/client/ai/TtsPlayer.java")
    }
    into(myvuGeneratedJavaSourceDir)
}
'''
if vars_replacement not in build:
    if vars_anchor not in build:
        raise SystemExit("Could not find MYVU source variables in app/build.gradle")
    build = build.replace(vars_anchor, vars_replacement, 1)

old_source = '''            if (myvuEnabled) {
                java.srcDir(myvuJavaSourceDir)
                assets.srcDir(myvuAssetsSourceDir)
                java.exclude("com/myvu/client/ui/**")
                java.exclude("com/myvu/client/service/MyvuService.java")
                java.exclude("com/myvu/client/service/MirrorNotificationListener.java")
            } else {
'''
new_source = '''            if (myvuEnabled) {
                java.srcDir(myvuGeneratedJavaSourceDir)
                assets.srcDir(myvuAssetsSourceDir)
            } else {
'''
if new_source not in build:
    if old_source not in build:
        raise SystemExit("Could not find MYVU source-set block in app/build.gradle")
    build = build.replace(old_source, new_source, 1)

old_prebuild = '''tasks.named("preBuild").configure {
    dependsOn(syncSharedComposeResources)
}
'''
new_prebuild = '''tasks.named("preBuild").configure {
    dependsOn(syncSharedComposeResources)
    if (myvuEnabled) dependsOn(syncMyvuSources)
}
'''
if new_prebuild not in build:
    if old_prebuild not in build:
        raise SystemExit("Could not find preBuild dependency block")
    build = build.replace(old_prebuild, new_prebuild, 1)

if "exclude(\"com/myvu/client/ai/TtsPlayer.java\")" not in build:
    raise SystemExit("MYVU upstream TtsPlayer exclusion was not installed")
if "java.srcDir(myvuGeneratedJavaSourceDir)" not in build:
    raise SystemExit("MYVU generated Java source directory was not installed")

if build != original_build:
    BUILD.write_text(build, encoding="utf-8")
    print("Configured MYVU generated source without upstream Android TTS player")
else:
    print("MYVU source replacement already configured")
