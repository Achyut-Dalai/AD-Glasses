import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
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
import {useProductSettings} from '../hooks/useProductSettings';
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
        <View>
          <SectionTitle caption="Start on the glasses. The phone can stay in your pocket.">From your glasses</SectionTitle>
          <View style={[styles.grid, {marginTop: 10}]}>
            <ActionTile icon="mic" title="Ask" detail="Voice question" onPress={() => ADNative.action('voiceQuestion')}/>
            <ActionTile icon="eye" title="What I see" detail="Ask with vision" onPress={() => ADNative.action('imageQuestion')}/>
            <ActionTile icon="camera" title="Snap" detail="Capture a photo" onPress={() => ADNative.action('capturePhoto')}/>
            <ActionTile icon="wave" title="Record" detail="Capture audio" onPress={() => ADNative.action('startRecording')}/>
          </View>
        </View>
      </Reveal>
      <Reveal delay={130}>
        <Card>
          <ListRow icon="video" title="Video" detail="Record from the glasses" onPress={() => ADNative.action('toggleVideo')}/><Divider/>
          <ListRow icon="translate" title="Translate" detail="Live translation through the glasses" onPress={() => navigate('capability', {capability: 'Translate'})}/><Divider/>
          <ListRow icon="bolt" title="Automation" detail="Background Android actions and Tasker routes" onPress={() => navigate('capability', {capability: 'Automation'})}/>
        </Card>
      </Reveal>
    </Screen>
  );
}

function ActionTile({icon, title, detail, onPress}: {icon: IconName; title: string; detail: string; onPress: () => void}) {
  return <PressableScale onPress={onPress} style={styles.actionTile}><IconTile name={icon}/><View style={{marginTop: 20}}><Text style={styles.tileTitle}>{title}</Text><Text style={styles.meta}>{detail}</Text></View></PressableScale>;
}

/** Hidden compatibility surface for contextual sessions launched from an artifact or internal route. */
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
    const optimistic = {id: `pending-${Date.now()}`, role: 'user' as const, text, createdAt: Date.now()};
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
      setConversation(await ADNative.conversation());
    } finally {
      setSending(false);
    }
  };

  return (
    <Screen scroll={false} style={{flex: 1}}>
      <View style={styles.promptHeader}>
        <IconTile name="terminal"/>
        <View style={{flex: 1}}><Text style={styles.promptTitle}>Context</Text><Text style={styles.meta}>Continue an artifact-specific AI session</Text></View>
        <PressableScale style={styles.newPrompt} onPress={startNew} accessibilityLabel="New context"><Icon name="plus" size={16}/><Text style={styles.newPromptText}>New</Text></PressableScale>
      </View>
      <ScrollView ref={scrollRef} style={styles.promptBody} contentContainerStyle={styles.promptScroll} showsVerticalScrollIndicator={false}>
        {conversation.messages.length === 0 && !sending ? (
          <View style={styles.emptyPrompt}>
            <View style={styles.promptHeroIcon}><Icon name="spark" size={28}/></View>
            <Text style={styles.emptyTitle}>Ask about this context</Text>
            <Text style={styles.emptyBody}>This surface is opened from a photo, recording, note or another piece of glasses context.</Text>
          </View>
        ) : (
          <View style={{gap: 16}}>
            {conversation.messages.map(message => <View key={message.id} style={[styles.message, message.role === 'user' && styles.userMessage]}><Text style={styles.messageText}>{message.text}</Text></View>)}
            {sending ? <ThinkingRow/> : null}
            {error ? <View style={styles.errorBubble}><Text style={styles.errorText}>{error}</Text></View> : null}
          </View>
        )}
      </ScrollView>
      <View style={styles.composer}>
        <PressableScale onPress={() => setWebSearch(enabled => !enabled)} style={[styles.webToggle, webSearch && styles.webToggleActive]} accessibilityLabel="Toggle web search"><Icon name="web" size={17} stroke={webSearch ? color.white : color.grey700}/></PressableScale>
        <TextInput placeholder={webSearch ? 'Ask with web…' : 'Ask about this…'} placeholderTextColor={color.grey500} value={value} onChangeText={setValue} onSubmitEditing={send} editable={!sending} returnKeyType="send" style={styles.composerInput}/>
        <PressableScale onPress={send} style={[styles.send, sending && {opacity: 0.45}]} accessibilityLabel="Send"><Icon name="send" size={18} stroke={color.white}/></PressableScale>
      </View>
    </Screen>
  );
}

