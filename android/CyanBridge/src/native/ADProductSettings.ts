import {NativeModules} from 'react-native';

export type LocalModelItem = {
  id: string;
  name: string;
  sizeBytes: number;
  selected: boolean;
};

export type AIProfile = 'BALANCED' | 'FAST' | 'PRIVATE' | 'CUSTOM';
export type ConversationEngine = 'AUTO' | 'GEMINI_LIVE' | 'GEMINI_STANDARD' | 'LOCAL';
export type SpeechEngine = 'AUTO' | 'GEMINI_NATIVE_AUDIO' | 'MOONSHINE' | 'VOSK';
export type VisionEngine = 'AUTO' | 'GEMINI_LIVE' | 'GEMINI_STANDARD' | 'LOCAL_GEMMA';
export type FileEngine = 'AUTO' | 'GEMINI_FILES' | 'GEMINI_INLINE' | 'LOCAL';
export type GroundingPolicy = 'AUTO' | 'ALWAYS' | 'NEVER';
export type VisibleFallbackPolicy = 'ASK' | 'NEVER' | 'ALLOW';

export type ProductSettings = {
  provider: 'Gemini' | 'OpenAI / Codex' | 'Local AI';
  automationExecutor: 'Background / Tasker' | 'Accessibility fallback';
  taskerInstalled: boolean;
  assistantRoleAvailable: boolean;
  assistantRoleHeld: boolean;
  aiProfile: AIProfile;
  conversationEngine: ConversationEngine;
  speechEngine: SpeechEngine;
  visionEngine: VisionEngine;
  fileEngine: FileEngine;
  groundingPolicy: GroundingPolicy;
  visibleFallbackPolicy: VisibleFallbackPolicy;
  screenOffFirst: boolean;
  relayUrl: string;
  relayBackend: 'Gemini' | 'OpenAI / Codex';
  relayConfigured: boolean;
  remoteEnabled: boolean;
  remoteUrl: string;
  remoteModel: string;
  language: string;
  redactNames: boolean;
  transcriptStorage: boolean;
  cameraGranted: boolean;
  bluetoothGranted: boolean;
  microphoneGranted: boolean;
  automationGranted: boolean;
  localModels: LocalModelItem[];
};

type ProductSettingsModule = {
  getSettings?: () => Promise<ProductSettings>;
  requestAssistantRole?: () => Promise<boolean>;
  setAiProfile?: (name: AIProfile) => void;
  setConversationEngine?: (name: ConversationEngine) => void;
  setSpeechEngine?: (name: SpeechEngine) => void;
  setVisionEngine?: (name: VisionEngine) => void;
  setFileEngine?: (name: FileEngine) => void;
  setGroundingPolicy?: (name: GroundingPolicy) => void;
  setVisibleFallbackPolicy?: (name: VisibleFallbackPolicy) => void;
  importLocalModel?: () => Promise<LocalModelItem | null>;
  setLanguage?: (languageName: string) => void;
  setRedactNames?: (enabled: boolean) => void;
  setTranscriptStorage?: (enabled: boolean) => void;
  setAutomationExecutor?: (executorName: ProductSettings['automationExecutor']) => void;
  selectLocalModel?: (modelId: string) => void;
};

const native = NativeModules.ADProductSettings as ProductSettingsModule | undefined;

export const defaultProductSettings: ProductSettings = {
  provider: 'Gemini',
  automationExecutor: 'Background / Tasker',
  taskerInstalled: false,
  assistantRoleAvailable: false,
  assistantRoleHeld: false,
  aiProfile: 'BALANCED',
  conversationEngine: 'AUTO',
  speechEngine: 'AUTO',
  visionEngine: 'AUTO',
  fileEngine: 'AUTO',
  groundingPolicy: 'AUTO',
  visibleFallbackPolicy: 'ASK',
  screenOffFirst: true,
  relayUrl: '',
  relayBackend: 'Gemini',
  relayConfigured: false,
  remoteEnabled: false,
  remoteUrl: '',
  remoteModel: '',
  language: 'SYSTEM',
  redactNames: true,
  transcriptStorage: false,
  cameraGranted: false,
  bluetoothGranted: false,
  microphoneGranted: false,
  automationGranted: false,
  localModels: [],
};

export const ADProductSettings = {
  async read(): Promise<ProductSettings> {
    if (!native?.getSettings) return defaultProductSettings;
    try {
      return await native.getSettings();
    } catch {
      return defaultProductSettings;
    }
  },
  async requestAssistantRole(): Promise<boolean> {
    if (!native?.requestAssistantRole) return false;
    try {
      return await native.requestAssistantRole();
    } catch {
      return false;
    }
  },
  setAiProfile(name: AIProfile) { native?.setAiProfile?.(name); },
  setConversationEngine(name: ConversationEngine) { native?.setConversationEngine?.(name); },
  setSpeechEngine(name: SpeechEngine) { native?.setSpeechEngine?.(name); },
  setVisionEngine(name: VisionEngine) { native?.setVisionEngine?.(name); },
  setFileEngine(name: FileEngine) { native?.setFileEngine?.(name); },
  setGroundingPolicy(name: GroundingPolicy) { native?.setGroundingPolicy?.(name); },
  setVisibleFallbackPolicy(name: VisibleFallbackPolicy) { native?.setVisibleFallbackPolicy?.(name); },
  async importLocalModel(): Promise<LocalModelItem | null> {
    if (!native?.importLocalModel) return null;
    return native.importLocalModel();
  },
  setLanguage(languageName: string) { native?.setLanguage?.(languageName); },
  setRedactNames(enabled: boolean) { native?.setRedactNames?.(enabled); },
  setTranscriptStorage(enabled: boolean) { native?.setTranscriptStorage?.(enabled); },
  setAutomationExecutor(executorName: ProductSettings['automationExecutor']) {
    native?.setAutomationExecutor?.(executorName);
  },
  selectLocalModel(modelId: string) { native?.selectLocalModel?.(modelId); },
};
