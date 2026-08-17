import React, {useEffect, useState} from 'react';
import {Image, StyleSheet, Text, View} from 'react-native';
import {
  Card,
  GlassesImage,
  PressableScale,
  PrimaryButton,
  Screen,
  TopBar,
} from '../design/components';
import {Icon} from '../design/icons';
import {color, radius, space, type} from '../design/tokens';
import {ADMediaPlayback, PlaybackState} from '../native/ADMediaPlayback';
import {ADNative, CaptureItem, NoteItem, RecordingItem} from '../native/ADNative';

export function CapturesScreen({back}: {back: () => void}) {
  const [items, setItems] = useState<CaptureItem[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    ADNative.captures().then(setItems).finally(() => setLoading(false));
  }, []);

  return (
    <Screen>
      <TopBar title="Captures" onBack={back}/>
      {loading ? (
        <MemoryEmpty icon="image" title="Loading captures" detail="Reading media already synced from your glasses."/>
      ) : items.length === 0 ? (
        <MemoryEmpty icon="image" title="No captures yet" detail="Photos and videos from your glasses will collect here after a sync."/>
      ) : (
        <View style={styles.captureGrid}>
          {items.map(item => <CaptureCard key={item.id} item={item}/>) }
        </View>
      )}
      <PrimaryButton label="Sync from glasses" secondary icon="sync" onPress={() => ADNative.action('startSync')}/>
    </Screen>
  );
}

function CaptureCard({item}: {item: CaptureItem}) {
  return (
    <PressableScale onPress={() => ADNative.action('openUri', {uri: item.uri})} style={styles.captureCard} accessibilityLabel={item.displayName}>
      <View style={styles.capturePreview}>
        {item.isVideo ? (
          <>
            <View style={styles.videoPlaceholder}><Icon name="video" size={30} stroke={color.grey500}/></View>
            <View style={styles.videoPlay}><Icon name="play" size={19} stroke={color.white}/></View>
          </>
        ) : (
          <Image source={{uri: item.uri}} resizeMode="cover" style={styles.captureImage}/>
        )}
      </View>
      <Text style={styles.captureName} numberOfLines={1}>{item.displayName}</Text>
      <Text style={styles.meta}>{item.isVideo ? 'Video from glasses' : 'Photo from glasses'}</Text>
    </PressableScale>
  );
}

export function RecordingsScreen({back}: {back: () => void}) {
  const [items, setItems] = useState<RecordingItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [playback, setPlayback] = useState<PlaybackState>({playing: false});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    ADNative.recordings().then(setItems).finally(() => setLoading(false));
    const sub = ADMediaPlayback.subscribe(setPlayback);
    return () => {
      sub.remove();
      ADMediaPlayback.stop();
    };
  }, []);

  const toggle = async (item: RecordingItem) => {
    setError(null);
    try {
      setPlayback(await ADMediaPlayback.toggle(String(item.id), item.audioPath));
    } catch (playError) {
      setError(playError instanceof Error ? playError.message : 'Couldn’t play that recording.');
    }
  };

  return (
    <Screen>
      <TopBar title="Recordings" onBack={back}/>
      {error ? <Card style={styles.errorCard}><Text style={styles.errorText}>{error}</Text></Card> : null}
      {loading ? (
        <MemoryEmpty icon="wave" title="Loading recordings" detail="Reading audio sessions on this phone."/>
      ) : items.length === 0 ? (
        <MemoryEmpty icon="wave" title="No recordings yet" detail="Record from Home or start Soundbites through the glasses."/>
      ) : items.map(item => {
        const playing = playback.playing && playback.id === String(item.id);
        return (
          <Card key={item.id} style={styles.recordingCard}>
            <PressableScale onPress={() => toggle(item)} accessibilityLabel={playing ? 'Stop recording' : 'Play recording'}>
              <View style={styles.recordingHeader}>
                <View style={[styles.playButton, playing && styles.playButtonActive]}>
                  {playing ? <Text style={styles.pauseMark}>Ⅱ</Text> : <Icon name="play" size={20}/>} 
                </View>
                <View style={styles.recordingCopy}>
                  <Text style={styles.recordingTitle}>{formatDate(item.startedAt)}</Text>
                  <Text style={styles.meta}>{formatDuration(item.durationSec)} · {friendlySource(item.captureSource)}</Text>
                </View>
                <Text style={styles.audioLabel}>{playing ? 'PLAYING' : 'AUDIO'}</Text>
              </View>
            </PressableScale>
            <Waveform active={playing} seed={item.id}/>
          </Card>
        );
      })}
    </Screen>
  );
}

function Waveform({active, seed}: {active: boolean; seed: number}) {
  return (
    <View style={styles.waveform}>
      {Array.from({length: 34}).map((_, index) => (
        <View
          key={index}
          style={[
            styles.waveBar,
            {height: 5 + ((index * 11 + seed * 3) % 21)},
            active && styles.waveBarActive,
          ]}
        />
      ))}
    </View>
  );
}

