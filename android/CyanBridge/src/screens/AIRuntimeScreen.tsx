import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {Card, Divider, ListRow, Reveal, Screen, SectionTitle, StatusPill, TopBar} from '../design/components';
import {IconName} from '../design/icons';
import {color, type} from '../design/tokens';
import {
  ADProductSettings,
  AIProfile,
  ConversationEngine,
  FileEngine,
  GroundingPolicy,
  ProductSettings,
  SpeechEngine,
  VisibleFallbackPolicy,
  VisionEngine,
} from '../native/ADProductSettings';
import {useProductSettings} from '../hooks/useProductSettings';

type Choice<T extends string> = {value: T; title: string; detail: string};
type CustomKey = 'conversationEngine' | 'speechEngine' | 'visionEngine' | 'fileEngine' | 'groundingPolicy' | 'visibleFallbackPolicy';

const profiles: Choice<AIProfile>[] = [
  {value: 'BALANCED', title: 'Balanced', detail: 'Screen-off first · choose the best available route automatically'},
  {value: 'FAST', title: 'Fast', detail: 'Prefer Gemini Live native audio and low-latency multimodal turns'},
  {value: 'PRIVATE', title: 'Private', detail: 'Moonshine + local models · no web grounding or visible fallback'},
];

const conversations: Choice<ConversationEngine>[] = [
  {value: 'AUTO', title: 'Auto', detail: 'Prefer Live when supported, then fall back without opening an app'},
  {value: 'GEMINI_LIVE', title: 'Gemini Live', detail: 'Streaming audio, vision, Search and AD tools in one bounded session'},
  {value: 'GEMINI_STANDARD', title: 'Gemini request', detail: 'One-shot model request for tasks that do not need a live session'},
  {value: 'LOCAL', title: 'Local AI', detail: 'Reason on this phone with the selected local/compatible model'},
];

const speech: Choice<SpeechEngine>[] = [
  {value: 'AUTO', title: 'Auto', detail: 'Use native Live audio online; local speech where it is more appropriate'},
  {value: 'GEMINI_NATIVE_AUDIO', title: 'Gemini native audio', detail: 'Skip a separate transcription stage for live cloud conversation'},
  {value: 'MOONSHINE', title: 'Moonshine', detail: 'Local transcription for private/offline questions and recordings'},
  {value: 'VOSK', title: 'Vosk', detail: 'Legacy offline fallback while Moonshine migration completes'},
];

const vision: Choice<VisionEngine>[] = [
  {value: 'AUTO', title: 'Auto', detail: 'Use a glasses frame with the best configured multimodal engine'},
  {value: 'GEMINI_LIVE', title: 'Gemini Live', detail: 'Inject glasses frames into the active voice session'},
  {value: 'GEMINI_STANDARD', title: 'Gemini request', detail: 'Analyze a captured image outside a live conversation'},
  {value: 'LOCAL_GEMMA', title: 'Local Gemma', detail: 'Use the phone-side local multimodal model when installed'},
];

const files: Choice<FileEngine>[] = [
  {value: 'AUTO', title: 'Auto', detail: 'Inline small inputs; use a file route for larger/reused media when configured'},
  {value: 'GEMINI_FILES', title: 'Gemini Files', detail: 'Upload larger documents/audio/video for temporary cloud inference'},
  {value: 'GEMINI_INLINE', title: 'Inline', detail: 'Send small images/files with the request without temporary file storage'},
  {value: 'LOCAL', title: 'Local only', detail: 'Keep file analysis on this phone when a compatible model supports it'},
];

const grounding: Choice<GroundingPolicy>[] = [
  {value: 'AUTO', title: 'Automatic', detail: 'Let AD use fresh Google-grounded information when the question needs it'},
  {value: 'ALWAYS', title: 'Prefer current info', detail: 'Request grounding for cloud questions whenever the engine supports it'},
  {value: 'NEVER', title: 'Never', detail: 'Do not use web grounding for assistant answers'},
];

const visibleFallback: Choice<VisibleFallbackPolicy>[] = [
  {value: 'ASK', title: 'Ask first', detail: 'Default · keep the screen dark unless Android truly needs visible interaction'},
  {value: 'NEVER', title: 'Never wake the screen', detail: 'Fail gracefully instead of using a visible app/accessibility fallback'},
  {value: 'ALLOW', title: 'Allow when needed', detail: 'Permit explicit visible fallbacks after background routes are exhausted'},
];

