import React, {useEffect, useMemo, useState} from 'react';
import {Image, StyleSheet, Text, View} from 'react-native';
import {Card, Divider, IconTile, ListRow, PressableScale, PrimaryButton, Reveal, Screen, SectionTitle, StatusPill, TopBar} from '../design/components';
import {Icon, IconName} from '../design/icons';
import {color, radius, space, type} from '../design/tokens';
import {ADMediaPlayback, PlaybackState} from '../native/ADMediaPlayback';
import {ADNative, CaptureItem, NoteItem, RecordingItem} from '../native/ADNative';
import {ADVideoView} from '../native/ADVideoView';
import type {Navigate, RouteEntry} from '../navigation/routes';

function Page({title, back, children}: React.PropsWithChildren<{title: string; back: () => void}>) {
  return <Screen><TopBar title={title} onBack={back}/>{children}</Screen>;
}

function EmptyState({icon, title, detail}: {icon: IconName; title: string; detail: string}) {
  return <View style={styles.empty}><View style={styles.emptyIcon}><Icon name={icon} size={28}/></View><Text style={styles.emptyTitle}>{title}</Text><Text style={styles.centerBody}>{detail}</Text></View>;
}

export function CapturesScreen({navigate, back}: {navigate: Navigate; back: () => void}) {
  const [items, setItems] = useState<CaptureItem[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => { ADNative.captures().then(setItems).finally(() => setLoading(false)); }, []);

  return (
    <Page title="Captures" back={back}>
      <Reveal><Text style={styles.lead}>Photos and videos are memory objects first. Open one to inspect, export or ask AD about that exact moment.</Text></Reveal>
      {loading ? <EmptyState icon="image" title="Loading captures" detail="Reading media already synced from your glasses."/> : items.length === 0 ? <EmptyState icon="image" title="No captures yet" detail="Sync photos and videos from your glasses when you’re ready."/> : (
        <View style={styles.mediaGrid}>
          {items.map(item => (
            <PressableScale key={`${item.id}`} onPress={() => navigate('capture-detail', {id: item.id})} style={styles.mediaTile}>
              <View style={styles.mediaPreview}>
                {!item.isVideo ? <Image source={{uri: item.uri}} style={styles.previewImage} resizeMode="cover"/> : <View style={styles.videoPlaceholder}><Icon name="video" size={30} stroke={color.grey700}/><View style={styles.playOverlay}><Icon name="play" size={17} stroke={color.white}/></View></View>}
              </View>
              <Text style={styles.cardTitle} numberOfLines={1}>{item.displayName}</Text>
              <Text style={styles.meta}>{item.isVideo ? 'Video from glasses' : 'Photo from glasses'}</Text>
            </PressableScale>
          ))}
        </View>
      )}
      <PrimaryButton label="Sync from glasses" secondary icon="sync" onPress={() => ADNative.action('startSync')}/>
    </Page>
  );
}

export function CaptureDetailScreen({route, navigate, back}: {route: RouteEntry; navigate: Navigate; back: () => void}) {
  const id = Number(route.params?.id ?? -1);
  const [item, setItem] = useState<CaptureItem | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => { ADNative.captures().then(items => setItem(items.find(value => value.id === id) ?? null)).finally(() => setLoading(false)); }, [id]);

  if (loading) return <Page title="Capture" back={back}><EmptyState icon="image" title="Loading capture" detail="Preparing this glasses moment."/></Page>;
  if (!item) return <Page title="Capture" back={back}><EmptyState icon="image" title="Capture unavailable" detail="It may have been moved or removed from this phone."/></Page>;

  const ask = () => {
    ADNative.action('setArtifactContext', {kind: 'capture', id: item.id, label: item.displayName});
    navigate('prompt', {artifactKind: 'capture', artifactId: item.id, artifactLabel: item.displayName});
  };

  return (
    <Page title="Capture" back={back}>
      <Reveal>
        <View style={styles.viewer}>
          {item.isVideo ? <ADVideoView uri={item.uri} style={styles.videoView}/> : <Image source={{uri: item.uri}} style={styles.heroImage} resizeMode="contain"/>}
        </View>
      </Reveal>
      <Reveal delay={45}><View><Text style={styles.artifactTitle}>{item.displayName}</Text><Text style={styles.meta}>{item.isVideo ? 'Video captured by your glasses' : 'Photo captured by your glasses'}</Text></View></Reveal>
      <Reveal delay={80}><Card><ListRow icon="spark" title="Ask AD about this" detail={item.isVideo ? 'Continue with this video as the selected artifact' : 'Use this exact photo as visual context'} onPress={ask}/><Divider/><ListRow icon="send" title="Share / export" detail="Use Android’s share sheet when you choose to take this artifact elsewhere" onPress={() => ADNative.action('shareUri', {uri: item.uri, mime: item.isVideo ? 'video/*' : 'image/*'})}/></Card></Reveal>
      {item.isVideo ? <Reveal delay={110}><Card><Text style={styles.cardTitle}>Video context</Text><Text style={[styles.meta, {marginTop: 5}]}>Playback stays inside AD Glasses. Frame-level AI analysis will only claim what the selected video/file engine has actually received.</Text></Card></Reveal> : null}
    </Page>
  );
}

