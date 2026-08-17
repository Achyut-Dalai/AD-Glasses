import React, {PropsWithChildren, useEffect, useMemo, useState} from 'react';
import {
  AccessibilityInfo,
  Image,
  Pressable,
  ScrollView,
  StyleProp,
  StyleSheet,
  Text,
  TextInput,
  View,
  ViewStyle,
} from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import {SafeAreaView} from 'react-native-safe-area-context';
import {Icon, IconName} from './icons';
import {color, motion, radius, shadow, space, type} from './tokens';
import type {Navigate, RootTab} from '../navigation/routes';

export function useReducedMotion() {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    AccessibilityInfo.isReduceMotionEnabled().then(setReduced).catch(() => undefined);
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => sub.remove();
  }, []);
  return reduced;
}

export function Screen({children, scroll = true, style}: PropsWithChildren<{scroll?: boolean; style?: StyleProp<ViewStyle>}>) {
  const content = <View style={[styles.screenContent, style]}>{children}</View>;
  return (
    <SafeAreaView edges={['top', 'left', 'right']} style={styles.safe}>
      {scroll ? <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>{content}</ScrollView> : content}
    </SafeAreaView>
  );
}

export function Reveal({children, delay = 0, distance = 10}: PropsWithChildren<{delay?: number; distance?: number}>) {
  const reduceMotion = useReducedMotion();
  const opacity = useSharedValue(reduceMotion ? 1 : 0);
  const y = useSharedValue(reduceMotion ? 0 : distance);
  useEffect(() => {
    if (reduceMotion) {
      opacity.value = 1;
      y.value = 0;
      return;
    }
    opacity.value = withTiming(1, {duration: motion.normal});
    y.value = withTiming(0, {duration: motion.deliberate});
  }, [delay, reduceMotion, opacity, y]);
  const animatedStyle = useAnimatedStyle(() => ({opacity: opacity.value, transform: [{translateY: y.value}]}));
  return <Animated.View style={animatedStyle}>{children}</Animated.View>;
}

export function PressableScale({children, onPress, style, accessibilityLabel}: PropsWithChildren<{onPress?: () => void; style?: StyleProp<ViewStyle>; accessibilityLabel?: string}>) {
  const reduceMotion = useReducedMotion();
  const scale = useSharedValue(1);
  const animatedStyle = useAnimatedStyle(() => ({transform: [{scale: scale.value}]}));
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      onPress={onPress}
      onPressIn={() => { scale.value = reduceMotion ? 1 : withSpring(0.982, motion.spring); }}
      onPressOut={() => { scale.value = reduceMotion ? 1 : withSpring(1, motion.spring); }}>
      <Animated.View style={[style, animatedStyle]}>{children}</Animated.View>
    </Pressable>
  );
}

export function GlassesImage({height = 156, dimmed = false}: {height?: number; dimmed?: boolean}) {
  return (
    <Image
      source={{uri: 'ad_glasses_hero_v4'}}
      accessibilityLabel="Smart glasses"
      resizeMode="contain"
      style={{width: '100%', height, opacity: dimmed ? 0.64 : 1}}
    />
  );
}

export function TopBar({title, onBack, action}: {title: string; onBack?: () => void; action?: React.ReactNode}) {
  return (
    <View style={styles.topBar}>
      {onBack ? (
        <PressableScale onPress={onBack} accessibilityLabel="Back" style={styles.iconButton}>
          <Icon name="back" size={20}/>
        </PressableScale>
      ) : <View style={styles.iconButton}/>} 
      <Text numberOfLines={1} style={styles.topBarTitle}>{title}</Text>
      <View style={styles.topBarAction}>{action}</View>
    </View>
  );
}

export function HeroTitle({children}: PropsWithChildren) {
  return <Text style={styles.heroTitle}>{children}</Text>;
}

export function SectionTitle({children, caption}: PropsWithChildren<{caption?: string}>) {
  return (
    <View style={styles.sectionHeading}>
      <Text style={styles.sectionTitle}>{children}</Text>
      {caption ? <Text style={styles.sectionCaption}>{caption}</Text> : null}
    </View>
  );
}

export function Card({children, onPress, style}: PropsWithChildren<{onPress?: () => void; style?: StyleProp<ViewStyle>}>) {
  const body = <View style={[styles.card, style]}>{children}</View>;
  return onPress ? <PressableScale onPress={onPress}>{body}</PressableScale> : body;
}

export function IconTile({name, dark = false}: {name: IconName; dark?: boolean}) {
  return (
    <View style={[styles.iconTile, dark && styles.iconTileDark]}>
      <Icon name={name} size={20} stroke={dark ? color.white : color.ink}/>
    </View>
  );
}

export function PrimaryButton({label, onPress, secondary = false, icon}: {label: string; onPress?: () => void; secondary?: boolean; icon?: IconName}) {
  return (
    <PressableScale onPress={onPress} accessibilityLabel={label}>
      <View style={[styles.button, secondary && styles.buttonSecondary]}>
        {icon ? <Icon name={icon} size={19} stroke={secondary ? color.ink : color.white}/> : null}
        <Text style={[styles.buttonText, secondary && styles.buttonSecondaryText]}>{label}</Text>
      </View>
    </PressableScale>
  );
}

