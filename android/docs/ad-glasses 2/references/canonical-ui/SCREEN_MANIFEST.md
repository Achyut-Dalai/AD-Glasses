# Canonical screen manifest

Status: approved visual input for the AD Glasses prototype

Every image in `screens/` is a state of a reusable screen family, not a demand for a separate Android Activity or an independent design system.

## Global shell

- Everyday bottom navigation is exactly Home, Assistant, Library and Automations.
- Welcome, setup, conversations, content detail, active sessions, Device Center, Sync, Settings, AI, privacy, firmware, diagnostics and approvals are focused routes.
- Focused routes use Back, a concise title and only a relevant trailing action.
- The compact mark and wordmark appear on everyday destinations. Do not add a profile avatar, account entry, fifth tab or giant repeated product title.

## References by family

| Family | Route / file | State and purpose |
|---|---|---|
| Onboarding | `welcome.png` | Product promise, transparent hero and the two first-run choices |
| Onboarding | `readiness.png` | Just-in-time initial permission rationale |
| Onboarding | `permission-denied.png` | Denied permission with Android-settings recovery |
| Devices | `brands.png` | Brand/family chooser with maturity labels |
| Devices | `scan.png` | Active scan, safe results and unknown-device fallback |
| Devices | `confirm.png` | Device confirmation sheet and capability preview |
| Devices | `connect-progress.png` | Preparing → Connecting → Reading capabilities |
| Home | `home.png` | Connected idle canonical Home |
| Home | `home-connecting.png` | Connection progress with phone-only actions preserved |
| Home | `home-active.png` | Global live-translation Activity Banner |
| Home | `home-disconnected.png` | Recoverable disconnect without losing saved content |
| Assistant | `assistant.png` | Composer-led hub and concise modality entries |
| Assistant | `assistant-offline.png` | Cloud failure with on-device fallback |
| Assistant | `conversation.png` | Multimodal question, captured source and answer actions |
| Assistant | `grounded.png` | Web-grounded answer, sources and disclosure boundary |
| Assistant | `assistant-live.png` | Focused listening session |
| Phone action | `approval.png` | Exact proposal, data, impact and explicit confirmation |
| Phone action | `action-result.png` | Honest partial result after Android cannot confirm completion |
| Library | `library.png` | Timeline, filters, Sync banner and mixed content |
| Library | `library-empty.png` | Useful empty state with Capture, Sync and Create note |
| Library | `content.png` | Reusable content-detail composition shown for a photo |
| Automations | `automations.png` | All eight built-ins, pause control and readiness |
| Automations | `automation-detail.png` | Meeting/recording detail archetype |
| Automations | `local-agent-detail.png` | Agent readiness, privacy and approval archetype |
| Automations | `translator-detail.png` | Language/input/output configuration archetype |
| Automations | `capture-detail.png` | Explicit interval/source/retention capture archetype |
| Automations | `meeting-active.png` | Active recording and live transcript |
| Automations | `automation-live.png` | Active translation with source and translated text |
| Community | `community.png` | Configured-service/local-package browse composition |
| Device Center | `device.png` | Primary HeyCyan-compatible capability-led controls |
| Device Center | `device-limited.png` | Generic audio limited capability presentation |
| Sync | `sync.png` | Five-stage local transfer with measured progress |
| Sync | `sync-result.png` | Partial success preserving already imported files |
| Settings | `settings.png` | Short configuration index, not a fifth tab |
| AI Services | `ai-services.png` | Provider health, routing defaults and owner-cloud form |
| Privacy | `privacy.png` | Actual memory modes, inventory and data actions |
| Firmware | `firmware.png` | HeyCyan-only blocked preflight |
| Firmware | `firmware-progress.png` | Exclusive six-stage update progress |
| Firmware | `firmware-result.png` | Paired-component partial result and safe recovery |
| Advanced | `advanced.png` | Secondary diagnostics and research runtimes |
| Prototype | `prototype-controls.png` | Deterministic debug fixture controls |

## Reuse rules for the eight built-ins

Do not create eight unrelated visual systems.

| Built-in | Canonical layout archetype | Unique content from the product spec |
|---|---|---|
| Local Agent | `local-agent-detail` | Accessibility/notification readiness, approval policy, privacy list, pending/history, safe read-only test |
| Meeting Spark Notes | `automation-detail` | Input, language/provider, transcript, summary sections and retention |
| Live Caption Relay | `translator-detail` | Input/language, phone caption fallback, compatible display output, text behavior and retention |
| Hands-Free Translator | `translator-detail` | Source/target language, input, spoken/display output, retention and test phrase |
| Errand Brain | `automation-detail` | Voice input, extracted list, destination, explicit item/time confirmation and preview |
| Auto Diary | `capture-detail` | Selected screen context, Accessibility, interval/pause, privacy list, redaction and no-store preview |
| Auto Audio | `automation-detail` | HeyCyan onboard-audio compatibility, schedule/duration/power/storage, Sync and visible test |
| Visual Diary | `capture-detail` | Compatible camera, explicit interval/pause, processing, retention and visible test capture |

## State variants generated from the same layouts

AI Studio must implement these as fixture-driven UI state, even when a dedicated screenshot is unnecessary:

- Home: not set up, reconnecting, recording, syncing and exclusive firmware session;
- Assistant: streaming, stopped, retry, personal Library recall and model unavailable;
- Live sessions: speaking, muted, reconnecting and ended;
- Library: search results, processing, multi-select and confirmed deletion;
- Automations: running, paused, incompatible, permission lost and failed;
- Sync: readiness, BLE loss, local Wi-Fi/IP/list failure, storage failure and complete;
- AI: testing, authentication failed, model unavailable and unsupported;
- Firmware: incompatible, preparation failure, verification pending and complete;
- all screens: long copy, 200% text and reduced motion.

## Implementation interpretation

The screenshots establish visual composition. Text, device names, metrics, versions, capabilities and results must come from fixture/UI models. Never bake screenshots or their sample values into the Android app.
