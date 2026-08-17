import React, {useState} from 'react';
import {StyleSheet, Switch, Text, View} from 'react-native';
import {
  Card,
  Divider,
  Field,
  GlassesImage,
  HeroTitle,
  IconTile,
  ListRow,
  PressableScale,
  PrimaryButton,
  Screen,
  SectionTitle,
  StatusPill,
  TopBar,
} from '../design/components';
import {Icon, IconName} from '../design/icons';
import {color, radius, space, type} from '../design/tokens';
import {ADNative} from '../native/ADNative';
import type {Navigate, RouteEntry} from '../navigation/routes';

function Page({title, back, children}: React.PropsWithChildren<{title: string; back: () => void}>) {
  return <Screen><TopBar title={title} onBack={back}/>{children}</Screen>;
}

export function SettingsScreen({navigate, back}: {navigate: Navigate; back: () => void}) {
  return (
    <Page title="Settings" back={back}>
      <PressableScale onPress={() => navigate('device')}>
        <View style={styles.glassesSettingsCard}>
          <GlassesImage height={64}/>
          <View style={{flex: 1}}><Text style={styles.cardTitle}>Glasses</Text><Text style={styles.meta}>Disconnected</Text></View>
          <Icon name="chevron" size={19} stroke={color.grey500}/>
        </View>
      </PressableScale>
      <SettingsGroup title="Privacy & data"><ListRow icon="lock" title="Privacy" detail="Transcripts, redaction and exports" onPress={() => navigate('privacy')}/><Divider/><ListRow icon="storage" title="Storage" detail="App data and media from the glasses" onPress={() => navigate('storage')}/></SettingsGroup>
      <SettingsGroup title="General"><ListRow icon="language" title="Language" detail="App language and system locale" onPress={() => navigate('language')}/><Divider/><ListRow icon="shield" title="Permissions" detail="Camera, microphone, Bluetooth and nearby devices" onPress={() => navigate('permissions')}/></SettingsGroup>
      <SettingsGroup title="AD Glasses"><ListRow icon="settings" title="Advanced" detail="Diagnostics and system controls" onPress={() => navigate('advanced')}/><Divider/><ListRow icon="info" title="About AD Glasses" detail="Version and product information" onPress={() => navigate('about')}/></SettingsGroup>
    </Page>
  );
}

function SettingsGroup({title, children}: React.PropsWithChildren<{title: string}>) { return <View><Text style={styles.groupLabel}>{title}</Text><Card style={{marginTop: 7}}>{children}</Card></View>; }

export function DeviceScreen({navigate, back}: {navigate: Navigate; back: () => void}) {
  return (
    <Page title="Device" back={back}>
      <Card style={styles.deviceCard}>
        <View style={styles.deviceStage}><GlassesImage height={170}/></View>
        <View style={styles.deviceSummary}><View style={styles.offlineDot}/><View style={{flex: 1}}><Text style={styles.heroCardTitle}>Glasses disconnected</Text><Text style={styles.meta}>Connect when you want the phone brain available to the glasses.</Text></View><StatusPill label="OFFLINE"/></View>
        <PrimaryButton label="Connect glasses" onPress={() => navigate('pairing')}/>
      </Card>
      <View><SectionTitle>Device tools</SectionTitle><Card style={{marginTop: 10}}><ListRow icon="sync" title="Sync media" detail="Bring glasses captures into Library" onPress={() => navigate('sync')}/><Divider/><ListRow icon="firmware" title="Firmware" detail="Updates and recovery" onPress={() => navigate('firmware')}/><Divider/><ListRow icon="settings" title="Advanced" detail="Connection diagnostics and Android controls" onPress={() => navigate('advanced')}/></Card></View>
    </Page>
  );
}

export function PairingScreen({back}: {back: () => void}) {
  return (
    <Page title="Connect glasses" back={back}>
      <View style={styles.pairingHero}><View style={styles.scanOuter}><View style={styles.scanInner}><Icon name="glasses" size={34}/></View></View><Text style={styles.pairingTitle}>Find your glasses</Text><Text style={styles.centerBody}>Keep the glasses nearby and ready to pair. The product image stays constant while connection state changes around it.</Text></View>
      <PrimaryButton label="Scan for glasses" icon="sync" onPress={() => ADNative.action('scan')}/>
      <Card><Text style={styles.cardTitle}>No supported glasses found</Text><Text style={[styles.meta, {marginTop: 5}]}>Check Bluetooth, keep the glasses close, and make sure they are not connected to another companion app.</Text></Card>
    </Page>
  );
}