export function AIRuntimeScreen({back}: {back: () => void}) {
  const {settings, setSettings} = useProductSettings();

  const applyProfile = (value: AIProfile) => {
    ADProductSettings.setAiProfile(value);
    const preset = value === 'FAST'
      ? {conversationEngine: 'GEMINI_LIVE' as const, speechEngine: 'GEMINI_NATIVE_AUDIO' as const, visionEngine: 'GEMINI_LIVE' as const, fileEngine: 'GEMINI_INLINE' as const, groundingPolicy: 'AUTO' as const, visibleFallbackPolicy: 'ASK' as const}
      : value === 'PRIVATE'
        ? {conversationEngine: 'LOCAL' as const, speechEngine: 'MOONSHINE' as const, visionEngine: 'LOCAL_GEMMA' as const, fileEngine: 'LOCAL' as const, groundingPolicy: 'NEVER' as const, visibleFallbackPolicy: 'NEVER' as const}
        : {conversationEngine: 'AUTO' as const, speechEngine: 'AUTO' as const, visionEngine: 'AUTO' as const, fileEngine: 'AUTO' as const, groundingPolicy: 'AUTO' as const, visibleFallbackPolicy: 'ASK' as const};
    setSettings(current => ({...current, aiProfile: value, ...preset, screenOffFirst: preset.visibleFallbackPolicy !== 'ALLOW'}));
  };

  const custom = (key: CustomKey, value: string) => {
    switch (key) {
      case 'conversationEngine': ADProductSettings.setConversationEngine(value as ConversationEngine); break;
      case 'speechEngine': ADProductSettings.setSpeechEngine(value as SpeechEngine); break;
      case 'visionEngine': ADProductSettings.setVisionEngine(value as VisionEngine); break;
      case 'fileEngine': ADProductSettings.setFileEngine(value as FileEngine); break;
      case 'groundingPolicy': ADProductSettings.setGroundingPolicy(value as GroundingPolicy); break;
      case 'visibleFallbackPolicy': ADProductSettings.setVisibleFallbackPolicy(value as VisibleFallbackPolicy); break;
    }
    setSettings(current => ({
      ...current,
      aiProfile: 'CUSTOM',
      [key]: value,
      ...(key === 'visibleFallbackPolicy' ? {screenOffFirst: value !== 'ALLOW'} : {}),
    } as ProductSettings));
  };

  return (
    <Screen>
      <TopBar title="AI runtime" onBack={back}/>
      <Reveal>
        <View style={styles.intro}>
          <View style={styles.titleRow}>
            <Text style={styles.title}>Build your AD</Text>
            <StatusPill label={settings.screenOffFirst ? 'SCREEN-OFF FIRST' : 'VISIBLE FALLBACKS'} tone={settings.screenOffFirst ? 'success' : 'warning'}/>
          </View>
          <Text style={styles.lead}>These layers are independent. Change one without rewriting how your glasses, memory, Tasker or UI work.</Text>
        </View>
      </Reveal>

      <Reveal delay={35}><ChoiceSection title="Profiles" caption="Start simple, then override any layer" icon="spark" choices={profiles} selected={settings.aiProfile} onChoose={applyProfile}/></Reveal>
      <Reveal delay={65}><ChoiceSection title="Conversation" caption="How a live glasses question is reasoned about" icon="mic" choices={conversations} selected={settings.conversationEngine} onChoose={value => custom('conversationEngine', value)}/></Reveal>
      <Reveal delay={95}><ChoiceSection title="Speech input" caption="Transcription is optional for native-audio Live sessions" icon="wave" choices={speech} selected={settings.speechEngine} onChoose={value => custom('speechEngine', value)}/></Reveal>
      <Reveal delay={125}><ChoiceSection title="Vision" caption="Frames come from the glasses, not the phone camera UI" icon="eye" choices={vision} selected={settings.visionEngine} onChoose={value => custom('visionEngine', value)}/></Reveal>
      <Reveal delay={155}><ChoiceSection title="Files & artifacts" caption="Original captures stay in your Library; this controls AI analysis transport" icon="library" choices={files} selected={settings.fileEngine} onChoose={value => custom('fileEngine', value)}/></Reveal>
      <Reveal delay={185}><ChoiceSection title="Fresh information" caption="Search is a reasoning tool, not a separate screen" icon="web" choices={grounding} selected={settings.groundingPolicy} onChoose={value => custom('groundingPolicy', value)}/></Reveal>
      <Reveal delay={215}><ChoiceSection title="Visible fallback" caption="What AD may do after native/background routes are exhausted" icon="shield" choices={visibleFallback} selected={settings.visibleFallbackPolicy} onChoose={value => custom('visibleFallbackPolicy', value)}/></Reveal>
    </Screen>
  );
}

function ChoiceSection<T extends string>({title, caption, icon, choices, selected, onChoose}: {title: string; caption: string; icon: IconName; choices: Choice<T>[]; selected: T; onChoose: (value: T) => void}) {
  return (
    <View>
      <SectionTitle caption={caption}>{title}</SectionTitle>
      <Card style={{marginTop: 10}}>
        {choices.map((choice, index) => (
          <React.Fragment key={choice.value}>
            <ListRow icon={icon} title={choice.title} detail={choice.detail} selected={selected === choice.value} onPress={() => onChoose(choice.value)}/>
            {index < choices.length - 1 ? <Divider/> : null}
          </React.Fragment>
        ))}
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  intro: {gap: 8},
  titleRow: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12},
  title: {...type.display, color: color.ink, flex: 1},
  lead: {...type.body, color: color.grey700, maxWidth: 380},
});
