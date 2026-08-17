import React, {useEffect, useMemo, useState} from 'react';
import {StyleSheet, Switch, Text, View} from 'react-native';
import {
  Card,
  Divider,
  Field,
  GlassesImage,
  IconTile,
  ListRow,
  PrimaryButton,
  Screen,
  SectionTitle,
  TopBar,
} from '../design/components';
import {Icon} from '../design/icons';
import {color, radius, space, type} from '../design/tokens';
import {useDashboardState} from '../hooks/useDashboardState';
import {useProductSettings} from '../hooks/useProductSettings';
import {ADNative} from '../native/ADNative';
import {ADProductSettings} from '../native/ADProductSettings';
import type {Navigate} from '../navigation/routes';

function SettingsPage({title, back, children}: React.PropsWithChildren<{title: string; back: () => void}>) {
  return <Screen><TopBar title={title} onBack={back}/>{children}</Screen>;
}

function SettingsGroup({title, caption, children}: React.PropsWithChildren<{title: string; caption?: string}>) {
  return (
    <View>
      <SectionTitle caption={caption}>{title}</SectionTitle>
      <Card style={styles.groupCard}>{children}</Card>
    </View>
  );
}

function ToggleRow({title, detail, value, onChange}: {title: string; detail: string; value: boolean; onChange: (value: boolean) => void}) {
  return (
    <View style={styles.toggleRow}>
      <View style={styles.copy}>
        <Text style={styles.rowTitle}>{title}</Text>
        <Text style={styles.rowDetail}>{detail}</Text>
      </View>
      <Switch
        value={value}
        onValueChange={onChange}
        trackColor={{false: color.grey300, true: color.grey900}}
        thumbColor={color.white}
      />
    </View>
  );
}

export function SettingsScreen({navigate, back}: {navigate: Navigate; back: () => void}) {
  const state = useDashboardState();
  const status = state.connecting ? 'Connecting' : state.connected ? 'Connected' : 'Disconnected';

  return (
    <SettingsPage title="Settings" back={back}>
      <Card onPress={() => navigate('device')} style={styles.glassesCard}>
        <View style={styles.glassesImage}><GlassesImage height={72} dimmed={!state.connected}/></View>
        <View style={styles.copy}>
          <Text style={styles.glassesTitle}>{state.connected ? state.deviceName : 'Glasses'}</Text>
          <Text style={styles.rowDetail}>{status}</Text>
        </View>
        <Icon name="chevron" size={19} stroke={color.grey500}/>
      </Card>

      <SettingsGroup title="Privacy & data">
        <ListRow icon="lock" title="Privacy" detail="Memory, transcripts and redaction" onPress={() => navigate('privacy')}/>
        <Divider/>
        <ListRow icon="storage" title="Storage" detail="Media, models and private app data" onPress={() => navigate('storage')}/>
      </SettingsGroup>

      <SettingsGroup title="General">
        <ListRow icon="language" title="Language" detail="App language and system locale" onPress={() => navigate('language')}/>
        <Divider/>
        <ListRow icon="shield" title="Permissions" detail="Camera, microphone, Bluetooth and automation" onPress={() => navigate('permissions')}/>
      </SettingsGroup>

      <SettingsGroup title="AD Glasses">
        <ListRow icon="settings" title="Advanced" detail="Diagnostics and Android controls" onPress={() => navigate('advanced')}/>
        <Divider/>
        <ListRow icon="info" title="About AD Glasses" detail="Version and product information" onPress={() => navigate('about')}/>
      </SettingsGroup>
    </SettingsPage>
  );
}

export function PrivacyScreen({back}: {back: () => void}) {
  const {settings, loading, setSettings} = useProductSettings();

  const setRedaction = (enabled: boolean) => {
    setSettings(current => ({...current, redactNames: enabled}));
    ADProductSettings.setRedactNames(enabled);
  };
  const setTranscriptStorage = (enabled: boolean) => {
    setSettings(current => ({...current, transcriptStorage: enabled}));
    ADProductSettings.setTranscriptStorage(enabled);
  };

  return (
    <SettingsPage title="Privacy" back={back}>
      <View style={styles.editorialLead}>
        <View style={styles.heroIcon}><Icon name="lock" size={28}/></View>
        <Text style={styles.editorialTitle}>Private by default.</Text>
        <Text style={styles.editorialBody}>Your glasses create context. The phone decides what becomes memory.</Text>
      </View>

      <SettingsGroup title="Memory & transcripts" caption={loading ? 'Reading your preferences…' : 'Stored on this phone'}>
        <ToggleRow
          title="Redact names"
          detail="Remove detected names from private summaries when possible."
          value={settings.redactNames}
          onChange={setRedaction}
        />
        <Divider inset={0}/>
        <ToggleRow
          title="Store transcripts"
          detail="Keep transcript text after processing instead of discarding it."
          value={settings.transcriptStorage}
          onChange={setTranscriptStorage}
        />
      </SettingsGroup>
    </SettingsPage>
  );
}

