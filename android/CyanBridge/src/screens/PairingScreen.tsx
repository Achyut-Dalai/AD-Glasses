import React, {useEffect, useState} from 'react';
import {StyleSheet, Text, View} from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import {
  Card,
  Divider,
  GlassesImage,
  PressableScale,
  PrimaryButton,
  Screen,
  TopBar,
  useReducedMotion,
} from '../design/components';
import {Icon} from '../design/icons';
import {color, motion, type} from '../design/tokens';
import {ADPairing, emptyPairingState, PairingDevice, PairingState} from '../native/ADPairing';
import {useDashboardState} from '../hooks/useDashboardState';

export function PairingScreen({back}: {back: () => void}) {
  const [state, setState] = useState<PairingState>(emptyPairingState);
  const [connectingMac, setConnectingMac] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const dashboard = useDashboardState(800);

  useEffect(() => {
    let alive = true;
    ADPairing.state().then(next => alive && setState(next));
    const subscription = ADPairing.subscribe(next => {
      if (alive) setState(next);
    });
    ADPairing.start().then(next => alive && setState(next)).catch(error => {
      if (alive) setLocalError(messageFor(error));
    });
    return () => {
      alive = false;
      subscription.remove();
      ADPairing.stop();
    };
  }, []);

  useEffect(() => {
    if (connectingMac && dashboard.connected) back();
  }, [back, connectingMac, dashboard.connected]);

  const scan = async () => {
    setLocalError(null);
    try {
      setState(await ADPairing.start());
    } catch (error) {
      setLocalError(messageFor(error));
    }
  };

  const connect = async (device: PairingDevice) => {
    if (!device.pairable || connectingMac) return;
    setLocalError(null);
    setConnectingMac(device.macAddress);
    try {
      const started = await ADPairing.connect(device.macAddress);
      if (!started) {
        setConnectingMac(null);
        setLocalError('Couldn’t start that connection.');
      }
    } catch (error) {
      setConnectingMac(null);
      setLocalError(messageFor(error));
    }
  };

  const error = localError ?? state.error;
  const headline = state.scanning
    ? 'Looking for nearby glasses'
    : state.devices.length
      ? 'Glasses found'
      : 'Find your glasses';
  const detail = state.scanning
    ? 'Keep the glasses nearby and ready to pair.'
    : state.devices.length
      ? 'Choose a detected pair. The app handles the setup; the glasses stay the hero.'
      : 'Scan again when your glasses are nearby and ready.';

  return (
    <Screen>
      <TopBar title="Connect glasses" onBack={back}/>
      <View style={styles.hero}>
        <PairingPulse active={state.scanning}/>
        <Text style={styles.headline}>{headline}</Text>
        <Text style={styles.detail}>{detail}</Text>
      </View>

      {state.scanning ? (
        <PrimaryButton label="Stop scanning" secondary onPress={() => ADPairing.stop()}/>
      ) : (
        <PrimaryButton label={state.devices.length ? 'Scan again' : 'Scan for glasses'} icon="sync" onPress={scan}/>
      )}

      {error ? (
        <Card style={styles.messageCard}>
          <View style={styles.messageRow}>
            <Icon name="info" size={20} stroke={color.grey700}/>
            <Text style={styles.message}>{error}</Text>
          </View>
        </Card>
      ) : null}

      {state.devices.length ? (
        <View>
          <Text style={styles.sectionLabel}>NEARBY</Text>
          <Card style={styles.deviceList}>
            {state.devices.map((device, index) => (
              <React.Fragment key={device.macAddress}>
                <DeviceRow
                  device={device}
                  connecting={connectingMac === device.macAddress}
                  onPress={() => connect(device)}
                />
                {index < state.devices.length - 1 ? <Divider inset={78}/> : null}
              </React.Fragment>
            ))}
          </Card>
        </View>
      ) : !state.scanning && !error ? (
        <Card>
          <Text style={styles.emptyTitle}>No supported glasses found</Text>
          <Text style={styles.emptyBody}>Check Bluetooth, keep the glasses close, and make sure another companion app is not holding the connection.</Text>
        </Card>
      ) : null}
    </Screen>
  );
}

