import React, {useCallback, useEffect, useRef, useState} from 'react';
import {ScrollView, StyleSheet, Text, TextInput, View} from 'react-native';
import {
  Card,
  Divider,
  GlassesImage,
  HeroTitle,
  IconTile,
  ListRow,
  PressableScale,
  Reveal,
  Screen,
  SectionTitle,
} from '../design/components';
import {Icon, IconName} from '../design/icons';
import {color, radius, space, type} from '../design/tokens';
import {ADNative, ConversationState} from '../native/ADNative';
import {useDashboardState} from '../hooks/useDashboardState';
import type {Navigate, RouteEntry} from '../navigation/routes';

export function HomeScreen({navigate}: {navigate: Navigate}) {
  const state = useDashboardState();
  const status = state.connecting ? 'Connecting' : state.connected ? 'Connected' : 'Disconnected';
  return (
    <Screen>
      <Reveal>
        <View style={styles.brandRow}>
          <Text style={styles.brand}>AD GLASSES</Text>
          <PressableScale onPress={() => navigate('settings')} style={styles.roundButton} accessibilityLabel="Settings">
            <Icon name="settings" size={19}/>
          </PressableScale>
        </View>
      </Reveal>
      <Reveal delay={45}>
        <Card onPress={() => navigate('device')} style={styles.deviceHero}>
          <View style={styles.heroGlass}><GlassesImage height={150}/></View>
          <View style={styles.deviceStatusRow}>
            <View style={[styles.dot, {backgroundColor: state.connected ? color.success : color.grey500}]}/>
            <View style={{flex: 1}}>
              <Text style={styles.deviceTitle}>{status}</Text>
              <Text style={styles.meta}>{state.connected ? state.deviceName : 'Your glasses are ready when you are'}</Text>
            </View>
            {state.connected && state.batteryPercent != null ? <Text style={styles.metric}>{state.batteryPercent}%</Text> : <Text style={styles.connectText}>Connect</Text>}
          </View>
        </Card>
      </Reveal>
      <Reveal delay={90}>
        <View style={styles.grid}>
          <ActionTile icon="mic" title="Ask" detail="Voice question" onPress={() => ADNative.action('voiceQuestion')}/>
          <ActionTile icon="camera" title="Photo" detail="Capture now" onPress={() => ADNative.action('capturePhoto')}/>
          <ActionTile icon="video" title="Video" detail="Record from glasses" onPress={() => ADNative.action('toggleVideo')}/>
          <ActionTile icon="translate" title="Translate" detail="Live translation" onPress={() => navigate('capability', {capability: 'Translate'})}/>
        </View>
      </Reveal>
      <Reveal delay={130}>
        <Card>
          <ListRow icon="eye" title="Ask what I see" detail="Use the glasses camera with AI" onPress={() => ADNative.action('imageQuestion')}/><Divider/>
          <ListRow icon="wave" title="Record audio" detail="Save an audio recording" onPress={() => ADNative.action('startRecording')}/><Divider/>
          <ListRow icon="web" title="Search web" detail="Fresh web-backed question" onPress={() => navigate('prompt', {web: true})}/><Divider/>
          <ListRow icon="bolt" title="Automation" detail="Apps and supported Android actions" onPress={() => navigate('capability', {capability: 'Automation'})}/>
        </Card>
      </Reveal>
    </Screen>
  );
}

function ActionTile({icon, title, detail, onPress}: {icon: IconName; title: string; detail: string; onPress: () => void}) {
  return <PressableScale onPress={onPress} style={styles.actionTile}><IconTile name={icon}/><View style={{marginTop: 20}}><Text style={styles.tileTitle}>{title}</Text><Text style={styles.meta}>{detail}</Text></View></PressableScale>;
}