export function SyncScreen({back}: {back: () => void}) {
  return <Page title="Sync" back={back}><TaskHero icon="sync" title="Sync media" detail="Ready to bring captures onto this phone"/><Card><Metric label="Connection" value="Not connected"/><Divider inset={0}/><Metric label="Transfer" value="Local Wi-Fi"/><Divider inset={0}/><Metric label="Media" value="Scanned when sync starts"/></Card><PrimaryButton label="Connect glasses" onPress={() => ADNative.action('openDeviceSetup')}/></Page>;
}

function TaskHero({icon, title, detail}: {icon: IconName; title: string; detail: string}) { return <Card><View style={styles.taskHero}><IconTile name={icon}/><View style={{flex: 1}}><Text style={styles.heroCardTitle}>{title}</Text><Text style={styles.meta}>{detail}</Text></View></View></Card>; }
function Metric({label, value}: {label: string; value: string}) { return <View style={styles.metricRow}><Text style={styles.metricLabel}>{label}</Text><Text style={styles.metricValue}>{value}</Text></View>; }

export function CapabilityScreen({route, back}: {route: RouteEntry; back: () => void}) {
  const name = String(route.params?.capability ?? 'Capability');
  const meta: Record<string, {icon: IconName; kicker: string; summary: string; output: string}> = {
    Translate: {icon: 'translate', kicker: 'LIVE TRANSLATION', summary: 'Hear a conversation through your glasses and get the meaning back without reaching for the phone.', output: 'Translated speech'},
    Soundbites: {icon: 'wave', kicker: 'AUDIO NOTES', summary: 'Capture spoken moments and turn them into concise notes you can revisit later.', output: 'Transcript + note'},
    Timeline: {icon: 'timeline', kicker: 'VISUAL MEMORY', summary: 'Turn visual captures into a searchable sequence of moments.', output: 'Visual timeline'},
    DayNote: {icon: 'book', kicker: 'DAILY MEMORY', summary: 'Distill the moments that matter into a private note for each day.', output: 'Private daily note'},
    Cron: {icon: 'repeat', kicker: 'SCHEDULED WORK', summary: 'Turn spoken requests into recurring or scheduled tasks without making scheduling feel like a separate app.', output: 'Scheduled tasks'},
    Automation: {icon: 'bolt', kicker: 'ANDROID ACTIONS', summary: 'Open apps, navigate and complete supported Android actions from the glasses.', output: 'Approved Android action'},
  };
  const data = meta[name] ?? meta.Automation;
  const [enabled, setEnabled] = useState(false);
  return (
    <Page title={name} back={back}>
      <View style={styles.capabilityHero}><View style={styles.capabilityIcon}><Icon name={data.icon} size={30}/></View><Text style={styles.kicker}>{data.kicker}</Text><Text style={styles.capabilitySummary}>{data.summary}</Text></View>
      <Card><View style={styles.toggleRow}><View style={{flex: 1}}><Text style={styles.heroCardTitle}>{enabled ? 'On' : 'Off'}</Text><Text style={styles.meta}>{enabled ? 'Ready when you use it from the glasses or phone.' : 'Turn it on when you want this capability available.'}</Text></View><Switch value={enabled} onValueChange={value => {setEnabled(value); ADNative.action('capabilityToggle', {name, enabled: value});}} trackColor={{false: color.grey300, true: color.grey900}} thumbColor={color.white}/></View></Card>
      <View><SectionTitle>What it does</SectionTitle><Card style={{marginTop: 10}}><Metric label="Processing" value={name === 'Automation' || name === 'DayNote' ? 'On device' : 'Configured AI'}/><Divider inset={0}/><Metric label="Saved as" value={data.output}/></Card></View>
    </Page>
  );
}

