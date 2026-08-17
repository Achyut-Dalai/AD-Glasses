import {NativeModules} from 'react-native';

export type DashboardState = {
  connected: boolean;
  connecting: boolean;
  deviceName: string;
  batteryPercent?: number;
  storageLabel?: string;
  syncActive: boolean;
};

export type ConversationMessage = {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  createdAt: number;
};

export type ConversationState = {
  threadId: string;
  messages: ConversationMessage[];
};

export type CaptureItem = {
  id: number;
  displayName: string;
  uri: string;
  isVideo: boolean;
};

export type RecordingItem = {
  id: number;
  startedAt: number;
  endedAt: number;
  durationSec: number;
  deviceClass: string;
  captureSource: string;
  audioPath: string;
};

export type NoteItem = {
  id: number;
  title: string;
  summary: string;
  createdAt: number;
  updatedAt: number;
};

type Bridge = {
  getDashboardState?: () => Promise<DashboardState>;
  getConversation?: () => Promise<ConversationState>;
  newConversation?: () => Promise<ConversationState>;
  sendPrompt?: (text: string, webRequested: boolean) => Promise<ConversationState>;
  getCaptures?: () => Promise<CaptureItem[]>;
  getRecordings?: () => Promise<RecordingItem[]>;
  getNotes?: () => Promise<NoteItem[]>;
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

const emptyConversation: ConversationState = {threadId: '', messages: []};

export const ADNative = {
  async dashboard(): Promise<DashboardState> {
    if (!native?.getDashboardState) return fallbackDashboard;
    try {
      return await native.getDashboardState();
    } catch {
      return fallbackDashboard;
    }
  },
  async conversation(): Promise<ConversationState> {
    if (!native?.getConversation) return emptyConversation;
    try {
      return await native.getConversation();
    } catch {
      return emptyConversation;
    }
  },
  async newConversation(): Promise<ConversationState> {
    if (!native?.newConversation) return emptyConversation;
    return native.newConversation();
  },
  async sendPrompt(text: string, webRequested = false): Promise<ConversationState> {
    if (!native?.sendPrompt) return emptyConversation;
    return native.sendPrompt(text, webRequested);
  },
  async captures(): Promise<CaptureItem[]> {
    if (!native?.getCaptures) return [];
    try { return await native.getCaptures(); } catch { return []; }
  },
  async recordings(): Promise<RecordingItem[]> {
    if (!native?.getRecordings) return [];
    try { return await native.getRecordings(); } catch { return []; }
  },
  async notes(): Promise<NoteItem[]> {
    if (!native?.getNotes) return [];
    try { return await native.getNotes(); } catch { return []; }
  },
  open(route: string) {
    native?.openNativeRoute?.(route);
  },
  action(action: string, payload?: Record<string, unknown>) {
    native?.runAction?.(action, payload);
  },
};