export function RecordingsScreen({navigate, back}: {navigate: Navigate; back: () => void}) {
  const [items, setItems] = useState<RecordingItem[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => { ADNative.recordings().then(setItems).finally(() => setLoading(false)); }, []);

  return <Page title="Recordings" back={back}>{loading ? <EmptyState icon="wave" title="Loading recordings" detail="Reading audio sessions on this phone."/> : items.length === 0 ? <EmptyState icon="wave" title="No recordings yet" detail="Record from Home or start Soundbites through the glasses."/> : items.map(item => <PressableScale key={`${item.id}`} onPress={() => navigate('recording-detail', {id: item.id})}><Card><View style={styles.recordingRow}><View style={styles.playButton}><Icon name="wave" size={20}/></View><View style={{flex: 1}}><Text style={styles.cardTitle}>{formatDate(item.startedAt)}</Text><Text style={styles.meta}>{formatDuration(item.durationSec)} · {friendlySource(item.captureSource)}</Text></View><StatusPill label={item.transcript ? 'TRANSCRIBED' : item.transcriptionStatus || 'AUDIO'} tone={item.transcript ? 'success' : 'neutral'}/></View><Waveform/></Card></PressableScale>)}</Page>;
}

export function RecordingDetailScreen({route, navigate, back}: {route: RouteEntry; navigate: Navigate; back: () => void}) {
  const id = Number(route.params?.id ?? -1);
  const [item, setItem] = useState<RecordingItem | null>(null);
  const [playback, setPlayback] = useState<PlaybackState>({playing: false});
  useEffect(() => { ADNative.recordings().then(items => setItem(items.find(value => value.id === id) ?? null)); }, [id]);
  useEffect(() => { const sub = ADMediaPlayback.subscribe(setPlayback); return () => { sub.remove(); ADMediaPlayback.stop(); }; }, []);

  const isPlaying = item ? playback.playing && playback.id === String(item.id) : false;
  if (!item) return <Page title="Recording" back={back}><EmptyState icon="wave" title="Loading recording" detail="Preparing audio and transcript."/></Page>;

  const ask = () => {
    ADNative.action('setArtifactContext', {kind: 'recording', id: item.id, label: formatDate(item.startedAt)});
    navigate('prompt', {artifactKind: 'recording', artifactId: item.id, artifactLabel: `Recording · ${formatDate(item.startedAt)}`});
  };

  return (
    <Page title="Recording" back={back}>
      <Reveal><Card style={styles.audioHero}><View style={styles.recordingTop}><IconTile name="wave" dark/><View style={{flex: 1}}><Text style={styles.artifactTitle}>{formatDate(item.startedAt)}</Text><Text style={styles.meta}>{formatDuration(item.durationSec)} · {friendlySource(item.captureSource)}</Text></View></View><Waveform large/><PrimaryButton label={isPlaying ? 'Stop playback' : 'Play recording'} icon="play" onPress={() => ADMediaPlayback.toggle(String(item.id), item.audioPath).then(setPlayback)}/></Card></Reveal>
      <Reveal delay={55}><View><SectionTitle caption={item.transcript ? 'Stored transcript available to contextual AD questions' : 'The audio remains usable even without a stored transcript'}>Transcript</SectionTitle><Card style={{marginTop: 10}}><Text style={item.transcript ? styles.transcript : styles.meta}>{item.transcript || transcriptionMessage(item.transcriptionStatus)}</Text></Card></View></Reveal>
      <Reveal delay={90}><Card><ListRow icon="spark" title="Ask AD about this" detail={item.transcript ? 'Use this transcript as hidden context' : 'Continue from this recording without inventing a transcript'} onPress={ask}/><Divider/><ListRow icon="send" title="Share / export audio" onPress={() => ADNative.action('shareFile', {path: item.audioPath, mime: 'audio/*'})}/></Card></Reveal>
    </Page>
  );
}

export function NotesScreen({navigate, back}: {navigate: Navigate; back: () => void}) {
  const [items, setItems] = useState<NoteItem[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => { ADNative.notes().then(setItems).finally(() => setLoading(false)); }, []);
  return <Page title="Notes & summaries" back={back}>{loading ? <EmptyState icon="note" title="Loading notes" detail="Reading your private memory on this phone."/> : items.length === 0 ? <EmptyState icon="note" title="No notes yet" detail="Soundbites, DayNote and transcript-derived notes will appear here."/> : items.map(item => <PressableScale key={`${item.id}`} onPress={() => navigate('note-detail', {id: item.id})}><Card><Text style={styles.noteDate}>{formatDate(item.createdAt).toUpperCase()}</Text><Text style={styles.noteTitle}>{item.title || 'Untitled note'}</Text><Text style={styles.notePreview} numberOfLines={4}>{item.summary}</Text></Card></PressableScale>)}</Page>;
}

