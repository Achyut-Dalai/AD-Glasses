export type RootTab = 'home' | 'prompt' | 'ai' | 'library';

export type RouteName =
  | RootTab
  | 'welcome'
  | 'pairing'
  | 'settings'
  | 'device'
  | 'sync'
  | 'relay'
  | 'local-ai'
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
  | 'recordings'
  | 'notes';

export type RouteEntry = {
  name: RouteName;
  params?: Record<string, string | number | boolean | undefined>;
};

export type Navigate = (name: RouteName, params?: RouteEntry['params']) => void;

export const rootTabs: RootTab[] = ['home', 'prompt', 'ai', 'library'];
