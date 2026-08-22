from pathlib import Path

script_path = Path('tools/tmp_mainactivity_audit_phase2.py')
script = script_path.read_text(encoding='utf-8')
old = '''replace_once(
    \'\'\'            val manager = getOrCreateMetaRaybanManager()\n            lifecycleScope.launch(Dispatchers.IO) {\n\'\'\',
    \'\'\'            prepareAiQuestionForLockScreen()\n            beginAiQuestionForegroundWork(\n                "Capturing image from Meta glasses",\n                usesPhoneMicrophone = pendingImageQuestionOfferSpokenQuestion &&\n                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,\n            )\n            val manager = getOrCreateMetaRaybanManager()\n            lifecycleScope.launch(Dispatchers.IO) {\n\'\'\',
    \'Meta image foreground ownership\',
)
'''
new = '''old_meta_ai = \'\'\'            val manager = getOrCreateMetaRaybanManager()\n            lifecycleScope.launch(Dispatchers.IO) {\n                runCatching {\n                    val photo = manager.capturePhotoOnce()\n                    manager.savePhotoForProcessing(photo, "META_AI_$sourceTag")\n\'\'\'\nnew_meta_ai = \'\'\'            prepareAiQuestionForLockScreen()\n            beginAiQuestionForegroundWork(\n                "Capturing image from Meta glasses",\n                usesPhoneMicrophone = pendingImageQuestionOfferSpokenQuestion &&\n                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,\n            )\n            val manager = getOrCreateMetaRaybanManager()\n            lifecycleScope.launch(Dispatchers.IO) {\n                runCatching {\n                    val photo = manager.capturePhotoOnce()\n                    manager.savePhotoForProcessing(photo, "META_AI_$sourceTag")\n\'\'\'\nreplace_once(old_meta_ai, new_meta_ai, \'Meta AI image foreground ownership\')\n'''
if old not in script:
    raise SystemExit('Could not locate the broad Meta transform in phase two')
exec(script.replace(old, new, 1), {})