export function StatusPill({label, tone = 'neutral'}: {label: string; tone?: 'neutral' | 'success' | 'warning' | 'error'}) {
  const palette = useMemo(() => {
    if (tone === 'success') return [color.successSoft, color.success] as const;
    if (tone === 'warning') return [color.warningSoft, color.warning] as const;
    if (tone === 'error') return [color.errorSoft, color.error] as const;
    return [color.grey100, color.grey700] as const;
  }, [tone]);
  return <View style={[styles.pill, {backgroundColor: palette[0]}]}><Text style={[styles.pillText, {color: palette[1]}]}>{label}</Text></View>;
}

export function ListRow({icon, title, detail, onPress, value, selected}: {icon: IconName; title: string; detail?: string; onPress?: () => void; value?: string; selected?: boolean}) {
  return (
    <PressableScale onPress={onPress} accessibilityLabel={title}>
      <View style={styles.listRow}>
        <IconTile name={icon}/>
        <View style={styles.listCopy}>
          <Text style={styles.listTitle}>{title}</Text>
          {detail ? <Text style={styles.listDetail}>{detail}</Text> : null}
        </View>
        {value ? <Text style={styles.listValue}>{value}</Text> : selected ? <Icon name="check" size={20}/> : onPress ? <Icon name="chevron" size={19} stroke={color.grey500}/> : null}
      </View>
    </PressableScale>
  );
}

export function Divider({inset = 52}: {inset?: number}) {
  return <View style={[styles.divider, {marginLeft: inset}]}/>;
}

export function Field({value, onChangeText, placeholder, secureTextEntry}: {value: string; onChangeText: (value: string) => void; placeholder: string; secureTextEntry?: boolean}) {
  return (
    <TextInput
      value={value}
      onChangeText={onChangeText}
      placeholder={placeholder}
      placeholderTextColor={color.grey500}
      secureTextEntry={secureTextEntry}
      style={styles.field}
      selectionColor={color.ink}
    />
  );
}

export function BottomBar({selected, navigate}: {selected: RootTab; navigate: Navigate}) {
  const items: {route: RootTab; label: string; icon: IconName}[] = [
    {route: 'home', label: 'Home', icon: 'home'},
    {route: 'prompt', label: 'Prompt', icon: 'terminal'},
    {route: 'ai', label: 'AI', icon: 'spark'},
    {route: 'library', label: 'Library', icon: 'library'},
  ];
  return (
    <SafeAreaView edges={['bottom']} style={styles.bottomSafe}>
      <View style={styles.bottomBar}>
        {items.map(item => {
          const active = item.route === selected;
          return (
            <Pressable key={item.route} onPress={() => navigate(item.route)} style={styles.tab} accessibilityRole="tab" accessibilityState={{selected: active}}>
              <Icon name={item.icon} size={21} stroke={active ? color.ink : color.grey500}/>
              <Text style={[styles.tabLabel, active && styles.tabLabelActive]}>{item.label}</Text>
            </Pressable>
          );
        })}
      </View>
    </SafeAreaView>
  );
}

export const styles = StyleSheet.create({
  safe: {flex: 1, backgroundColor: color.canvas},
  scroll: {flexGrow: 1},
  screenContent: {paddingHorizontal: space.md, paddingBottom: space.hero, gap: space.xl},
  topBar: {height: 58, flexDirection: 'row', alignItems: 'center', marginHorizontal: -4},
  iconButton: {width: 42, height: 42, alignItems: 'center', justifyContent: 'center'},
  topBarTitle: {...type.section, flex: 1, textAlign: 'center', color: color.ink},
  topBarAction: {width: 42, alignItems: 'flex-end'},
  heroTitle: {...type.display, color: color.ink},
  sectionHeading: {gap: 3},
  sectionTitle: {...type.section, color: color.ink},
  sectionCaption: {...type.meta, color: color.grey700},
  card: {backgroundColor: color.surface, borderRadius: radius.card, padding: space.md, borderWidth: StyleSheet.hairlineWidth, borderColor: color.grey200, ...shadow},
  iconTile: {width: 40, height: 40, borderRadius: radius.icon, backgroundColor: color.grey100, alignItems: 'center', justifyContent: 'center'},
  iconTileDark: {backgroundColor: color.ink},
  button: {height: 54, borderRadius: radius.control, backgroundColor: color.ink, flexDirection: 'row', gap: space.xs, alignItems: 'center', justifyContent: 'center', paddingHorizontal: space.md},
  buttonSecondary: {backgroundColor: color.surface, borderWidth: 1, borderColor: color.grey300},
  buttonText: {...type.cardTitle, color: color.white},
  buttonSecondaryText: {color: color.ink},
  pill: {borderRadius: radius.pill, paddingHorizontal: 10, paddingVertical: 6},
  pillText: {...type.micro},
  listRow: {minHeight: 66, flexDirection: 'row', alignItems: 'center', gap: space.sm, paddingVertical: 10},
  listCopy: {flex: 1, gap: 2},
  listTitle: {...type.cardTitle, color: color.ink},
  listDetail: {...type.meta, color: color.grey700},
  listValue: {...type.meta, color: color.grey700},
  divider: {height: StyleSheet.hairlineWidth, backgroundColor: color.grey200},
  field: {minHeight: 50, borderRadius: radius.control, backgroundColor: color.grey100, paddingHorizontal: 14, color: color.ink, ...type.body},
  bottomSafe: {backgroundColor: color.surface},
  bottomBar: {height: 62, flexDirection: 'row', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: color.grey200, backgroundColor: color.surface},
  tab: {flex: 1, alignItems: 'center', justifyContent: 'center', gap: 3},
  tabLabel: {...type.micro, color: color.grey500},
  tabLabelActive: {color: color.ink},
});
