import React from 'react';
import {requireNativeComponent, StyleProp, ViewStyle} from 'react-native';

type Props = {
  uri: string;
  autoPlay?: boolean;
  style?: StyleProp<ViewStyle>;
};

const NativeVideo = requireNativeComponent<Props>('ADVideoView');

export function ADVideoView({uri, autoPlay = false, style}: Props) {
  return <NativeVideo uri={uri} autoPlay={autoPlay} style={style}/>;
}
