import React, {useEffect, useMemo, useState} from 'react';
import {StyleSheet, Text, TextInput, View} from 'react-native';
import {
  Card,
  Divider,
  GlassesImage,
  HeroTitle,
  IconTile,
  ListRow,
  PressableScale,
  PrimaryButton,
  Reveal,
  Screen,
  SectionTitle,
  StatusPill,
  TopBar,
} from '../design/components';
import {Icon, IconName} from '../design/icons';
import {color, radius, space, type} from '../design/tokens';
import {ADNative, DashboardState, fallbackDashboard} from '../native/ADNative';
import type {Navigate} from '../navigation/routes';

export function HomeScreen({navigate}: {navigate: Navigate}) {
  const [state, setState] = useState<DashboardState>(fallbackDashboard);
  useEffect(() => { ADNative.dashboard().then(setState); }, []);
  const status = state.connecting ? 'Connecting' : state.connected ? 'Connected' : 'Disconnected';
  return (
    <Screen>
      <Reveal><View style={styles.brandRow}><Text style={styles.brand}>AD GLASSES</Text><PressableScale onPress={() => navigate('settings')} style={styles.roundButton}><Icon name="settings" size={19}/></PressableScale></View></Reveal>
      <Reveal delay={40}>
        <Card onPress={() => navigate('device')} style={styles.deviceHero}>
          <View style={styles.heroGlass}><GlassesImage height={150}/></View>
          <View style={styles.deviceStatusRow}>
            <View style={[styles.dot, {backgroundColor: state.connected ? color.success : color.grey500}]}/>
            <View style={{flex: 1}}><Text style={styles.deviceTitle}>{status}</Text><Text style={styles.meta}>{state.connected ? state.deviceName : 'Your glasses are ready when you are'}</Text></View>
            {state.connected && state.batteryPercent != null ? <Text style={styles.metric}>{state.batteryPercent}%</Text> : <Text style={styles.connectText}>Connect</Text>}
          </View>
        </Card>
      </Reveal>
      <View style={styles.grid}>
        <ActionTile icon="mic" title="Ask" detail="Voice question" onPress={() => ADNative.action('voiceQuestion')}/>
        <ActionTile icon="camera" title="Photo" detail="Capture now" onPress={() => ADNative.action('capturePhoto')}/>
        <ActionTile icon="video" title="Video" detail="Record from glasses" onPress={() => ADNative.action('toggleVideo')}/>
        <ActionTile icon="translate" title="Translate" detail="Live translation" onPress={() => navigate('capability', {capability: 'Translate'})}/>
      </View>
      <Card>
        <ListRow icon="eye" title="Ask what I see" detail="Use the glasses camera with AI" onPress={() => ADNative.action('imageQuestion')}/><Divider/>
        <ListRow icon="wave" title="Record audio" detail="Save an audio recording" onPress={() => ADNative.action('startRecording')}/><Divider/>
        <ListRow icon="web" title="Search web" detail="Fresh web-backed question" onPress={() => navigate('prompt', {web: true})}/><Divider/>
        <ListRow icon="bolt" title="Automation" detail="Apps and supported Android actions" onPress={() => navigate('capability', {capability: 'Automation'})}/>
      </Card>
    </Screen>
  );
}

function ActionTile({icon, title, detail, onPress}: {icon: IconName; title: string; detail: string; onPress: () => void}) {
  return <PressableScale onPress={onPress} style={styles.actionTile}><IconTile name={icon}/><View style={{marginTop: 20}}><Text style={styles.tileTitle}>{title}</Text><Text style={styles.meta}>{detail}</Text></View></PressableScale>;
}

