export type RootTab = 'home' | 'ai' | 'library';

export type RouteName =
  | RootTab
  | 'prompt'
  | 'welcome'
  | 'pairing'
  | 'settings'
  | 'device'
  | 'sync'
  | 'relay'
  | 'local-ai'
  | 'ai-runtime'
  | 'assistant-apps'
  | 'privacy'
  | 'storage'
  | 'language'
  | 'permissions'
  | 'advanced'
  | 'about'
  | 'firmware'
  | 'capability'
  | 'captures'
  | 'capture-detail'
  | 'recordings'
  | 'recording-detail'
  | 'notes'
  | 'note-detail';

export type RouteEntry = {
  name: RouteName;
  params?: Record<string, string | number | boolean | undefined>;
};

export type Navigate = (name: RouteName, params?: RouteEntry['params']) => void;

/**
 * The phone is a control plane and memory viewer for the glasses, not a chat app.
 * Prompt remains a hidden compatibility route for contextual/internal sessions.
 */
export const rootTabs: RootTab[] = ['home', 'ai', 'library'];