export function StorageScreen({back}: {back: () => void}) {
  const {settings} = useProductSettings();
  const selectedModel = settings.localModels.find(model => model.selected);

  return (
    <SettingsPage title="Storage" back={back}>
      <View style={styles.editorialLead}>
        <View style={styles.heroIcon}><Icon name="storage" size={28}/></View>
        <Text style={styles.editorialTitle}>Your memory lives here.</Text>
        <Text style={styles.editorialBody}>Captures, recordings, models and private context stay under the app’s storage boundary.</Text>
      </View>
      <SettingsGroup title="On this phone">
        <Metric label="Media" value="Library"/>
        <Divider inset={0}/>
        <Metric label="Local models" value={settings.localModels.length ? `${settings.localModels.length} installed` : 'None installed'}/>
        <Divider inset={0}/>
        <Metric label="Active model" value={selectedModel?.name ?? 'None'}/>
      </SettingsGroup>
      <PrimaryButton label="Open Android storage settings" secondary onPress={() => ADNative.action('openStorageSettings')}/>
    </SettingsPage>
  );
}

const languages = [
  ['SYSTEM', 'System default'],
  ['ENGLISH', 'English'],
  ['PORTUGUESE_BRAZIL', 'Português (Brasil)'],
  ['SPANISH', 'Español'],
  ['GERMAN', 'Deutsch'],
  ['FRENCH', 'Français'],
  ['ITALIAN', 'Italiano'],
  ['CHINESE_SIMPLIFIED', '中文（简体）'],
  ['KOREAN', '한국어'],
  ['RUSSIAN', 'Русский'],
] as const;

export function LanguageScreen({back}: {back: () => void}) {
  const {settings, setSettings} = useProductSettings();
  const choose = (id: string) => {
    setSettings(current => ({...current, language: id}));
    ADProductSettings.setLanguage(id);
  };
  return (
    <SettingsPage title="Language" back={back}>
      <SettingsGroup title="App language" caption="Changes apply to native and React surfaces">
        {languages.map(([id, label], index) => (
          <React.Fragment key={id}>
            <ListRow icon="language" title={label} selected={settings.language === id} onPress={() => choose(id)}/>
            {index < languages.length - 1 ? <Divider/> : null}
          </React.Fragment>
        ))}
      </SettingsGroup>
    </SettingsPage>
  );
}

export function PermissionsScreen({back}: {back: () => void}) {
  const {settings} = useProductSettings();
  return (
    <SettingsPage title="Permissions" back={back}>
      <View style={styles.editorialLead}>
        <View style={styles.heroIcon}><Icon name="shield" size={28}/></View>
        <Text style={styles.editorialTitle}>Only what the glasses need.</Text>
        <Text style={styles.editorialBody}>Permission state explains capability. It never replaces the glasses as the product identity.</Text>
      </View>
      <SettingsGroup title="Access">
        <PermissionRow icon="camera" title="Camera" detail="Vision and captures" granted={settings.cameraGranted}/>
        <Divider/>
        <PermissionRow icon="mic" title="Microphone" detail="Voice, recording and Soundbites" granted={settings.microphoneGranted}/>
        <Divider/>
        <PermissionRow icon="glasses" title="Nearby devices" detail="Connect and communicate with glasses" granted={settings.bluetoothGranted}/>
        <Divider/>
        <PermissionRow icon="bolt" title="Automation" detail="Operate supported Android actions" granted={settings.automationGranted}/>
      </SettingsGroup>
      <PrimaryButton label="Open Android app settings" secondary onPress={() => ADNative.action('openAppSettings')}/>
    </SettingsPage>
  );
}

function PermissionRow({icon, title, detail, granted}: {icon: 'camera' | 'mic' | 'glasses' | 'bolt'; title: string; detail: string; granted: boolean}) {
  return <ListRow icon={icon} title={title} detail={detail} value={granted ? 'Allowed' : 'Not allowed'}/>;
}

export function RelayScreen({back}: {back: () => void}) {
  const {settings} = useProductSettings();
  const [url, setUrl] = useState('');
  const [backend, setBackend] = useState<'Gemini' | 'OpenAI / Codex'>('Gemini');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    setUrl(settings.relayUrl);
    setBackend(settings.relayBackend);
  }, [settings.relayUrl, settings.relayBackend]);

  const save = () => {
    ADNative.action('saveRelay', {url, backend});
    setSaved(true);
  };

  return (
    <SettingsPage title="Relay" back={back}>
      <View style={styles.editorialLead}>
        <View style={styles.heroIcon}><Icon name="cloud" size={28}/></View>
        <Text style={styles.editorialTitle}>Remote intelligence, one route.</Text>
        <Text style={styles.editorialBody}>Web Search and cloud AI use this endpoint when a request needs them.</Text>
      </View>
      <SettingsGroup title="Server">
        <Text style={styles.fieldLabel}>Address</Text>
        <Field value={url} onChangeText={value => {setUrl(value); setSaved(false);}} placeholder="https://your-relay.example"/>
      </SettingsGroup>
      <SettingsGroup title="Backend">
        <ListRow icon="cloud" title="Gemini" detail="Gemini through your relay" selected={backend === 'Gemini'} onPress={() => {setBackend('Gemini'); setSaved(false);}}/>
        <Divider/>
        <ListRow icon="cloud" title="OpenAI / Codex" detail="OpenAI-compatible route through your relay" selected={backend === 'OpenAI / Codex'} onPress={() => {setBackend('OpenAI / Codex'); setSaved(false);}}/>
      </SettingsGroup>
      <PrimaryButton label={saved ? 'Saved' : 'Save relay'} onPress={save}/>
    </SettingsPage>
  );
}