function PairingPulse({active}: {active: boolean}) {
  const reduced = useReducedMotion();
  const progress = useSharedValue(active && !reduced ? 0 : 0.35);

  useEffect(() => {
    if (!active || reduced) {
      progress.value = withTiming(0.35, {duration: motion.fast});
      return;
    }
    progress.value = withRepeat(withTiming(1, {duration: 1100}), -1, true);
  }, [active, progress, reduced]);

  const outer = useAnimatedStyle(() => ({
    opacity: 0.28 + progress.value * 0.18,
    transform: [{scale: 0.94 + progress.value * 0.08}],
  }));
  const inner = useAnimatedStyle(() => ({
    opacity: 0.5 + progress.value * 0.2,
    transform: [{scale: 0.96 + progress.value * 0.04}],
  }));

  return (
    <View style={styles.pulseStage}>
      <Animated.View style={[styles.outerPulse, outer]}/>
      <Animated.View style={[styles.innerPulse, inner]}/>
      <View style={styles.glassesObject}><GlassesImage height={110}/></View>
    </View>
  );
}

function DeviceRow({device, connecting, onPress}: {device: PairingDevice; connecting: boolean; onPress: () => void}) {
  const disabled = !device.pairable || connecting;
  return (
    <PressableScale onPress={disabled ? undefined : onPress} accessibilityLabel={`Connect ${device.name}`}>
      <View style={styles.deviceRow}>
        <View style={styles.miniGlasses}><GlassesImage height={45} dimmed={!device.pairable}/></View>
        <View style={styles.deviceCopy}>
          <Text style={styles.deviceName} numberOfLines={1}>{device.name}</Text>
          <Text style={styles.deviceDetail} numberOfLines={1}>
            {connecting ? 'Connecting…' : device.pairable ? signalLabel(device.rssi) : 'Not supported yet'}
          </Text>
        </View>
        {connecting ? <Text style={styles.trailing}>•••</Text> : <Icon name="chevron" size={19} stroke={device.pairable ? color.grey500 : color.grey300}/>} 
      </View>
    </PressableScale>
  );
}

function signalLabel(rssi: number) {
  if (rssi >= -55) return 'Strong signal';
  if (rssi >= -70) return 'Good signal';
  if (rssi >= -82) return 'Nearby';
  return 'Weak signal';
}

function messageFor(error: unknown) {
  if (error instanceof Error) return error.message;
  if (typeof error === 'string') return error;
  return 'Couldn’t scan for glasses.';
}

const styles = StyleSheet.create({
  hero: {alignItems: 'center', paddingTop: 4, paddingBottom: 4},
  pulseStage: {width: 206, height: 190, alignItems: 'center', justifyContent: 'center'},
  outerPulse: {position: 'absolute', width: 186, height: 186, borderRadius: 93, backgroundColor: color.grey200},
  innerPulse: {position: 'absolute', width: 144, height: 144, borderRadius: 72, backgroundColor: color.grey100},
  glassesObject: {width: 174, height: 110, justifyContent: 'center'},
  headline: {...type.title, color: color.ink, textAlign: 'center', marginTop: 10},
  detail: {...type.body, color: color.grey700, textAlign: 'center', maxWidth: 330, marginTop: 7},
  messageCard: {paddingVertical: 13},
  messageRow: {flexDirection: 'row', gap: 10, alignItems: 'flex-start'},
  message: {...type.meta, color: color.grey700, flex: 1},
  sectionLabel: {...type.micro, color: color.grey500, letterSpacing: 1.1, marginLeft: 4, marginBottom: 8},
  deviceList: {paddingVertical: 2},
  deviceRow: {minHeight: 74, flexDirection: 'row', alignItems: 'center', gap: 12},
  miniGlasses: {width: 66, height: 46, justifyContent: 'center'},
  deviceCopy: {flex: 1, gap: 2},
  deviceName: {...type.cardTitle, color: color.ink},
  deviceDetail: {...type.meta, color: color.grey700},
  trailing: {...type.meta, color: color.grey500, letterSpacing: 2},
  emptyTitle: {...type.cardTitle, color: color.ink},
  emptyBody: {...type.body, color: color.grey700, marginTop: 5},
});