export function RelayScreen({back}: {back: () => void}) { const [url,setUrl]=useState(''); const [backend,setBackend]=useState('Gemini'); return <Page title="Relay" back={back}><View><SectionTitle caption="The remote route used by web and cloud AI">Server</SectionTitle><Card style={{marginTop:10}}><Field value={url} onChangeText={setUrl} placeholder="https://your-relay.example"/></Card></View><View><SectionTitle>Backend</SectionTitle><Card style={{marginTop:10}}><ListRow icon="cloud" title="Gemini" detail="Gemini CLI through your relay" selected={backend==='Gemini'} onPress={()=>setBackend('Gemini')}/><Divider/><ListRow icon="cloud" title="OpenAI / Codex" detail="OpenAI-compatible route" selected={backend==='OpenAI / Codex'} onPress={()=>setBackend('OpenAI / Codex')}/></Card></View><PrimaryButton label="Save relay"/></Page>; }

export function LocalAIScreen({back}: {back: () => void}) { const [url,setUrl]=useState(''); const [model,setModel]=useState(''); return <Page title="Local AI" back={back}><TaskHero icon="computer" title="On this phone" detail="No local model installed"/><PrimaryButton label="Import model file" secondary icon="storage"/><View><SectionTitle caption="Ollama, llama.cpp, vLLM or another compatible endpoint">Compatible server</SectionTitle><Card style={{marginTop:10, gap:10}}><Field value={url} onChangeText={setUrl} placeholder="http://192.168.1.50:11434/v1"/><Field value={model} onChangeText={setModel} placeholder="Model name"/><PrimaryButton label="Save server"/></Card></View></Page>; }

export function AssistantAppsScreen({back}: {back: () => void}) { return <Page title="Assistant apps" back={back}><TaskHero icon="spark" title="Optional app handoff" detail="Use an installed Gemini or ChatGPT app for selected glasses requests."/><SettingsGroup title="Current assistant"><ListRow icon="computer" title="Android assistant" detail="Not selected" onPress={()=>ADNative.action('openAssistantSettings')}/><Divider/><ListRow icon="mic" title="Voice handoff" detail="Advanced bridge setup needed"/><Divider/><ListRow icon="image" title="Image handoff" detail="Advanced bridge and accessibility setup needed"/></SettingsGroup><SettingsGroup title="Advanced handoff"><ListRow icon="settings" title="Choose Android assistant" onPress={()=>ADNative.action('openAssistantSettings')}/><Divider/><ListRow icon="settings" title="Import automation bridge"/><Divider/><ListRow icon="shield" title="Accessibility" onPress={()=>ADNative.action('openAccessibility')}/></SettingsGroup></Page>; }

export function PrivacyScreen({back}: {back: () => void}) { const [redaction,setRedaction]=useState(true); const [confirm,setConfirm]=useState(true); return <Page title="Privacy" back={back}><TaskHero icon="lock" title="Private by default" detail="Keep sensitive context on this phone whenever the capability supports it."/><SettingsGroup title="Memory & transcripts"><Toggle title="Redact sensitive transcript text" detail="Apply privacy filters before long-term storage" value={redaction} onChange={setRedaction}/><Divider/><Toggle title="Ask before protected automation actions" detail="Require confirmation before sensitive Android actions" value={confirm} onChange={setConfirm}/></SettingsGroup></Page>; }
function Toggle({title,detail,value,onChange}:{title:string;detail:string;value:boolean;onChange:(v:boolean)=>void}) { return <View style={styles.toggleRow}><View style={{flex:1}}><Text style={styles.cardTitle}>{title}</Text><Text style={styles.meta}>{detail}</Text></View><Switch value={value} onValueChange={onChange} trackColor={{false:color.grey300,true:color.grey900}} thumbColor={color.white}/></View>; }