export function LocalAIScreen({back}: {back: () => void}) {
  const {settings, setSettings} = useProductSettings();
  const [url, setUrl] = useState('');
  const [model, setModel] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    setUrl(settings.remoteUrl);
    setModel(settings.remoteModel);
  }, [settings.remoteUrl, settings.remoteModel]);

  const selectedModel = useMemo(() => settings.localModels.find(item => item.selected), [settings.localModels]);
  const selectModel = (id: string) => {
    ADProductSettings.selectLocalModel(id);
    setSettings(current => ({
      ...current,
      localModels: current.localModels.map(item => ({...item, selected: item.id === id})),
    }));
  };
  const save = () => {
    ADNative.action('saveRemoteServer', {url, model, enabled: true});
    setSaved(true);
  };

  return (
    <SettingsPage title="Local AI" back={back}>
      <View style={styles.editorialLead}>
        <View style={styles.heroIcon}><Icon name="computer" size={28}/></View>
        <Text style={styles.editorialTitle}>Intelligence on this phone.</Text>
        <Text style={styles.editorialBody}>Use a managed local model, or connect to a compatible server on your own network.</Text>
      </View>

      <SettingsGroup title="Installed models" caption={selectedModel ? `Active · ${selectedModel.name}` : 'No model selected'}>
        {settings.localModels.length ? settings.localModels.map((item, index) => (
          <React.Fragment key={item.id}>
            <ListRow
              icon="computer"
              title={item.name}
              detail={formatBytes(item.sizeBytes)}
              selected={item.selected}
              onPress={() => selectModel(item.id)}
            />
            {index < settings.localModels.length - 1 ? <Divider/> : null}
          </React.Fragment>
        )) : (
          <View style={styles.emptyInline}>
            <Text style={styles.rowTitle}>No local model installed</Text>
            <Text style={styles.rowDetail}>Import remains native while file-copy and model registration are migrated.</Text>
          </View>
        )}
      </SettingsGroup>

      <PrimaryButton label="Import model file" secondary icon="storage" onPress={() => ADNative.action('openModelImport')}/>

      <SettingsGroup title="Compatible server" caption="Ollama, llama.cpp, vLLM or another OpenAI-compatible endpoint">
        <Text style={styles.fieldLabel}>Server address</Text>
        <Field value={url} onChangeText={value => {setUrl(value); setSaved(false);}} placeholder="http://192.168.1.50:11434/v1"/>
        <View style={styles.fieldGap}/>
        <Text style={styles.fieldLabel}>Model</Text>
        <Field value={model} onChangeText={value => {setModel(value); setSaved(false);}} placeholder="Model name"/>
        <View style={styles.fieldGap}/>
        <PrimaryButton label={saved ? 'Saved' : 'Save server'} onPress={save}/>
      </SettingsGroup>
    </SettingsPage>
  );
}

function Metric({label, value}: {label: string; value: string}) {
  return <View style={styles.metric}><Text style={styles.metricLabel}>{label}</Text><Text style={styles.metricValue}>{value}</Text></View>;
}

function formatBytes(bytes: number) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

const styles = StyleSheet.create({
  groupCard: {marginTop: 10},
  glassesCard: {minHeight: 102, flexDirection: 'row', alignItems: 'center', gap: 14},
  glassesImage: {width: 104, height: 74, justifyContent: 'center'},
  glassesTitle: {...type.section, color: color.ink},
  copy: {flex: 1, gap: 2},
  rowTitle: {...type.cardTitle, color: color.ink},
  rowDetail: {...type.meta, color: color.grey700},
  toggleRow: {minHeight: 76, flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 6},
  editorialLead: {paddingTop: space.xs, paddingBottom: space.xs},
  heroIcon: {width: 62, height: 62, borderRadius: 20, backgroundColor: color.grey100, alignItems: 'center', justifyContent: 'center'},
  editorialTitle: {...type.title, color: color.ink, marginTop: 18},
  editorialBody: {...type.body, color: color.grey700, marginTop: 7, maxWidth: 370},
  metric: {minHeight: 50, flexDirection: 'row', alignItems: 'center', gap: 16},
  metricLabel: {...type.body, color: color.ink, flex: 1},
  metricValue: {...type.meta, color: color.grey700, maxWidth: '56%', textAlign: 'right'},
  fieldLabel: {...type.meta, color: color.grey700, marginBottom: 7},
  fieldGap: {height: 14},
  emptyInline: {paddingVertical: 10, gap: 4},
});