export function PromptScreen() {
  const [value, setValue] = useState('');
  const [messages, setMessages] = useState<{role: 'user' | 'assistant'; text: string}[]>([]);
  const send = () => {
    const text = value.trim(); if (!text) return;
    setMessages(current => [...current, {role: 'user', text}, {role: 'assistant', text: 'The React Native prompt surface is ready for the native assistant bridge.'}]);
    setValue('');
  };
  return (
    <Screen scroll={false} style={{flex: 1}}>
      <View style={styles.promptHeader}><IconTile name="terminal"/><View style={{flex: 1}}><Text style={styles.promptTitle}>Prompt</Text><Text style={styles.meta}>Ask AI from your phone or glasses</Text></View><PressableScale style={styles.newPrompt} onPress={() => setMessages([])}><Icon name="plus" size={16}/><Text style={styles.newPromptText}>New</Text></PressableScale></View>
      <View style={styles.promptBody}>
        {messages.length === 0 ? <View style={styles.emptyPrompt}><View style={styles.promptHeroIcon}><Icon name="terminal" size={28}/></View><Text style={styles.emptyTitle}>What do you want to know?</Text><Text style={styles.emptyBody}>Use the phone when you want detail. Start on the glasses when you want speed.</Text><View style={{gap: 8, width: '100%', marginTop: 18}}><Suggestion text="What did I capture today?" onPress={() => setValue('What did I capture today?')}/><Suggestion text="Search the web for something current" onPress={() => setValue('Search the web for ')}/><Suggestion text="Help me plan something" onPress={() => setValue('Help me plan ')}/></View></View> : <View style={{gap: 16}}>{messages.map((m, i) => <View key={i} style={[styles.message, m.role === 'user' && styles.userMessage]}><Text style={styles.messageText}>{m.text}</Text></View>)}</View>}
      </View>
      <View style={styles.composer}><TextInput placeholder="Ask AI…" placeholderTextColor={color.grey500} value={value} onChangeText={setValue} onSubmitEditing={send} style={styles.composerInput}/><PressableScale onPress={send} style={styles.send}><Icon name="send" size={18} stroke={color.white}/></PressableScale></View>
    </Screen>
  );
}

function Suggestion({text, onPress}: {text: string; onPress: () => void}) { return <PressableScale onPress={onPress} style={styles.suggestion}><Icon name="terminal" size={17}/><Text style={{...type.body, flex: 1, color: color.ink}}>{text}</Text><Icon name="chevron" size={17} stroke={color.grey500}/></PressableScale>; }

const capabilities: {title: string; detail: string; icon: IconName}[] = [
  {title: 'Translate', detail: 'Live translation', icon: 'translate'},
  {title: 'Soundbites', detail: 'Audio to notes', icon: 'wave'},
  {title: 'Timeline', detail: 'Searchable visual memory', icon: 'timeline'},
  {title: 'DayNote', detail: 'Daily moments, distilled', icon: 'book'},
  {title: 'Cron', detail: 'Recurring scheduled work', icon: 'repeat'},
  {title: 'Automation', detail: 'Apps & Android actions', icon: 'bolt'},
];

export function AIScreen({navigate}: {navigate: Navigate}) {
  const [provider, setProvider] = useState('Gemini');
  return (
    <Screen>
      <Reveal><HeroTitle>Your AI</HeroTitle><Text style={styles.pageLead}>Choose how your glasses think, then shape what they can do.</Text></Reveal>
      <View><SectionTitle caption="Things your glasses can do">Capabilities</SectionTitle><View style={[styles.grid, {marginTop: 10}]}>{capabilities.map(item => <PressableScale key={item.title} onPress={() => navigate('capability', {capability: item.title})} style={styles.capability}><IconTile name={item.icon}/><View style={{marginTop: 22}}><Text style={styles.tileTitle}>{item.title}</Text><Text style={styles.meta} numberOfLines={2}>{item.detail}</Text></View></PressableScale>)}</View></View>
      <View><SectionTitle caption={`Current route · ${provider}`}>Default AI</SectionTitle><Card style={{marginTop: 10}}>{['Gemini','OpenAI / Codex','Local AI'].map((name, index) => <React.Fragment key={name}><ListRow icon={name === 'Local AI' ? 'computer' : 'cloud'} title={name} detail={name === 'Local AI' ? 'Run a configured model on this phone' : 'Use your configured relay'} selected={provider === name} onPress={() => setProvider(name)}/>{index < 2 ? <Divider/> : null}</React.Fragment>)}</Card></View>
      <View><SectionTitle>Connections</SectionTitle><Card style={{marginTop: 10}}><ListRow icon="spark" title="Assistant apps" detail="Optional Gemini or ChatGPT app handoff" onPress={() => navigate('assistant-apps')}/><Divider/><ListRow icon="cloud" title="Relay" detail="Server, backend and web access" onPress={() => navigate('relay')}/><Divider/><ListRow icon="computer" title="Local & compatible models" detail="On-device files and compatible endpoints" onPress={() => navigate('local-ai')}/></Card></View>
    </Screen>
  );
}

export function LibraryScreen({navigate}: {navigate: Navigate}) {
  return (
    <Screen>
      <Reveal><HeroTitle>Library</HeroTitle><Text style={styles.pageLead}>Everything your glasses remembered, recorded or captured lives here.</Text></Reveal>
      <View style={styles.collectionGrid}><Collection icon="image" title="Captures" detail="Photos & video" onPress={() => navigate('captures')}/><Collection icon="wave" title="Recordings" detail="Audio & transcripts" onPress={() => navigate('recordings')}/></View>
      <Collection icon="note" title="Notes & summaries" detail="Soundbites, DayNote and generated notes" onPress={() => navigate('notes')} wide/>
      <Card><View style={styles.syncCard}><IconTile name="sync"/><View style={{flex: 1}}><Text style={styles.tileTitle}>Sync from glasses</Text><Text style={styles.meta}>Bring new captures onto this phone</Text></View><Icon name="chevron" size={18} stroke={color.grey500}/></View></Card>
    </Screen>
  );
}