export function StorageScreen({back}: {back: () => void}) { return <Page title="Storage" back={back}><TaskHero icon="storage" title="On this phone" detail="Media, models, transcripts and memories stay visible here."/><Card><Metric label="Media" value="—"/><Divider inset={0}/><Metric label="Models" value="—"/><Divider inset={0}/><Metric label="App data" value="—"/></Card><PrimaryButton label="Open Android storage settings" secondary onPress={()=>ADNative.action('openStorageSettings')}/></Page>; }
export function LanguageScreen({back}: {back: () => void}) { const [lang,setLang]=useState('System default'); return <Page title="Language" back={back}><SettingsGroup title="App language">{['System default','English','Hindi'].map((name,i)=><React.Fragment key={name}><ListRow icon="language" title={name} selected={lang===name} onPress={()=>setLang(name)}/>{i<2?<Divider/>:null}</React.Fragment>)}</SettingsGroup></Page>; }
export function PermissionsScreen({back}: {back: () => void}) { return <Page title="Permissions" back={back}><TaskHero icon="shield" title="Only what the glasses need" detail="Permission status should explain capability, not become the product identity."/><Card><ListRow icon="camera" title="Camera" detail="Used for vision and captures" value="Check"/><Divider/><ListRow icon="mic" title="Microphone" detail="Used for voice and recording" value="Check"/><Divider/><ListRow icon="glasses" title="Nearby devices" detail="Used to connect to glasses" value="Check"/></Card></Page>; }
export function AdvancedScreen({navigate,back}:{navigate:Navigate;back:()=>void}) { return <Page title="Advanced" back={back}><Card><ListRow icon="glasses" title="Device diagnostics" detail="Connection, sync, firmware and recovery" onPress={()=>navigate('device')}/><Divider/><ListRow icon="settings" title="Android app settings" detail="Permissions, battery and system controls" onPress={()=>ADNative.action('openAppSettings')}/></Card></Page>; }

export function AboutScreen({back}: {back: () => void}) { return <Page title="About" back={back}><View style={styles.aboutHero}><GlassesImage height={160}/><Text style={styles.aboutName}>AD Glasses</Text><Text style={styles.aboutVersion}>Version alpha</Text></View><Text style={styles.aboutStatement}>A personal companion for displayless smart glasses. The glasses handle the interaction; the phone handles intelligence, tools, memory and media behind the scenes.</Text><SettingsGroup title="Product"><Metric label="Primary interaction" value="Voice through glasses"/><Divider inset={0}/><Metric label="Vision" value="Glasses camera"/><Divider inset={0}/><Metric label="Current information" value="Web through relay"/></SettingsGroup></Page>; }

export function FirmwareScreen({back}: {back: () => void}) { return <Page title="Firmware" back={back}><TaskHero icon="firmware" title="Firmware" detail="No firmware session is active"/><View><SectionTitle>Preflight</SectionTitle><Card style={{marginTop:10}}><CheckRow title="Firmware support" ready={false}/><Divider inset={0}/><CheckRow title="Bluetooth connected" ready={false}/></Card></View><Card><Text style={styles.cardTitle}>Firmware is not available for these glasses yet.</Text><Text style={[styles.meta,{marginTop:5}]}>Updates appear here once the connected glasses have a validated firmware path.</Text></Card></Page>; }
function CheckRow({title,ready}:{title:string;ready:boolean}) { return <View style={styles.checkRow}><Icon name={ready?'check':'info'} size={20} stroke={ready?color.success:color.grey500}/><Text style={[styles.cardTitle,{flex:1}]}>{title}</Text><StatusPill label={ready?'READY':'PENDING'} tone={ready?'success':'neutral'}/></View>; }

export function CapturesScreen({back}: {back: () => void}) { return <Page title="Captures" back={back}><View style={styles.mediaGrid}>{[1,2,3,4].map(i=><View key={i} style={styles.mediaTile}><View style={styles.mediaPreview}><Icon name="image" size={30} stroke={color.grey500}/></View><Text style={styles.cardTitle}>Capture {i}</Text><Text style={styles.meta}>Photo from glasses</Text></View>)}</View><PrimaryButton label="Sync from glasses" secondary icon="sync"/></Page>; }
export function RecordingsScreen({back}: {back: () => void}) { return <Page title="Recordings" back={back}>{[1,2,3].map(i=><Card key={i}><View style={styles.recordingRow}><View style={styles.playButton}><Icon name="play" size={20}/></View><View style={{flex:1}}><Text style={styles.cardTitle}>Today · 10:{20+i}</Text><Text style={styles.meta}>{8+i} min · Glasses</Text></View><StatusPill label="TEXT"/></View><View style={styles.waveform}>{Array.from({length:24}).map((_,j)=><View key={j} style={[styles.waveBar,{height:6+((j*7)%18)}]}/>)}</View></Card>)}</Page>; }
export function NotesScreen({back}: {back: () => void}) { return <Page title="Notes & summaries" back={back}>{['Project thoughts','Walk notes','DayNote · Sunday'].map((title,i)=><Card key={title}><Text style={styles.noteDate}>AUG {17-i}</Text><Text style={styles.noteTitle}>{title}</Text><Text style={styles.notePreview}>A concise preview of the note lives here. Content leads; the decorative icon does not.</Text></Card>)}</Page>; }

