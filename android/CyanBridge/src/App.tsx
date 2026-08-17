import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {BackHandler, StatusBar, StyleSheet, View} from 'react-native';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {BottomBar} from './design/components';
import {color} from './design/tokens';
import {ADNative} from './native/ADNative';
import {AIScreen, HomeScreen, LibraryScreen, PromptScreen} from './screens/MainScreens';
import {WelcomeScreen} from './screens/WelcomeScreen';
import {PairingScreen} from './screens/PairingScreen';
import {
  AboutScreen,
  AdvancedScreen,
  AssistantAppsScreen,
  CapabilityScreen,
  CapturesScreen,
  DeviceScreen,
  FirmwareScreen,
  NotesScreen,
  RecordingsScreen,
  SyncScreen,
} from './screens/DetailScreens';
import {
  LanguageScreen,
  LocalAIScreen,
  PermissionsScreen,
  PrivacyScreen,
  RelayScreen,
  SettingsScreen,
  StorageScreen,
} from './screens/SettingsScreens';
import type {Navigate, RootTab, RouteEntry, RouteName} from './navigation/routes';
import {rootTabs} from './navigation/routes';

type Props = {initialRoute?: RouteName};

export default function App({initialRoute = 'home'}: Props) {
  const [stack, setStack] = useState<RouteEntry[]>([{name: initialRoute}]);
  const current = stack[stack.length - 1] ?? {name: 'home' as RouteName};

  const navigate: Navigate = useCallback((name, params) => {
    setStack(history => {
      if (rootTabs.includes(name as RootTab)) return [{name, params}];
      return [...history, {name, params}];
    });
  }, []);

  const back = useCallback(() => {
    setStack(history => history.length > 1 ? history.slice(0, -1) : history);
  }, []);

  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (stack.length <= 1) {
        ADNative.action('exitApp');
        return true;
      }
      back();
      return true;
    });
    return () => sub.remove();
  }, [back, stack.length]);

  const tab = useMemo(
    () => rootTabs.includes(current.name as RootTab) ? current.name as RootTab : undefined,
    [current.name],
  );

  return (
    <SafeAreaProvider>
      <StatusBar barStyle="dark-content" backgroundColor={color.canvas}/>
      <View style={styles.app} key={`${current.name}-${stack.length}`}>
        <RouteView route={current} navigate={navigate} back={back}/>
      </View>
      {tab ? <BottomBar selected={tab} navigate={navigate}/> : null}
    </SafeAreaProvider>
  );
}

function RouteView({route, navigate, back}: {route: RouteEntry; navigate: Navigate; back: () => void}) {
  switch (route.name) {
    case 'welcome': return <WelcomeScreen navigate={navigate}/>;
    case 'home': return <HomeScreen navigate={navigate}/>;
    case 'prompt': return <PromptScreen route={route}/>;
    case 'ai': return <AIScreen navigate={navigate}/>;
    case 'library': return <LibraryScreen navigate={navigate}/>;
    case 'settings': return <SettingsScreen navigate={navigate} back={back}/>;
    case 'device': return <DeviceScreen navigate={navigate} back={back}/>;
    case 'pairing': return <PairingScreen back={back}/>;
    case 'sync': return <SyncScreen back={back}/>;
    case 'relay': return <RelayScreen back={back}/>;
    case 'local-ai': return <LocalAIScreen back={back}/>;
    case 'assistant-apps': return <AssistantAppsScreen back={back}/>;
    case 'privacy': return <PrivacyScreen back={back}/>;
    case 'storage': return <StorageScreen back={back}/>;
    case 'language': return <LanguageScreen back={back}/>;
    case 'permissions': return <PermissionsScreen back={back}/>;
    case 'advanced': return <AdvancedScreen navigate={navigate} back={back}/>;
    case 'about': return <AboutScreen back={back}/>;
    case 'firmware': return <FirmwareScreen back={back}/>;
    case 'capability': return <CapabilityScreen route={route} back={back}/>;
    case 'captures': return <CapturesScreen back={back}/>;
    case 'recordings': return <RecordingsScreen back={back}/>;
    case 'notes': return <NotesScreen back={back}/>;
    default: return null;
  }
}

const styles = StyleSheet.create({app: {flex: 1, backgroundColor: color.canvas}});