export function NoteDetailScreen({route, navigate, back}: {route: RouteEntry; navigate: Navigate; back: () => void}) {
  const id = Number(route.params?.id ?? -1);
  const [item, setItem] = useState<NoteItem | null>(null);
  useEffect(() => { ADNative.notes().then(items => setItem(items.find(value => value.id === id) ?? null)); }, [id]);
  if (!item) return <Page title="Note" back={back}><EmptyState icon="note" title="Loading note" detail="Opening your saved memory."/></Page>;

  const label = item.title || 'Untitled note';
  const ask = () => {
    ADNative.action('setArtifactContext', {kind: 'note', id: item.id, label});
    navigate('prompt', {artifactKind: 'note', artifactId: item.id, artifactLabel: label});
  };

  return <Page title="Note" back={back}><Reveal><Text style={styles.noteDate}>{formatDate(item.createdAt).toUpperCase()}</Text><Text style={styles.noteHeroTitle}>{label}</Text></Reveal><Reveal delay={45}><Card><Text style={styles.noteBody}>{item.summary || 'This note has no saved body yet.'}</Text></Card></Reveal><Reveal delay={85}><Card><ListRow icon="spark" title="Ask AD about this" detail="Continue with this note as trusted context" onPress={ask}/></Card></Reveal></Page>;
}

function Waveform({large = false}: {large?: boolean}) {
  const bars = useMemo(() => Array.from({length: large ? 34 : 24}), [large]);
  return <View style={[styles.waveform, large && styles.waveformLarge]}>{bars.map((_, index) => <View key={index} style={[styles.waveBar, {height: (large ? 9 : 6) + ((index * 7) % (large ? 25 : 18))}]}/>)}</View>;
}

function formatDate(ms: number) { return new Date(ms).toLocaleString(undefined, {month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'}); }
function formatDuration(seconds: number) { const minutes = Math.max(1, Math.round(seconds / 60)); return `${minutes} min`; }
function friendlySource(value: string) { return value.replaceAll('_', ' ').toLowerCase().replace(/^./, char => char.toUpperCase()); }
function transcriptionMessage(status: string) { return status ? `Transcription status: ${status.toLowerCase().replaceAll('_', ' ')}` : 'No transcript has been stored for this recording yet.'; }

const styles = StyleSheet.create({
  lead: {...type.body, color: color.grey700, maxWidth: 390},
  mediaGrid: {flexDirection: 'row', flexWrap: 'wrap', gap: 10},
  mediaTile: {width: '48.5%', gap: 9},
  mediaPreview: {height: 150, borderRadius: radius.card, overflow: 'hidden', backgroundColor: color.grey100},
  previewImage: {width: '100%', height: '100%'},
  videoPlaceholder: {flex: 1, alignItems: 'center', justifyContent: 'center'},
  playOverlay: {position: 'absolute', width: 44, height: 44, borderRadius: 22, backgroundColor: color.ink, alignItems: 'center', justifyContent: 'center'},
  cardTitle: {...type.cardTitle, color: color.ink},
  meta: {...type.meta, color: color.grey700},
  viewer: {width: '100%', minHeight: 320, borderRadius: radius.hero, overflow: 'hidden', backgroundColor: color.ink},
  heroImage: {width: '100%', height: 420, backgroundColor: color.ink},
  videoView: {width: '100%', height: 320},
  artifactTitle: {...type.title, color: color.ink},
  recordingRow: {flexDirection: 'row', alignItems: 'center', gap: 12},
  recordingTop: {flexDirection: 'row', alignItems: 'center', gap: 12},
  playButton: {width: 44, height: 44, borderRadius: 22, backgroundColor: color.grey100, alignItems: 'center', justifyContent: 'center'},
  waveform: {height: 34, flexDirection: 'row', alignItems: 'center', gap: 3, marginTop: 14},
  waveformLarge: {height: 52, marginVertical: 8},
  waveBar: {width: 3, borderRadius: 2, backgroundColor: color.grey700, flexShrink: 1},
  audioHero: {gap: space.sm},
  transcript: {...type.body, color: color.ink, lineHeight: 23},
  noteDate: {...type.micro, color: color.grey500, letterSpacing: 1.1},
  noteTitle: {...type.section, color: color.ink, marginTop: 5},
  notePreview: {...type.body, color: color.grey700, marginTop: 8},
  noteHeroTitle: {...type.display, color: color.ink, marginTop: 7},
  noteBody: {...type.body, color: color.ink, lineHeight: 24},
  empty: {minHeight: 310, alignItems: 'center', justifyContent: 'center', paddingHorizontal: space.xl},
  emptyIcon: {width: 62, height: 62, borderRadius: 20, backgroundColor: color.grey100, alignItems: 'center', justifyContent: 'center'},
  emptyTitle: {...type.title, color: color.ink, marginTop: 18},
  centerBody: {...type.body, color: color.grey700, textAlign: 'center', marginTop: 7},
});
