import {NativeEventEmitter, NativeModules} from 'react-native';

export type PlaybackState = {
  playing: boolean;
  id?: string;
};

type PlaybackModule = {
  toggle?: (id: string, path: string) => Promise<PlaybackState>;
  stop?: () => void;
  getState?: () => Promise<PlaybackState>;
  addListener?: (eventName: string) => void;
  removeListeners?: (count: number) => void;
};

const native = NativeModules.ADMediaPlayback as PlaybackModule | undefined;
const emitter = native ? new NativeEventEmitter(NativeModules.ADMediaPlayback) : undefined;

export const ADMediaPlayback = {
  async toggle(id: string, path: string): Promise<PlaybackState> {
    if (!native?.toggle) return {playing: false};
    return native.toggle(id, path);
  },
  stop() {
    native?.stop?.();
  },
  subscribe(listener: (state: PlaybackState) => void) {
    if (!emitter) return {remove() {}};
    return emitter.addListener('adPlaybackState', listener);
  },
};