const styles = StyleSheet.create({
  glassesSettingsCard:{minHeight:92,borderRadius:radius.card,backgroundColor:color.surface,borderWidth:1,borderColor:color.grey200,paddingHorizontal:space.md,flexDirection:'row',alignItems:'center',gap:14},
  cardTitle:{...type.cardTitle,color:color.ink}, meta:{...type.meta,color:color.grey700}, groupLabel:{...type.meta,color:color.grey700,marginLeft:4},
  deviceCard:{padding:0,overflow:'hidden'}, deviceStage:{height:190,backgroundColor:color.grey100,justifyContent:'center',paddingHorizontal:12}, deviceSummary:{padding:space.md,flexDirection:'row',gap:10,alignItems:'center'}, offlineDot:{width:8,height:8,borderRadius:4,backgroundColor:color.grey500}, heroCardTitle:{...type.section,color:color.ink},
  pairingHero:{alignItems:'center',paddingVertical:space.lg},scanOuter:{width:170,height:170,borderRadius:85,backgroundColor:'#ECECEE',alignItems:'center',justifyContent:'center'},scanInner:{width:94,height:94,borderRadius:47,backgroundColor:color.surface,alignItems:'center',justifyContent:'center',borderWidth:1,borderColor:color.grey200},pairingTitle:{...type.title,color:color.ink,marginTop:space.xl},centerBody:{...type.body,color:color.grey700,textAlign:'center',maxWidth:330,marginTop:7},
  taskHero:{flexDirection:'row',alignItems:'center',gap:13},metricRow:{minHeight:48,flexDirection:'row',alignItems:'center'},metricLabel:{...type.body,color:color.ink,flex:1},metricValue:{...type.meta,color:color.grey700,maxWidth:'58%',textAlign:'right'},
  capabilityHero:{paddingTop:space.xs,gap:10},capabilityIcon:{width:64,height:64,borderRadius:20,backgroundColor:color.grey100,alignItems:'center',justifyContent:'center'},kicker:{...type.micro,color:color.grey700,letterSpacing:1.1,marginTop:4},capabilitySummary:{...type.title,color:color.ink,maxWidth:380},toggleRow:{minHeight:64,flexDirection:'row',alignItems:'center',gap:12},
  aboutHero:{alignItems:'center',paddingVertical:space.lg},aboutName:{...type.display,color:color.ink,marginTop:8},aboutVersion:{...type.meta,color:color.grey700,marginTop:4},aboutStatement:{...type.body,color:color.grey700,fontSize:17,lineHeight:25},checkRow:{minHeight:54,flexDirection:'row',alignItems:'center',gap:10},
  mediaGrid:{flexDirection:'row',flexWrap:'wrap',gap:10},mediaTile:{width:'48.5%',backgroundColor:color.surface,borderRadius:radius.card,padding:10,borderWidth:1,borderColor:color.grey200},mediaPreview:{height:130,borderRadius:15,backgroundColor:color.grey100,alignItems:'center',justifyContent:'center',marginBottom:10},recordingRow:{flexDirection:'row',alignItems:'center',gap:12},playButton:{width:44,height:44,borderRadius:22,backgroundColor:color.grey100,alignItems:'center',justifyContent:'center'},waveform:{height:34,flexDirection:'row',alignItems:'center',gap:3,marginTop:14},waveBar:{width:3,borderRadius:2,backgroundColor:color.grey500},noteDate:{...type.micro,color:color.grey500,letterSpacing:1},noteTitle:{...type.title,color:color.ink,marginTop:10},notePreview:{...type.body,color:color.grey700,marginTop:7},
});