function Collection({icon, title, detail, onPress, wide}: {icon: IconName; title: string; detail: string; onPress: () => void; wide?: boolean}) {
  return <PressableScale onPress={onPress} style={[styles.collection, wide && {width: '100%'}]}><IconTile name={icon}/><View style={{marginTop: 26}}><Text style={styles.collectionTitle}>{title}</Text><Text style={styles.meta}>{detail}</Text></View></PressableScale>;
}

const styles = StyleSheet.create({
  brandRow: {height: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'},
  brand: {...type.section, letterSpacing: 2.4, color: color.ink},
  roundButton: {width: 38, height: 38, borderRadius: 19, backgroundColor: color.surface, alignItems: 'center', justifyContent: 'center'},
  deviceHero: {padding: 0, overflow: 'hidden'},
  heroGlass: {height: 168, backgroundColor: color.grey100, justifyContent: 'center', paddingHorizontal: 14},
  deviceStatusRow: {minHeight: 70, paddingHorizontal: space.md, flexDirection: 'row', alignItems: 'center', gap: 10},
  dot: {width: 8, height: 8, borderRadius: 4},
  deviceTitle: {...type.cardTitle, color: color.ink},
  meta: {...type.meta, color: color.grey700},
  metric: {...type.meta, color: color.ink},
  connectText: {...type.meta, color: color.ink},
  grid: {flexDirection: 'row', flexWrap: 'wrap', gap: 10},
  actionTile: {width: '48.5%', minHeight: 126, borderRadius: radius.card, backgroundColor: color.surface, padding: space.md, borderWidth: 1, borderColor: color.grey200},
  tileTitle: {...type.cardTitle, color: color.ink},
  promptHeader: {height: 64, flexDirection: 'row', alignItems: 'center', gap: 11},
  promptTitle: {...type.title, color: color.ink},
  newPrompt: {height: 36, paddingHorizontal: 10, borderRadius: 12, backgroundColor: color.grey100, flexDirection: 'row', alignItems: 'center', gap: 5},
  newPromptText: {...type.meta, color: color.ink},
  promptBody: {flex: 1, paddingTop: 10},
  emptyPrompt: {flex: 1, alignItems: 'center', justifyContent: 'center', paddingBottom: 20},
  promptHeroIcon: {width: 64, height: 64, borderRadius: 20, backgroundColor: color.grey100, alignItems: 'center', justifyContent: 'center'},
  emptyTitle: {...type.title, color: color.ink, marginTop: 18},
  emptyBody: {...type.body, color: color.grey700, textAlign: 'center', maxWidth: 330, marginTop: 7},
  suggestion: {minHeight: 52, borderRadius: radius.control, backgroundColor: color.surface, paddingHorizontal: 14, flexDirection: 'row', alignItems: 'center', gap: 10, borderWidth: 1, borderColor: color.grey200},
  message: {maxWidth: '88%', alignSelf: 'flex-start', paddingHorizontal: 14, paddingVertical: 11},
  userMessage: {alignSelf: 'flex-end', backgroundColor: color.grey100, borderRadius: 18},
  messageText: {...type.body, color: color.ink},
  composer: {minHeight: 66, marginHorizontal: -4, marginBottom: 4, padding: 8, borderRadius: 22, backgroundColor: color.surface, flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderColor: color.grey200},
  composerInput: {...type.body, flex: 1, color: color.ink, paddingHorizontal: 8},
  send: {width: 42, height: 42, borderRadius: 21, backgroundColor: color.ink, alignItems: 'center', justifyContent: 'center'},
  pageLead: {...type.body, color: color.grey700, marginTop: 7, maxWidth: 360},
  capability: {width: '48.5%', minHeight: 128, borderRadius: radius.card, backgroundColor: color.surface, padding: space.md, borderWidth: 1, borderColor: color.grey200},
  collectionGrid: {flexDirection: 'row', gap: 10},
  collection: {width: '48.5%', minHeight: 168, borderRadius: radius.card, backgroundColor: color.surface, padding: space.md, borderWidth: 1, borderColor: color.grey200},
  collectionTitle: {...type.section, color: color.ink},
  syncCard: {flexDirection: 'row', alignItems: 'center', gap: 12},
});
