import {NativeEventEmitter, NativeModules} from 'react-native';

export type PairingDevice = {
  macAddress: string;
  name: string;
  rssi: number;
  deviceClass: string;
  deviceClassLabel: string;
  pairable: boolean;
};

export type PairingState = {
  scanning: boolean;
  permissionGranted: boolean;
  bluetoothEnabled: boolean;
  devices: PairingDevice[];
  error?: string;
};

type PairingModule = {
  getState?: () => Promise<PairingState>;
  startScan?: () => Promise<PairingState>;
  stopScan?: () => void;
  connect?: (macAddress: string) => Promise<boolean>;
  addListener?: (eventName: string) => void;
  removeListeners?: (count: number) => void;
};

const native = NativeModules.ADPairing as PairingModule | undefined;
const emitter = native ? new NativeEventEmitter(NativeModules.ADPairing) : undefined;

export const emptyPairingState: PairingState = {
  scanning: false,
  permissionGranted: false,
  bluetoothEnabled: false,
  devices: [],
};

export const ADPairing = {
  async state(): Promise<PairingState> {
    if (!native?.getState) return emptyPairingState;
    try {
      return await native.getState();
    } catch {
      return emptyPairingState;
    }
  },
  async start(): Promise<PairingState> {
    if (!native?.startScan) return emptyPairingState;
    return native.startScan();
  },
  stop() {
    native?.stopScan?.();
  },
  async connect(macAddress: string): Promise<boolean> {
    if (!native?.connect) return false;
    return native.connect(macAddress);
  },
  subscribe(listener: (state: PairingState) => void) {
    if (!emitter) return {remove() {}};
    return emitter.addListener('adPairingState', listener);
  },
};
