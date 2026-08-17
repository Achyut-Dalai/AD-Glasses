import {Platform} from 'react-native';

export const color = {
  ink: '#050506',
  graphite: '#151517',
  carbon: '#1F1F22',
  grey900: '#2C2C2E',
  grey700: '#636366',
  grey500: '#8E8E93',
  grey300: '#C7C7CC',
  grey200: '#DCDCE0',
  grey100: '#EFEFF1',
  canvas: '#F6F6F7',
  surface: '#FFFFFF',
  white: '#FFFFFF',
  success: '#248A3D',
  successSoft: '#EAF7ED',
  warning: '#A94D00',
  warningSoft: '#FFF3E8',
  error: '#D92D20',
  errorSoft: '#FFF0EF',
} as const;

export const space = {
  xxs: 4,
  xs: 8,
  sm: 12,
  md: 16,
  lg: 20,
  xl: 24,
  xxl: 32,
  hero: 40,
  poster: 52,
} as const;

export const radius = {
  icon: 11,
  control: 15,
  card: 20,
  hero: 28,
  pill: 999,
} as const;

export const type = {
  poster: {fontSize: 44, lineHeight: 46, fontWeight: '500' as const, letterSpacing: -1.25},
  display: {fontSize: 34, lineHeight: 39, fontWeight: '600' as const, letterSpacing: -0.8},
  title: {fontSize: 22, lineHeight: 28, fontWeight: '600' as const, letterSpacing: -0.25},
  section: {fontSize: 18, lineHeight: 24, fontWeight: '600' as const},
  cardTitle: {fontSize: 16, lineHeight: 22, fontWeight: '600' as const},
  body: {fontSize: 15, lineHeight: 22, fontWeight: '400' as const},
  meta: {fontSize: 13, lineHeight: 18, fontWeight: '500' as const},
  micro: {fontSize: 11, lineHeight: 14, fontWeight: '600' as const, letterSpacing: 0.2},
} as const;

export const shadow = Platform.select({
  ios: {
    shadowColor: '#000000',
    shadowOpacity: 0.06,
    shadowRadius: 18,
    shadowOffset: {width: 0, height: 8},
  },
  android: {elevation: 2},
  default: {},
});

export const motion = {
  instant: 90,
  fast: 160,
  normal: 280,
  deliberate: 420,
  hero: 620,
  spring: {damping: 18, stiffness: 230, mass: 0.8},
} as const;
