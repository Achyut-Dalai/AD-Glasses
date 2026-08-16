# AD Glasses prototype test and integration plan

## Goal

Approve how the app feels before real hardware and backend work can create risk or slow every UI change.

The prototype is a real installable Compose app with fake implementations. We keep useful UI, navigation and state code. We replace fake repositories gradually after acceptance.

## Gate 1 — Generated-project health

Before design review:

- Gradle debug build succeeds;
- app launches without a crash;
- every bottom destination and focused route opens;
- Back, bottom navigation, sheets, dialogs and keyboard behave correctly;
- rotation/process recreation does not corrupt important prototype state where supported;
- no real hardware/network/firmware permissions or side effects exist;
- prototype controls can reset seed data.

Reject the delivery if it is a web view, a React app wrapped as Android, static screenshots, or one giant Compose file.

## Gate 2 — Phone experience review

Install the APK on the emulator and a physical Android phone. Use it for several sessions rather than reviewing screenshots only.

Check:

- Home answers connection/activity/next-action questions within a few seconds;
- all four quick actions are understandable;
- the same Activity Banner remains understandable while moving between tabs;
- Assistant input is faster to reach than browsing conversations;
- Library content can be found without knowing its media type first;
- all eight automations feel related, not like eight different mini-apps;
- Settings and Advanced do not crowd daily use;
- empty, offline, permission-denied and failure screens provide a next action;
- the light design remains comfortable indoors and outdoors;
- text, icons and touch areas feel right on the real phone.

Record requested changes by route and state, for example `Home / connected / Device Stage too tall`. Do not request an entirely new design language for a single state.

## Gate 3 — Nine journey walkthroughs

Complete each using Prototype controls to exercise success and failure:

1. first connection;
2. ask what the glasses see;
3. current-information search with citations;
4. media Sync complete and partial;
5. meeting recording to summary;
6. live translation to saved transcript;
7. owner-cloud setup and provider failure;
8. firmware blocked, partial and complete simulations;
9. confirmed phone action, edit/cancel and failed result.

No route may strand the user or require restarting the app to recover.

## Gate 4 — State and accessibility sweep

From Prototype controls test:

- every device family and limited-capability presentation;
- connecting, reconnecting, disconnected and stale metrics;
- Library empty and long content;
- provider offline/auth/model errors;
- automation incompatible and permission-lost;
- 200% font scale and long translated copy;
- TalkBack traversal and button labels;
- reduced motion;
- keyboard open on Assistant and forms;
- destructive confirmation and safe cancellation.

## Acceptance output

When the prototype is accepted, preserve:

- design tokens;
- reusable Compose components;
- navigation routes;
- UI state/data models that do not depend on fixture or vendor types;
- ViewModel reducers and validation logic;
- accessibility semantics;
- UI and state-transition tests.

Do not preserve generated fake networking, fake hardware protocol logic, fixture-only shortcuts in production code, or any guessed SDK behavior.

## Bringing the project back

Export the entire AI Studio Android project as a ZIP. Do not copy individual screenshots or isolated Compose files manually.

Place the untouched export in a temporary import branch or worktree. We will:

1. build it locally as delivered;
2. inventory its modules, dependencies, routes and assets;
3. compare package/minimum SDK/toolchain with the existing Android project;
4. move the accepted design system and UI layer behind interfaces;
5. keep upstream protocol code as the source of hardware truth;
6. integrate vertical slices with tests and emulator/physical-device checks.

## Real integration order

1. **Shell and fixtures:** theme, components, routes, state models and prototype controls.
2. **Connection slice:** real scan/bind/reconnect/status behind `DeviceRepository`; test HeyCyan physically.
3. **Basic device slice:** battery, storage, photo/video/audio commands and Device Center capability gating.
4. **Sync and Library:** proven BLE trigger plus local Wi-Fi HTTP transfer, safe import ledger and real content.
5. **Assistant:** local/owner-cloud routing, voice/image/live, grounding and saved answers.
6. **Automations:** one automation at a time, starting with low-risk meeting/caption flows; Local Agent receives its separate approval/audio safety work.
7. **Additional devices:** experimental adapters remain runtime-gated until physically validated.
8. **Firmware last:** only after separate artifact, preflight, persistence, recovery and physical-device safety gates.

Each slice replaces one fake repository without rewriting the screen. If a generated interface does not fit the proven implementation, adapt the interface—never rewrite a working protocol merely to match generated code.

## Release boundary

The prototype APK is for private UI testing. It is not a production release and must not be used to flash glasses, automate the phone, or store real credentials. A feature becomes real only after its repository adapter, failure states, permissions, tests and physical validation are complete.
