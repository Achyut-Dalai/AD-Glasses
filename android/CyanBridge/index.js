import React from 'react';
import {AppRegistry} from 'react-native';
import App from './src/App';

AppRegistry.registerComponent('ADGlasses', () => App);
AppRegistry.registerComponent('ADGlassesWelcome', () => () =>
  React.createElement(App, {initialRoute: 'welcome'}),
);