export function PromptScreen({route}: {route?: RouteEntry}) {
  const [value, setValue] = useState('');
  const [conversation, setConversation] = useState<ConversationState>({threadId: '', messages: []});
  const [sending, setSending] = useState(false);
  const [webSearch, setWebSearch] = useState(Boolean(route?.params?.web));
  const [error, setError] = useState<string | null>(null);
  const scrollRef = useRef<ScrollView>(null);

  const refresh = useCallback(() => {
    if (sending) return;
    ADNative.conversation().then(setConversation);
  }, [sending]);

  useEffect(() => {
    void refresh();
    const timer = setInterval(refresh, 2200);
    return () => clearInterval(timer);
  }, [refresh]);

  useEffect(() => {
    scrollRef.current?.scrollToEnd({animated: true});
  }, [conversation.messages.length, sending]);

  const startNew = async () => {
    if (sending) return;
    setError(null);
    setValue('');
    setWebSearch(false);
    setConversation(await ADNative.newConversation());
  };

  const send = async () => {
    const text = value.trim();
    if (!text || sending) return;
    const optimistic = {
      id: `pending-${Date.now()}`,
      role: 'user' as const,
      text,
      createdAt: Date.now(),
    };
    setValue('');
    setError(null);
    setSending(true);
    setConversation(current => ({...current, messages: [...current.messages, optimistic]}));
    try {
      const next = await ADNative.sendPrompt(text, webSearch);
      setConversation(next);
      setWebSearch(false);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Couldn’t finish that request.');
      await refresh();
    } finally {
      setSending(false);
    }
  };

  return (
    <Screen scroll={false} style={{flex: 1}}>
      <View style={styles.promptHeader}>
        <IconTile name="terminal"/>
        <View style={{flex: 1}}><Text style={styles.promptTitle}>Prompt</Text><Text style={styles.meta}>Ask AI from your phone or glasses</Text></View>
        <PressableScale style={styles.newPrompt} onPress={startNew} accessibilityLabel="New prompt"><Icon name="plus" size={16}/><Text style={styles.newPromptText}>New</Text></PressableScale>
      </View>
      <ScrollView ref={scrollRef} style={styles.promptBody} contentContainerStyle={styles.promptScroll} showsVerticalScrollIndicator={false}>
        {conversation.messages.length === 0 && !sending ? (
          <View style={styles.emptyPrompt}>
            <View style={styles.promptHeroIcon}><Icon name="terminal" size={28}/></View>
            <Text style={styles.emptyTitle}>What do you want to know?</Text>
            <Text style={styles.emptyBody}>Use the phone when you want detail. Start on the glasses when you want speed.</Text>
            <View style={{gap: 8, width: '100%', marginTop: 18}}>
              <Suggestion text="What did I capture today?" onPress={() => setValue('What did I capture today?')}/>
              <Suggestion text="Search the web for something current" web onPress={() => {setValue('Search the web for '); setWebSearch(true);}}/>
              <Suggestion text="Help me plan something" onPress={() => setValue('Help me plan ')}/>
            </View>
          </View>
        ) : (
          <View style={{gap: 16}}>
            {conversation.messages.map(message => (
              <View key={message.id} style={[styles.message, message.role === 'user' && styles.userMessage]}>
                <Text style={styles.messageText}>{message.text}</Text>
              </View>
            ))}
            {sending ? <ThinkingRow/> : null}
            {error ? <View style={styles.errorBubble}><Text style={styles.errorText}>{error}</Text></View> : null}
          </View>
        )}
      </ScrollView>
      <View style={styles.composer}>
        <PressableScale onPress={() => setWebSearch(enabled => !enabled)} style={[styles.webToggle, webSearch && styles.webToggleActive]} accessibilityLabel="Toggle web search">
          <Icon name="web" size={17} stroke={webSearch ? color.white : color.grey700}/>
        </PressableScale>
        <TextInput placeholder={webSearch ? 'Ask with web…' : 'Ask AI…'} placeholderTextColor={color.grey500} value={value} onChangeText={setValue} onSubmitEditing={send} editable={!sending} returnKeyType="send" style={styles.composerInput}/>
        <PressableScale onPress={send} style={[styles.send, sending && {opacity: 0.45}]} accessibilityLabel="Send"><Icon name="send" size={18} stroke={color.white}/></PressableScale>
      </View>
    </Screen>
  );
}

function ThinkingRow() {
  return <View style={styles.thinking}><Icon name="spark" size={17} stroke={color.grey700}/><Text style={styles.meta}>Thinking…</Text></View>;
}

function Suggestion({text, web = false, onPress}: {text: string; web?: boolean; onPress: () => void}) {
  return <PressableScale onPress={onPress} style={styles.suggestion}><Icon name={web ? 'web' : 'terminal'} size={17}/><Text style={{...type.body, flex: 1, color: color.ink}}>{text}</Text><Icon name="chevron" size={17} stroke={color.grey500}/></PressableScale>;
}

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
  const chooseProvider = (name: string) => {
    setProvider(name);
    ADNative.action('setAiProvider', {provider: name});
  };
  return (
    <Screen>
      <Reveal><HeroTitle>Your AI</HeroTitle><Text style={styles.pageLead}>Choose how your glasses think, then shape what they can do.</Text></Reveal>
      <Reveal delay={45}><View><SectionTitle caption="Things your glasses can do">Capabilities</SectionTitle><View style={[styles.grid, {marginTop: 10}]}>{capabilities.map(item => <PressableScale key={item.title} onPress={() => navigate('capability', {capability: item.title})} style={styles.capability}><IconTile name={item.icon}/><View style={{marginTop: 22}}><Text style={styles.tileTitle}>{item.title}</Text><Text style={styles.meta} numberOfLines={2}>{item.detail}</Text></View></PressableScale>)}</View></View></Reveal>
      <Reveal delay={90}><View><SectionTitle caption={`Current route · ${provider}`}>Default AI</SectionTitle><Card style={{marginTop: 10}}>{['Gemini','OpenAI / Codex','Local AI'].map((name, index) => <React.Fragment key={name}><ListRow icon={name === 'Local AI' ? 'computer' : 'cloud'} title={name} detail={name === 'Local AI' ? 'Run a configured model on this phone' : 'Use your configured relay'} selected={provider === name} onPress={() => chooseProvider(name)}/>{index < 2 ? <Divider/> : null}</React.Fragment>)}</Card></View></Reveal>
      <Reveal delay={130}><View><SectionTitle>Connections</SectionTitle><Card style={{marginTop: 10}}><ListRow icon="spark" title="Assistant apps" detail="Optional Gemini or ChatGPT app handoff" onPress={() => navigate('assistant-apps')}/><Divider/><ListRow icon="cloud" title="Relay" detail="Server, backend and web access" onPress={() => navigate('relay')}/><Divider/><ListRow icon="computer" title="Local & compatible models" detail="On-device files and compatible endpoints" onPress={() => navigate('local-ai')}/></Card></View></Reveal>
    </Screen>
  );
}

