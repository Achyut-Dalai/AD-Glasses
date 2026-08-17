import {NativeModules} from 'react-native';

export type DashboardState = {
  connected: boolean;
  connecting: boolean;
  deviceName: string;
  batteryPercent?: number;
  storageLabel?: string;
  syncActive: boolean;
};

type Bridge = {
  getDashboardState?: () => Promise<DashboardState>;
  openNativeRoute?: (route: string) => void;
  runAction?: (action: string, payload?: Record<string, unknown>) => void;
};

const native = NativeModules.ADGlassesBridge as Bridge | undefined;

export const fallbackDashboard: DashboardState = {
  connected: false,
  connecting: false,
  deviceName: 'Glasses',
  syncActive: false,
};

export const ADNative = {
  async dashboard(): Promise<DashboardState> {
    if (!native?.getDashboardState) return fallbackDashboard;
    try {
      return await native.getDashboardState();
    } catch {
      return fallbackDashboard;
    }
  },
  open(route: string) {
    native?.openNativeRoute?.(route);
  },
  action(action: string, payload?: Record<string, unknown>) {
    native?.runAction?.(action, payload);
  },
};
