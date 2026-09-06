# AD Glasses interaction and motion specification

Motion communicates state, ownership and continuity. It is never decoration competing with the glasses or AI outcome.

## Timing tokens

| Token | Duration | Use |
|---|---:|---|
| `motion-instant` | 100 ms | Press feedback, icon/state swap |
| `motion-fast` | 180 ms | Chip, toggle, small inline disclosure |
| `motion-standard` | 280 ms | Route content, cards, Activity Banner |
| `motion-emphasis` | 420 ms | Product Stage state change, focused sheet |
| `motion-progress` | measured | Transfer, recording and firmware progress only |

Standard easing is `cubic-bezier(0.2, 0.8, 0.2, 1)`. Exit uses a slightly faster 180–220 ms version. Avoid bounce, spring overshoot and perpetual floating.

## Navigation

- Bottom-destination switch: crossfade plus at most 8 dp vertical settle over 220 ms. Preserve the destination’s scroll position.
- Push to focused detail: content enters from 12 dp toward the navigation direction with a fade over 280 ms.
- Back: exact inverse of the focused-detail transition.
- Modal sheet: backdrop fades over 180 ms; sheet rises from 32 dp over 320 ms. Drag dismissal is allowed only when the action is safely cancellable.
- Dialog: scale 0.98 → 1 and fade over 180 ms. No dialog bounce.
- Shared product imagery may visually persist between Home and Device Center, but do not block navigation on a complex shared-element implementation.

## Device Stage

- First appearance: product opacity 0 → 1, translate Y 10 dp → 0 and scale 0.97 → 1 over 420–520 ms.
- Connection state changes update status text and semantic announcement first; halo color/opacity follows over 280 ms.
- Connecting/reconnecting may use a slow 1.8-second status-dot pulse. The glasses themselves do not bob, rotate or shimmer.
- Battery/storage numbers animate only when a fresh measured value changes. Stale or unknown state crossfades to its label instead of counting.

## Global Activity Banner

- Enters from 10 dp below with fade over 280–340 ms.
- Persists at the same location and component identity across bottom destinations.
- Progress changes smoothly to actual or simulated measured progress; indeterminate activity uses a restrained single-line indicator.
- The status dot may pulse every two seconds. Recording uses a red semantic state; other active work uses blue.
- When the highest-priority activity changes, content crossfades in place. Do not stack animated banners.

Priority: firmware flashing → recording → safety-critical approval/recovery → Sync → live translation/captions → other automation activity.

## Setup and connection

- Readiness step changes fill the step rail over 220 ms.
- Scanning uses two low-contrast expanding rings with a 2.2-second cycle. There is no radar sweep or fake signal triangulation.
- A discovered row inserts with fade/size over 240 ms and an accessibility announcement.
- Confirmation uses the standard sheet.
- Preparing → Connecting → Reading capabilities advances a real/fixture state timeline. Never use random stage order.
- Success transforms to Home only after the fixture repository emits Connected.

## Assistant

- Composer send gives immediate press feedback and inserts the user message within 180 ms.
- Streaming answer reveals text naturally without a typing-dot delay; Stop replaces Send while streaming.
- Web grounding sources appear with the answer, not as a separate dramatic reveal.
- Live listening uses a modest waveform driven by real/fixture audio level. Speaking swaps the semantic label and waveform state.
- Reconnect freezes the waveform, keeps the transcript, and exposes End immediately.

## Recording, translation and captions

- Timer increments without scale animation.
- Waveforms are supporting feedback, not decorative full-screen spectacle.
- Pause/mute switches label and icon within 180 ms and announces the new state.
- Stop uses an explicit button and transitions to processing/result; it never disappears behind a gesture.

## Sync and firmware

- Progress bar movement follows actual/fixture progress and is clamped monotonically within a stage.
- Timeline completion uses icon/label/color together. Color is never the only indication.
- Sync cancellation remains reachable throughout cancellable stages.
- Firmware cancellation is hidden or disabled during non-cancellable flashing. The reason is visible.
- Partial success does not replay success animation. It enters as a stable recovery state.
- Complete may use one 420 ms check transition; no confetti.

## Lists, filters and selection

- Filters update content with a 180 ms crossfade.
- Multi-select changes the top action row in place and keeps selected items stable.
- Delete confirmation uses a dialog. The list removes an item only after the fake/real repository confirms deletion.
- Empty-state transitions preserve the title, tabs and filters so the user retains orientation.

## Controls and validation

- Toggle thumb travels over 180 ms and is accompanied by label/status change.
- Form errors appear inline below the field and move focus to the first invalid field on submit.
- Save and test changes to Testing with progress, then Ready or a specific failure. It never reports success speculatively.
- Consequential phone actions always use the approval sheet; outcome screens are driven by observed result state.

## Reduced motion

When Android reduced-motion/animation-scale settings or the prototype fixture request reduced motion:

- remove translation, scale, scanning rings, halo breathing and pulsing;
- retain short 100–150 ms crossfades where needed for continuity;
- keep measured progress bars and timers because they convey state;
- never rely on motion to reveal an action or explain completion.

## Accessibility announcements

Announce, without repeated noise:

- connected/disconnected/reconnecting;
- scan result count changes;
- recording started, paused and stopped;
- live listening/speaking/reconnecting;
- Sync stage and completion/partial result;
- provider test result;
- approval requested and observed phone-action outcome;
- firmware stage, non-cancellable boundary, partial result and completion.