export function NotesScreen({back}: {back: () => void}) {
  const [items, setItems] = useState<NoteItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  useEffect(() => {
    ADNative.notes().then(setItems).finally(() => setLoading(false));
  }, []);

  return (
    <Screen>
      <TopBar title="Notes & summaries" onBack={back}/>
      {loading ? (
        <MemoryEmpty icon="note" title="Loading notes" detail="Reading your private memory on this phone."/>
      ) : items.length === 0 ? (
        <MemoryEmpty icon="note" title="No notes yet" detail="Soundbites, DayNote and transcript-derived notes will appear here."/>
      ) : items.map(item => {
        const expanded = expandedId === item.id;
        return (
          <Card key={item.id} onPress={() => setExpandedId(expanded ? null : item.id)} style={styles.noteCard}>
            <Text style={styles.noteDate}>{formatDay(item.createdAt)}</Text>
            <Text style={styles.noteTitle}>{item.title || 'Untitled note'}</Text>
            <Text style={styles.noteBody} numberOfLines={expanded ? undefined : 4}>{item.summary}</Text>
            {item.summary.length > 160 ? <Text style={styles.readMore}>{expanded ? 'Show less' : 'Read note'}</Text> : null}
          </Card>
        );
      })}
    </Screen>
  );
}

function MemoryEmpty({icon, title, detail}: {icon: 'image' | 'wave' | 'note'; title: string; detail: string}) {
  return (
    <View style={styles.empty}>
      <View style={styles.emptyGlasses}><GlassesImage height={82} dimmed/></View>
      <View style={styles.emptyIcon}><Icon name={icon} size={22}/></View>
      <Text style={styles.emptyTitle}>{title}</Text>
      <Text style={styles.emptyBody}>{detail}</Text>
    </View>
  );
}

function formatDate(ms: number) {
  return new Date(ms).toLocaleString(undefined, {month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'});
}
function formatDay(ms: number) {
  return new Date(ms).toLocaleDateString(undefined, {month: 'short', day: 'numeric', year: 'numeric'}).toUpperCase();
}
function formatDuration(seconds: number) {
  const minutes = Math.max(1, Math.round(seconds / 60));
  return `${minutes} min`;
}
function friendlySource(source: string) {
  const text = source.replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

const styles = StyleSheet.create({
  captureGrid: {flexDirection: 'row', flexWrap: 'wrap', gap: 10},
  captureCard: {width: '48.5%', backgroundColor: color.surface, borderRadius: radius.card, padding: 9, borderWidth: 1, borderColor: color.grey200},
  capturePreview: {height: 144, borderRadius: 15, overflow: 'hidden', backgroundColor: color.grey100},
  captureImage: {width: '100%', height: '100%'},
  videoPlaceholder: {width: '100%', height: '100%', alignItems: 'center', justifyContent: 'center'},
  videoPlay: {position: 'absolute', left: '50%', top: '50%', marginLeft: -22, marginTop: -22, width: 44, height: 44, borderRadius: 22, backgroundColor: color.ink, alignItems: 'center', justifyContent: 'center'},
  captureName: {...type.cardTitle, color: color.ink, marginTop: 10},
  meta: {...type.meta, color: color.grey700},
  errorCard: {backgroundColor: color.errorSoft, borderColor: color.errorSoft},
  errorText: {...type.meta, color: color.error},
  recordingCard: {paddingBottom: 12},
  recordingHeader: {flexDirection: 'row', alignItems: 'center', gap: 12},
  playButton: {width: 44, height: 44, borderRadius: 22, backgroundColor: color.grey100, alignItems: 'center', justifyContent: 'center'},
  playButtonActive: {backgroundColor: color.ink},
  pauseMark: {...type.cardTitle, color: color.white, letterSpacing: -2},
  recordingCopy: {flex: 1, gap: 2},
  recordingTitle: {...type.cardTitle, color: color.ink},
  audioLabel: {...type.micro, color: color.grey500, letterSpacing: 1},
  waveform: {height: 38, flexDirection: 'row', alignItems: 'center', gap: 3, marginTop: 14, overflow: 'hidden'},
  waveBar: {width: 2.5, borderRadius: 2, backgroundColor: color.grey300},
  waveBarActive: {backgroundColor: color.grey700},
  noteCard: {paddingVertical: 19},
  noteDate: {...type.micro, color: color.grey500, letterSpacing: 1},
  noteTitle: {...type.title, color: color.ink, marginTop: 9},
  noteBody: {...type.body, color: color.grey700, marginTop: 8},
  readMore: {...type.meta, color: color.ink, marginTop: 12},
  empty: {minHeight: 360, alignItems: 'center', justifyContent: 'center', paddingHorizontal: space.lg},
  emptyGlasses: {width: 156, height: 84, justifyContent: 'center'},
  emptyIcon: {width: 46, height: 46, borderRadius: 15, backgroundColor: color.grey100, alignItems: 'center', justifyContent: 'center', marginTop: 8},
  emptyTitle: {...type.title, color: color.ink, marginTop: 16},
  emptyBody: {...type.body, color: color.grey700, textAlign: 'center', maxWidth: 320, marginTop: 6},
});
