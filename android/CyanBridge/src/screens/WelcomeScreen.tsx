import React from 'react';
import {StyleSheet, Text, View} from 'react-native';
import {GlassesImage, PrimaryButton, Reveal, Screen} from '../design/components';
import {color, radius, space, type} from '../design/tokens';
import {ADNative} from '../native/ADNative';
import type {Navigate} from '../navigation/routes';

export function WelcomeScreen({navigate}: {navigate: Navigate}) {
  const finish = (connect: boolean) => {
    ADNative.action('completeOnboarding', {connect});
    if (connect) navigate('pairing'); else navigate('home');
  };
  return (
    <Screen scroll={false} style={styles.root}>
      <Reveal>
        <View style={styles.statement}>
          <Text style={styles.poster}>YOUR GLASSES</Text>
          <Text style={styles.poster}>YOUR AI</Text>
          <Text style={styles.poster}>YOUR DATA</Text>
        </View>
      </Reveal>
      <Reveal delay={80}>
        <View style={styles.glassesStage}>
          <GlassesImage height={220}/>
        </View>
      </Reveal>
      <View style={styles.actions}>
        <PrimaryButton label="Connect glasses" onPress={() => finish(true)}/>
        <PrimaryButton label="Continue without glasses" secondary onPress={() => finish(false)}/>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, paddingTop: 34, justifyContent: 'space-between', paddingBottom: space.lg},
  statement: {paddingTop: space.xs},
  poster: {...type.poster, color: color.ink},
  glassesStage: {height: 270, borderRadius: radius.hero, backgroundColor: color.grey100, borderWidth: 1, borderColor: color.grey200, paddingHorizontal: 8, justifyContent: 'center', overflow: 'hidden'},
  actions: {gap: 10},
});
