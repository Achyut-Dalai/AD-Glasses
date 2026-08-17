import React, {useEffect, useMemo, useState} from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {
  Card,
  Divider,
  Field,
  IconTile,
  ListRow,
  PrimaryButton,
  Screen,
  SectionTitle,
  TopBar,
} from '../design/components';
import {color, space, type} from '../design/tokens';
import {useProductSettings} from '../hooks/useProductSettings';
import {ADNative} from '../native/ADNative';
import {ADProductSettings} from '../native/ADProductSettings';

export function LocalAIScreen({back}: {back: () => void}) {
  const {settings, setSettings, refresh} = useProductSettings();
  const [url, setUrl] = useState('');
  const [model, setModel] = useState('');
  const [saved, setSaved] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);

  useEffect(() => {
    setUrl(settings.remoteUrl);
    setModel(settings.remoteModel);
  }, [settings.remoteModel, settings.remoteUrl]);

  const selectedModel = useMemo(
    () => settings.localModels.find(item => item.selected),
    [settings.localModels],
  );

  const selectModel = (id: string) => {
    ADProductSettings.selectLocalModel(id);
    setSettings(current => ({
      ...current,
      localModels: current.localModels.map(item => ({...item, selected: item.id === id})),
    }));
  };

  const importModel = async () => {
    if (importing) return;
    setImportError(null);
    setImporting(true);
    try {
      const imported = await ADProductSettings.importLocalModel();
      if (imported) await refresh();
    } catch (error) {
      setImportError(error instanceof Error ? error.message : 'Couldn’t import that model.');
    } finally {
      setImporting(false);
    }
  };

  const save = () => {
    ADNative.action('saveRemoteServer', {url, model, enabled: true});
    setSaved(true);
  };

  return (
    <Screen>
      <TopBar title="Local AI" onBack={back}/>
      <View style={styles.lead}>
        <IconTile name="computer"/>
        <Text style={styles.title}>Intelligence on this phone.</Text>
        <Text style={styles.body}>Run a managed model locally, or connect AD Glasses to a compatible endpoint on your own network.</Text>
      </View>

      <View>
        <SectionTitle caption={selectedModel ? `Active · ${selectedModel.name}` : 'Private on-device models'}>Installed models</SectionTitle>
        <Card style={styles.cardGap}>
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
            <View style={styles.empty}>
              <Text style={styles.emptyTitle}>No local model installed</Text>
              <Text style={styles.emptyBody}>Import a compatible model file. AD Glasses copies it into private app storage and selects it immediately.</Text>
            </View>
          )}
        </Card>
      </View>

      <PrimaryButton
        label={importing ? 'Importing…' : 'Import model file'}
        secondary
        icon="storage"
        onPress={importing ? undefined : importModel}
      />
      {importError ? <Text style={styles.error}>{importError}</Text> : null}

      <View>
        <SectionTitle caption="Ollama, llama.cpp, vLLM or another OpenAI-compatible endpoint">Compatible server</SectionTitle>
        <Card style={styles.serverCard}>
          <Text style={styles.label}>Server address</Text>
          <Field value={url} onChangeText={value => {setUrl(value); setSaved(false);}} placeholder="http://192.168.1.50:11434/v1"/>
          <Text style={styles.label}>Model</Text>
          <Field value={model} onChangeText={value => {setModel(value); setSaved(false);}} placeholder="Model name"/>
          <PrimaryButton label={saved ? 'Saved' : 'Save server'} onPress={save}/>
        </Card>
      </View>
    </Screen>
  );
}

function formatBytes(bytes: number) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

const styles = StyleSheet.create({
  lead: {paddingTop: space.xs},
  title: {...type.title, color: color.ink, marginTop: 18},
  body: {...type.body, color: color.grey700, marginTop: 7, maxWidth: 380},
  cardGap: {marginTop: 10},
  empty: {paddingVertical: 8},
  emptyTitle: {...type.cardTitle, color: color.ink},
  emptyBody: {...type.body, color: color.grey700, marginTop: 5},
  error: {...type.meta, color: color.error, marginTop: -12},
  serverCard: {marginTop: 10, gap: 10},
  label: {...type.meta, color: color.grey700, marginTop: 3},
});
