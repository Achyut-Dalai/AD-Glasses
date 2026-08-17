import React, {PropsWithChildren, useEffect} from 'react';
import {StyleSheet} from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import {useReducedMotion} from '../design/components';
import {motion} from '../design/tokens';

/**
 * Navigation motion is intentionally quiet. Product-specific motion belongs inside
 * the screen; route changes only establish continuity and hierarchy.
 */
export function ScreenTransition({children}: PropsWithChildren) {
  const reduced = useReducedMotion();
  const opacity = useSharedValue(reduced ? 1 : 0);
  const y = useSharedValue(reduced ? 0 : 6);

  useEffect(() => {
    if (reduced) {
      opacity.value = 1;
      y.value = 0;
      return;
    }
    opacity.value = withTiming(1, {duration: motion.normal});
    y.value = withTiming(0, {duration: motion.normal});
  }, [opacity, reduced, y]);

  const style = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{translateY: y.value}],
  }));

  return <Animated.View style={[styles.fill, style]}>{children}</Animated.View>;
}

const styles = StyleSheet.create({fill: {flex: 1}});
