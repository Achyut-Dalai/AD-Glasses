import React, {useMemo, useState} from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {
  Card,
  Divider,
  HeroTitle,
  IconTile,
  ListRow,
  PressableScale,
  PrimaryButton,
  Reveal,
  Screen,
  SectionTitle,
  StatusPill,
} from '../design/components';
import {IconName} from '../design/icons';
import {color, radius, space, type} from '../design/tokens';
import {ADNative} from '../native/ADNative';
import {ADProductSettings, ProductSettings} from '../native/ADProductSettings';
import {useProductSettings} from '../hooks/useProductSettings';
import type {Navigate} from '../navigation/routes';

const capabilities: {title: string; detail: string; icon: IconName}[] = [
  {title: 'Translate', detail: 'Live translation', icon: 'translate'},
  {title: 'Soundbites', detail: 'Audio to notes', icon: 'wave'},
  {title: 'Timeline', detail: 'Searchable visual memory', icon: 'timeline'},
  {title: 'DayNote', detail: 'Daily moments, distilled', icon: 'book'},
  {title: 'Cron', detail: 'Recurring scheduled work', icon: 'repeat'},
  {title: 'Automation', detail: 'Apps & Android actions', icon: 'bolt'},
];

type Provider = ProductSettings['provider'];

export function AIScreen({navigate}: {navigate: Navigate}) {
  const {settings, setSettings, refresh} = useProductSettings();
  const [requestingRole, setRequestingRole] = useState(false);
  const hasLocalModel = settings.localModels.length > 0;

  const routes = useMemo(() => [
    {
      icon: 'mic' as IconName,
      title: 'Live conversation',
      detail: 'Low-latency voice from the glasses, with vision when needed',
      value: settings.conversationEngine === 'GEMINI_LIVE' || (settings.conversationEngine === 'AUTO' && settings.provider === 'Gemini') ? 'Gemini Live' : settings.provider,
    },
    {
      icon: 'web' as IconName,
      title: 'Current knowledge',
      detail: 'Fresh information is grounded during the AI request, not sent to a search screen',
      value: settings.groundingPolicy === 'NEVER' ? 'Off' : settings.provider === 'Gemini' ? 'Gemini + Search' : settings.provider,
    },
    {
      icon: 'lock' as IconName,
      title: 'Private & offline',
      detail: 'Local speech and private processing stay available without making cloud AI the only path',
      value: hasLocalModel ? 'Moonshine + Local AI' : 'Moonshine',
    },
    {
      icon: 'bolt' as IconName,
      title: 'Phone actions',
      detail: 'Screen-off execution is preferred; visible UI automation is an explicit fallback',
      value: settings.automationExecutor,
    },
  ], [hasLocalModel, settings.automationExecutor, settings.conversationEngine, settings.groundingPolicy, settings.provider]);

  const chooseProvider = (name: Provider) => {
    setSettings(current => ({...current, provider: name}));
    ADNative.action('setAiProvider', {provider: name});
  };

  const chooseAutomation = (executor: ProductSettings['automationExecutor']) => {
    setSettings(current => ({...current, automationExecutor: executor}));
    ADProductSettings.setAutomationExecutor(executor);
  };

  const requestAssistantRole = async () => {
    if (requestingRole) return;
    setRequestingRole(true);
    await ADProductSettings.requestAssistantRole();
    await refresh();
    setRequestingRole(false);
  };

  return (
    <Screen>
      <Reveal>
        <HeroTitle>AI</HeroTitle>
        <Text style={styles.pageLead}>AD is the assistant. Gemini, local models and Android tools work behind it.</Text>
      </Reveal>

      <Reveal delay={35}>
        <Card style={styles.runtimeCard}>
          <View style={styles.runtimeTop}>
            <IconTile name="glasses" dark/>
            <View style={styles.runtimeCopy}>
              <Text style={styles.runtimeTitle}>AD Assistant</Text>
              <Text style={styles.meta}>Your glasses wake AD directly. The phone can stay dark.</Text>
            </View>
            <StatusPill
              label={settings.screenOffFirst ? 'SCREEN-OFF FIRST' : 'VISIBLE FALLBACKS'}
              tone={settings.screenOffFirst ? 'success' : 'warning'}
            />
          </View>
          {settings.assistantRoleAvailable && !settings.assistantRoleHeld ? (
            <View style={styles.roleAction}>
              <Text style={styles.meta}>Optional: make AD Android’s assistant too for stronger system-level screen-off integration.</Text>
              <PrimaryButton label={requestingRole ? 'Opening Android…' : 'Use AD as Android assistant'} secondary onPress={requestAssistantRole}/>
            </View>
          ) : null}
        </Card>
      </Reveal>

      <Reveal delay={70}>
        <View>
          <SectionTitle caption="One assistant, different engines for different work">Runtime</SectionTitle>
          <Card style={{marginTop: 10}}>
            {routes.map((item, index) => (
              <React.Fragment key={item.title}>
                <ListRow icon={item.icon} title={item.title} detail={item.detail} value={item.value}/>
                {index < routes.length - 1 ? <Divider/> : null}
              </React.Fragment>
            ))}
            <Divider/>
            <ListRow
              icon="settings"
              title="Customize routing"
              detail="Conversation, speech, vision, files, grounding and visible fallback"
              value={profileLabel(settings.aiProfile)}
              onPress={() => navigate('ai-runtime')}
            />
          </Card>
        </View>
      </Reveal>

      <Reveal delay={105}>
        <View>
          <SectionTitle caption="Recommended for live voice, vision, tools and fresh information">Cloud brain</SectionTitle>
          <Card style={{marginTop: 10}}>
            <ListRow
              icon="spark"
              title="Gemini"
              detail="Recommended · direct AD integration, not the Gemini app UI"
              selected={settings.provider === 'Gemini'}
              onPress={() => chooseProvider('Gemini')}
            />
            <Divider/>
            <ListRow
              icon="computer"
              title="Local AI"
              detail="Private or offline model on this phone / compatible endpoint"
              selected={settings.provider === 'Local AI'}
              onPress={() => chooseProvider('Local AI')}
            />
            <Divider/>
            <ListRow
              icon="cloud"
              title="OpenAI / Codex"
              detail="Advanced alternate provider; not required for the core glasses experience"
              selected={settings.provider === 'OpenAI / Codex'}
              onPress={() => chooseProvider('OpenAI / Codex')}
            />
          </Card>
        </View>
      </Reveal>

      <Reveal delay={140}>
        <View>
          <SectionTitle caption="How AD acts on the phone">Automation</SectionTitle>
          <Card style={{marginTop: 10}}>
            <ListRow
              icon="bolt"
              title="Background / Tasker"
              detail={settings.taskerInstalled ? 'Preferred · broadcasts actions without opening the phone UI' : 'Preferred · install/configure Tasker for broad screen-off actions'}
              selected={settings.automationExecutor === 'Background / Tasker'}
              onPress={() => chooseAutomation('Background / Tasker')}
            />
            <Divider/>
            <ListRow
              icon="shield"
              title="Accessibility fallback"
              detail="Use only for actions Android cannot complete headlessly"
              selected={settings.automationExecutor === 'Accessibility fallback'}
              onPress={() => chooseAutomation('Accessibility fallback')}
            />
          </Card>
        </View>
      </Reveal>

      <Reveal delay={175}>
        <View>
          <SectionTitle caption="Behavior that belongs to the glasses">Capabilities</SectionTitle>
          <View style={[styles.grid, {marginTop: 10}]}>
            {capabilities.map(item => (
              <PressableScale
                key={item.title}
                onPress={() => navigate('capability', {capability: item.title})}
                style={styles.capability}>
                <IconTile name={item.icon}/>
                <View style={{marginTop: 22}}>
                  <Text style={styles.tileTitle}>{item.title}</Text>
                  <Text style={styles.meta} numberOfLines={2}>{item.detail}</Text>
                </View>
              </PressableScale>
            ))}
          </View>
        </View>
      </Reveal>

      <Reveal delay={210}>
        <View>
          <SectionTitle>Infrastructure</SectionTitle>
          <Card style={{marginTop: 10}}>
            <ListRow icon="cloud" title="Gemini relay" detail="Authentication, ephemeral Live tokens and cloud routing" onPress={() => navigate('relay')}/>
            <Divider/>
            <ListRow icon="computer" title="Local & compatible models" detail="Private models, Moonshine speech and compatible endpoints" onPress={() => navigate('local-ai')}/>
          </Card>
        </View>
      </Reveal>
    </Screen>
  );
}

function profileLabel(profile: ProductSettings['aiProfile']) {
  if (profile === 'FAST') return 'Fast';
  if (profile === 'PRIVATE') return 'Private';
  if (profile === 'CUSTOM') return 'Custom';
  return 'Balanced';
}

const styles = StyleSheet.create({
  pageLead: {...type.body, color: color.grey700, marginTop: 7, maxWidth: 360},
  runtimeCard: {gap: space.md},
  runtimeTop: {flexDirection: 'row', alignItems: 'center', gap: space.sm},
  runtimeCopy: {flex: 1, gap: 3},
  runtimeTitle: {...type.section, color: color.ink},
  roleAction: {gap: space.sm, paddingTop: space.sm, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.grey200},
  meta: {...type.meta, color: color.grey700},
  grid: {flexDirection: 'row', flexWrap: 'wrap', gap: 10},
  capability: {width: '48.5%', minHeight: 128, borderRadius: radius.card, backgroundColor: color.surface, padding: space.md, borderWidth: 1, borderColor: color.grey200},
  tileTitle: {...type.cardTitle, color: color.ink},
});
