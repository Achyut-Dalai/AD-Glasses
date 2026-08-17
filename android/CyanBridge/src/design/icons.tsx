import React from 'react';
import Svg, {Circle, Line, Path, Polyline, Rect} from 'react-native-svg';
import {color} from './tokens';

export type IconName =
  | 'home' | 'terminal' | 'spark' | 'library' | 'settings' | 'back' | 'chevron'
  | 'glasses' | 'mic' | 'camera' | 'video' | 'translate' | 'eye' | 'web'
  | 'bolt' | 'wave' | 'timeline' | 'book' | 'repeat' | 'cloud' | 'computer'
  | 'lock' | 'storage' | 'language' | 'shield' | 'info' | 'sync' | 'firmware'
  | 'play' | 'note' | 'image' | 'plus' | 'send' | 'check';

export function Icon({name, size = 22, stroke = color.ink}: {name: IconName; size?: number; stroke?: string}) {
  const common = {stroke, strokeWidth: 1.8, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, fill: 'none'};
  const body = (() => {
    switch (name) {
      case 'home': return <><Path d="M4 11 12 4l8 7" {...common}/><Path d="M6.5 10.5V20h11v-9.5" {...common}/></>;
      case 'terminal': return <><Polyline points="5,7 10,12 5,17" {...common}/><Line x1="12" y1="17" x2="19" y2="17" {...common}/></>;
      case 'spark': return <><Path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3Z" {...common}/><Path d="M18.5 15.5 19.3 18l2.2.8-2.2.8-.8 2.4-.8-2.4-2.2-.8 2.2-.8.8-2.5Z" {...common}/></>;
      case 'library': return <><Rect x="4" y="5" width="5" height="14" rx="1" {...common}/><Rect x="10.5" y="5" width="4" height="14" rx="1" {...common}/><Path d="m16.5 6 3-1 3.5 12-3 1-3.5-12Z" {...common}/></>;
      case 'settings': return <><Circle cx="12" cy="12" r="3" {...common}/><Path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M18.4 5.6 17 7M7 17l-1.4 1.4" {...common}/></>;
      case 'back': return <><Line x1="19" y1="12" x2="5" y2="12" {...common}/><Polyline points="11,6 5,12 11,18" {...common}/></>;
      case 'chevron': return <Polyline points="9,5 16,12 9,19" {...common}/>;
      case 'glasses': return <><Path d="M3 11h4l1 4h4l1-4h4l1 4h3" {...common}/><Path d="M3 11 5 8h4l1 3M21 11l-2-3h-4l-1 3" {...common}/></>;
      case 'mic': return <><Rect x="9" y="3" width="6" height="11" rx="3" {...common}/><Path d="M6 11a6 6 0 0 0 12 0M12 17v4" {...common}/></>;
      case 'camera': return <><Rect x="3" y="7" width="18" height="12" rx="3" {...common}/><Path d="m8 7 1.5-3h5L16 7" {...common}/><Circle cx="12" cy="13" r="3" {...common}/></>;
      case 'video': return <><Rect x="3" y="6" width="13" height="12" rx="2" {...common}/><Path d="m16 10 5-3v10l-5-3" {...common}/></>;
      case 'translate': return <><Path d="M4 5h8M8 3v2c0 4-2 7-5 9M5 9c1.5 2 3.5 3.5 6 5" {...common}/><Path d="m14 19 3-8 3 8M15 16h4" {...common}/></>;
      case 'eye': return <><Path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" {...common}/><Circle cx="12" cy="12" r="2.7" {...common}/></>;
      case 'web': return <><Circle cx="12" cy="12" r="9" {...common}/><Path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18" {...common}/></>;
      case 'bolt': return <Path d="M13.5 2 6 13h5l-.5 9L18 10h-5l.5-8Z" {...common}/>;
      case 'wave': return <Path d="M3 12h2l1.5-5L9 17l2-10 2 10 2-10 1.5 5H21" {...common}/>;
      case 'timeline': return <><Line x1="5" y1="5" x2="5" y2="19" {...common}/><Circle cx="5" cy="8" r="2" {...common}/><Circle cx="5" cy="16" r="2" {...common}/><Path d="M9 8h10M9 16h7" {...common}/></>;
      case 'book': return <><Path d="M4 5h6a2 2 0 0 1 2 2v13a3 3 0 0 0-3-3H4V5Z" {...common}/><Path d="M20 5h-6a2 2 0 0 0-2 2v13a3 3 0 0 1 3-3h5V5Z" {...common}/></>;
      case 'repeat': return <><Path d="M7 7h10l-2.5-2.5M17 17H7l2.5 2.5" {...common}/><Path d="M18 7a5 5 0 0 1 2 4M6 17a5 5 0 0 1-2-4" {...common}/></>;
      case 'cloud': return <Path d="M6 18h12a4 4 0 0 0 .8-7.9A7 7 0 0 0 5.4 9 4.5 4.5 0 0 0 6 18Z" {...common}/>;
      case 'computer': return <><Rect x="3" y="4" width="18" height="13" rx="2" {...common}/><Path d="M8 21h8M12 17v4" {...common}/></>;
      case 'lock': return <><Rect x="5" y="10" width="14" height="11" rx="2" {...common}/><Path d="M8 10V7a4 4 0 0 1 8 0v3" {...common}/></>;
      case 'storage': return <><EllipseLike/><Path d="M4 7v10c0 2 16 2 16 0V7" {...common}/></>;
      case 'language': return <><Circle cx="12" cy="12" r="9" {...common}/><Path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18" {...common}/></>;
      case 'shield': return <Path d="M12 3 5 6v5c0 5 3 8 7 10 4-2 7-5 7-10V6l-7-3Z" {...common}/>;
      case 'info': return <><Circle cx="12" cy="12" r="9" {...common}/><Line x1="12" y1="11" x2="12" y2="17" {...common}/><Circle cx="12" cy="7.5" r=".7" fill={stroke}/></>;
      case 'sync': return <><Path d="M19 7a8 8 0 0 0-13-2L4 7" {...common}/><Polyline points="4,3 4,7 8,7" {...common}/><Path d="M5 17a8 8 0 0 0 13 2l2-2" {...common}/><Polyline points="20,21 20,17 16,17" {...common}/></>;
      case 'firmware': return <><Rect x="6" y="6" width="12" height="12" rx="2" {...common}/><Path d="M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M18 9h4M2 15h4M18 15h4" {...common}/></>;
      case 'play': return <Path d="m8 5 11 7-11 7V5Z" {...common}/>;
      case 'note': return <><Path d="M5 3h10l4 4v14H5V3Z" {...common}/><Path d="M15 3v5h4M8 12h8M8 16h6" {...common}/></>;
      case 'image': return <><Rect x="3" y="4" width="18" height="16" rx="2" {...common}/><Circle cx="9" cy="9" r="2" {...common}/><Path d="m5 18 5-5 3 3 2-2 4 4" {...common}/></>;
      case 'plus': return <><Line x1="12" y1="5" x2="12" y2="19" {...common}/><Line x1="5" y1="12" x2="19" y2="12" {...common}/></>;
      case 'send': return <Path d="m3 11 18-8-8 18-2-7-8-3Z" {...common}/>;
      case 'check': return <Polyline points="5,12 10,17 19,7" {...common}/>;
    }
  })();
  return <Svg width={size} height={size} viewBox="0 0 24 24">{body}</Svg>;
}

function EllipseLike() {
  return <Path d="M4 7c0-2 16-2 16 0s-16 2-16 0Z" stroke={color.ink} strokeWidth={1.8} fill="none"/>;
}