function ThinkingRow() {
  return <View style={styles.thinking}><Icon name="spark" size={17} stroke={color.grey700}/><Text style={styles.meta}>Thinking…</Text></View>;
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
  const {settings, setSettings} = useProductSettings();
  const provider = settings.provider;
  const hasLocalModel = settings.localModels.length > 0;
  const privateBrain = hasLocalModel ? 'Local AI' : provider;
  const visionBrain = provider === 'Local AI' ? 'Gemini' : provider;

  const routes = useMemo(() => [
    {icon: 'spark' as IconName, title: 'Primary brain', detail: 'General questions and glasses voice requests', value: provider},
    {icon: 'eye' as IconName, title: 'Vision brain', detail: 'Photos, video and what you see', value: visionBrain},
    {icon: 'lock' as IconName, title: 'Private brain', detail: 'Stored context, summaries and private memory', value: privateBrain},
    {icon: 'bolt' as IconName, title: 'Automation executor', detail: 'Native actions → Tasker → Accessibility fallback', value: 'Android'},
  ], [privateBrain, provider, visionBrain]);

  const chooseProvider = (name: 'Gemini' | 'OpenAI / Codex' | 'Local AI') => {
    setSettings(current => ({...current, provider: name}));
    ADNative.action('setAiProvider', {provider: name});
  };

  return (
    <Screen>
      <Reveal><HeroTitle>AI</HeroTitle><Text style={styles.pageLead}>Choose the brains behind the glasses. AD Glasses routes the request; it does not need to become another chat app.</Text></Reveal>
      <Reveal delay={45}>
        <View>
          <SectionTitle caption="Different work can use different intelligence">Routing</SectionTitle>
          <Card style={{marginTop: 10}}>
            {routes.map((item, index) => <React.Fragment key={item.title}><ListRow icon={item.icon} title={item.title} detail={item.detail} value={item.value}/>{index < routes.length - 1 ? <Divider/> : null}</React.Fragment>)}
          </Card>
        </View>
      </Reveal>
      <Reveal delay={80}>
        <View>
          <SectionTitle caption={`Current · ${provider}`}>Primary brain</SectionTitle>
          <Card style={{marginTop: 10}}>
            {(['Gemini', 'OpenAI / Codex', 'Local AI'] as const).map((name, index) => <React.Fragment key={name}><ListRow icon={name === 'Local AI' ? 'computer' : 'cloud'} title={name} detail={name === 'Local AI' ? 'On-device model or compatible private endpoint' : 'Provider-backed reasoning through your configured route'} selected={provider === name} onPress={() => chooseProvider(name)}/>{index < 2 ? <Divider/> : null}</React.Fragment>)}
          </Card>
        </View>
      </Reveal>
      <Reveal delay={115}><View><SectionTitle caption="Behavior that belongs to the glasses">Capabilities</SectionTitle><View style={[styles.grid, {marginTop: 10}]}>{capabilities.map(item => <PressableScale key={item.title} onPress={() => navigate('capability', {capability: item.title})} style={styles.capability}><IconTile name={item.icon}/><View style={{marginTop: 22}}><Text style={styles.tileTitle}>{item.title}</Text><Text style={styles.meta} numberOfLines={2}>{item.detail}</Text></View></PressableScale>)}</View></View></Reveal>
      <Reveal delay={150}><View><SectionTitle>Connections</SectionTitle><Card style={{marginTop: 10}}><ListRow icon="spark" title="System assistants" detail="Optional Gemini or ChatGPT handoff — never the core runtime" onPress={() => navigate('assistant-apps')}/><Divider/><ListRow icon="cloud" title="Relay" detail="Cloud provider, backend and web access" onPress={() => navigate('relay')}/><Divider/><ListRow icon="computer" title="Local & compatible models" detail="Private on-device and network models" onPress={() => navigate('local-ai')}/></Card></View></Reveal>
    </Screen>
  );
}

export function LibraryScreen({navigate}: {navigate: Navigate}) {
  return (
    <Screen>
      <Reveal><HeroTitle>Library</HeroTitle><Text style={styles.pageLead}>What your glasses captured becomes useful context here — not a second camera roll.</Text></Reveal>
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