export function LibraryScreen({navigate}: {navigate: Navigate}) {
  return (
    <Screen>
      <Reveal><HeroTitle>Library</HeroTitle><Text style={styles.pageLead}>Everything your glasses remembered, recorded or captured lives here.</Text></Reveal>
      <Reveal delay={45}><View style={styles.collectionGrid}><Collection icon="image" title="Captures" detail="Photos & video" onPress={() => navigate('captures')}/><Collection icon="wave" title="Recordings" detail="Audio & transcripts" onPress={() => navigate('recordings')}/></View></Reveal>
      <Reveal delay={90}><Collection icon="note" title="Notes & summaries" detail="Soundbites, DayNote and generated notes" onPress={() => navigate('notes')} wide/></Reveal>
      <Reveal delay={130}><Card onPress={() => navigate('sync')}><View style={styles.syncCard}><IconTile name="sync"/><View style={{flex: 1}}><Text style={styles.tileTitle}>Sync from glasses</Text><Text style={styles.meta}>Bring new captures onto this phone</Text></View><Icon name="chevron" size={18} stroke={color.grey500}/></View></Card></Reveal>
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
  promptScroll: {flexGrow: 1, paddingBottom: 16},
  emptyPrompt: {flex: 1, alignItems: 'center', justifyContent: 'center', paddingBottom: 20, minHeight: 430},
  promptHeroIcon: {width: 64, height: 64, borderRadius: 20, backgroundColor: color.grey100, alignItems: 'center', justifyContent: 'center'},
  emptyTitle: {...type.title, color: color.ink, marginTop: 18},
  emptyBody: {...type.body, color: color.grey700, textAlign: 'center', maxWidth: 330, marginTop: 7},
  suggestion: {minHeight: 52, borderRadius: radius.control, backgroundColor: color.surface, paddingHorizontal: 14, flexDirection: 'row', alignItems: 'center', gap: 10, borderWidth: 1, borderColor: color.grey200},
  message: {maxWidth: '91%', alignSelf: 'flex-start', paddingHorizontal: 2, paddingVertical: 3},
  userMessage: {alignSelf: 'flex-end', backgroundColor: color.grey100, borderRadius: 18, paddingHorizontal: 14, paddingVertical: 11},
  messageText: {...type.body, color: color.ink},
  thinking: {alignSelf: 'flex-start', minHeight: 38, borderRadius: 14, backgroundColor: color.grey100, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', gap: 8},
  errorBubble: {borderRadius: 14, backgroundColor: color.errorSoft, paddingHorizontal: 12, paddingVertical: 10},
  errorText: {...type.meta, color: color.error},
  composer: {minHeight: 66, marginHorizontal: -4, marginBottom: 4, padding: 8, borderRadius: 22, backgroundColor: color.surface, flexDirection: 'row', alignItems: 'center', gap: 8, borderWidth: 1, borderColor: color.grey200},
  composerInput: {...type.body, flex: 1, color: color.ink, paddingHorizontal: 4},
  webToggle: {width: 38, height: 38, borderRadius: 19, backgroundColor: color.grey100, alignItems: 'center', justifyContent: 'center'},
  webToggleActive: {backgroundColor: color.ink},
  send: {width: 42, height: 42, borderRadius: 21, backgroundColor: color.ink, alignItems: 'center', justifyContent: 'center'},
  pageLead: {...type.body, color: color.grey700, marginTop: 7, maxWidth: 360},
  capability: {width: '48.5%', minHeight: 128, borderRadius: radius.card, backgroundColor: color.surface, padding: space.md, borderWidth: 1, borderColor: color.grey200},
  collectionGrid: {flexDirection: 'row', gap: 10},
  collection: {width: '48.5%', minHeight: 168, borderRadius: radius.card, backgroundColor: color.surface, padding: space.md, borderWidth: 1, borderColor: color.grey200},
  collectionTitle: {...type.section, color: color.ink},
  syncCard: {flexDirection: 'row', alignItems: 'center', gap: 12},
});
