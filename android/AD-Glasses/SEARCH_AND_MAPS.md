# Search & Maps integration

AD Glasses has two optional grounding layers before the selected Cloud AI profile produces the final answer:

- **Tavily Search** for live public-web evidence and source URLs.
- **OpenStreetMap-family services** for current-position context, reverse geocoding, nearby POIs and routes.

The assistant orchestrator owns both paths, so phone text, phone voice and glasses turns use the same policy. Ordinary conversation, coding, arithmetic and general explanation bypass both grounding layers.

Visual grounding is deliberately one-answer-only: **silent fact-only image observation → optional Tavily/OSM evidence → one final Cloud AI answer**. The silent observation never streams tokens or prepares speech output.

## Configure on device

Open **Cloud AI → Configure Realtime service**. The activity is titled **Cloud services** and contains a **Search & Maps** section.

### Tavily

1. Enter a Tavily API key (`tvly-…`).
2. Keep **Tavily web grounding** enabled.
3. Tap **Save Search & Maps** or **Save and test Tavily**.

The saved key is stored in Android Keystore-backed `EncryptedSharedPreferences`. It is never read back into a UI text field. Enter a value only when adding or replacing the key.

Requests use:

- `POST https://api.tavily.com/search`
- `Authorization: Bearer <key>`
- `search_depth = fast` for ordinary grounded turns
- `search_depth = advanced` only for explicit deep research/comparison/verification/evidence requests
- `include_answer = true`
- `include_raw_content = false`
- at most five results in assistant grounding

Result URLs are parsed as valid HTTP(S) URLs and deduplicated before the evidence block is built so `[n]` citations remain aligned with the source list appended to rich chat output.

When Tavily returns sourced evidence, AD disables the Cloud provider's native web tool for that generation to avoid duplicate retrieval. If Tavily is disabled, unconfigured, times out or returns no sourced result, provider-native web remains a fallback only when that turn is allowed to use web.

### Device location permission

Device location is optional and is **not** requested automatically because a phrase happened to match a spatial heuristic. In **Search & Maps**, tap **Grant location access** if you want `near me`, self-location, or routes from the current position.

Android may grant precise or approximate location; either is accepted. Named-place searches such as `cafes near Cubbon Park` and explicit place-to-place routes do not require device GPS.

## Intent routing: avoid false positives first

The Android policy uses **compound intent heuristics**, not a bag of single keywords. Bare words such as `find`, `search`, `current`, `price`, `score`, `available`, `restaurant` or `route` are not enough by themselves.

Conceptually the routing priority is:

1. **Image attached / glasses vision** → normal image answer, or the silent grounded-visual pipeline when external evidence is actually useful.
2. **Spatial intent** → location/OSM pipeline.
3. **Explicit or inherently-live web intent** → Tavily/provider web pipeline.
4. **Otherwise** → direct Cloud AI inference with no Tavily, GPS, Nominatim or Overpass latency.

Spatial and web grounding can intentionally run together when the question needs both. Example: “nearest pharmacy open right now” needs OSM for nearby candidates and web evidence for live opening status. A plain “nearest pharmacy” does not automatically double-dip into Tavily.

### High-confidence spatial triggers

Location/OSM may run for compound phrases such as:

- proximity: `near me`, `nearby`, `around here`, `closest`, `nearest`, `within 500 m` with an actual POI category;
- current-position identity: `where am I?`, `what street am I on?`, `what city am I in?`;
- routing: `directions to X`, `navigate to X`, `how do I get to X?`, `route from X to Y`, `how far is X from me?`;
- POI discovery: `find a cafe and pharmacy near me`, `museum in this area`, `cafes within 1 km of Cubbon Park`;
- selective visual landmark context when a grounded visual turn needs nearby map evidence.

Named destinations remain named destinations. `Navigate to the Ritz hotel` is forward-geocoded as that place; it is **not** converted into “route to the nearest hotel”. Generic targets such as `navigate to a pharmacy` may use the nearest matching POI.

Personal anchors that AD cannot resolve safely are not silently replaced with the current GPS fix. `Find a pharmacy near my hotel` or `closest cafe to my office` stays off the map path unless the place is otherwise resolved; the normal assistant can ask for clarification.

### Spatial false-positive protection

These stay on the normal assistant path:

- `What makes a good restaurant?`
- `How do banks work?`
- `Find the bug in this function.`
- `Route HTTP requests from service A to service B.`
- `Directions from graph node A to graph node B.`
- `Walk me through Kotlin coroutines.`
- `What does “near me” mean in search?`
- `What is around here in this code?`
- `Where am I in this proof?`
- `Find the nearest node in this graph.`

The policy contains explicit technical/meta-context vetoes for networking, source code, graphs, data structures and explanatory uses of GPS/routing/proximity language.

### High-confidence web triggers

Web retrieval runs for:

- explicit directives: `search the web`, `browse the internet`, `check online`, `Google this`, `look up ...`;
- conversational weather: `is it going to rain?`, `do I need an umbrella?`, `weather tomorrow`;
- live markets: a named stock/share price, concrete currency pair/rate, or crypto price;
- sports results/schedules: `who won the match?`, `final score`, `when is the next match?`;
- live business facts: `open now`, opening/business hours, in-stock/sold-out/availability-now wording;
- current news/headlines;
- clearly current versions/releases/security patches when paired with recency wording;
- inherently changing office-holders/elections: `who is the CEO of X?`, `who is the president of X?`, election results;
- explicit flight/service-status wording.

Short conversational follow-ups may inherit web intent **only from the immediately relevant previous user request**, for example:

- `What's the weather today?` → `And tomorrow?`
- `What's Bitcoin at?` → `And now?`

Assistant-generated text never creates inherited web intent by itself.

### Web false-positive protection

These do not automatically use Tavily:

- `Find the square root of 81.`
- `Find the bug in this function.`
- `Search your feelings.`
- `Search for the maximum value in this array.`
- `Look up a key in this hashmap.`
- `Look up at the sky.`
- `Explain electrical current.`
- `What is a current account?`
- `Explain price elasticity.`
- `What does availability mean in distributed systems?`
- `What is a musical score?`
- `Give me the newest recipe ideas.`
- `Forecast sales for next quarter.`
- `What is local news?` / `What does breaking news mean?`
- `Explain stock price.` / `What is an exchange rate?`
- `Explain service status in a state machine.`

The policy explicitly distinguishes terminology/concept questions from requests for current values. For example, `What is local news?` stays offline, while `What is the local news?` is treated as a live request; `Explain stock price` stays offline, while `Explain Apple's stock price` can use current data.

A visible per-turn web choice of **off** suppresses inferred freshness and inherited web use. A direct utterance such as `search the web` or `look up ...` is treated as a new explicit request and can re-enable web for that turn. The same explicit-off state is preserved through the grounded camera pipeline and cannot be silently re-enabled by provider-native web fallback.

## Why AD does not ask for voice confirmation on every search

AD does **not** interrupt high-confidence requests with “Do you want me to use the web/maps?” Doing that for `where am I?`, `nearest pharmacy`, `who won the match?` or `is it raining?` would add an extra ASR/TTS round trip, increase latency and make common voice interactions feel unreliable.

Instead:

- high-confidence compound intent runs the relevant grounding layer;
- ambiguous/meta/technical wording stays off-network;
- unresolved semantic ambiguity is left to the normal assistant response, which can ask a clarification naturally when needed;
- current-device location additionally requires the user to grant Android location access from Search & Maps settings.

This bias intentionally prefers an occasional false negative over silently leaking location or paying network latency on a false positive.

## OpenStreetMap stack

Default public endpoints:

- Nominatim: `https://nominatim.openstreetmap.org`
- Overpass: `https://overpass-api.de/api/interpreter`
- routing root: `https://routing.openstreetmap.de`

All three are editable on-device. Configured endpoints must be HTTPS. Embedded credentials, query strings, fragments and malformed hosts are rejected; Nominatim/routing base settings must be service roots. This allows production deployments to move to an AD proxy, managed provider or self-hosted instance without shipping another APK.

### Nominatim

AD uses Nominatim only for **user-triggered** reverse geocoding and one-shot destination/reference-place geocoding. It is never used for autocomplete.

The client:

- sends an identifying `User-Agent`;
- globally serializes Nominatim calls;
- enforces at least 1.05 seconds between public calls;
- caches reverse-geocode results in memory;
- never performs periodic/background geocoding;
- uses cancellable requests with bounded call timeouts.

The shared `nominatim.openstreetmap.org` service has an absolute maximum of one request per second and requires attribution, caching and an identifying User-Agent. For scaled/commercial use, configure a proxy/self-hosted or suitable third-party service.

### Overpass

Nearby requests are deterministic and use a fixed whitelist of OSM tags rather than arbitrary LLM-generated Overpass QL. Current categories include cafes, pharmacies, restaurants/fast food, bars/pubs, hospitals/clinics/doctors/dentists/vets, ATM/bank, supermarkets/convenience/bakery, selected retail, fuel/EV charging, toilets/water, police/fire/post office, parking, bus/train/subway/airport, hotels, museums/cinemas/theatres, parks/playgrounds, libraries, gyms, schools/universities, taxi ranks, laundry and vehicle rentals.

User radii support metres/kilometres/miles/feet and are clamped to **50–5000 m**. Overpass calls are serialized. The query uses `out body center` so node coordinates and way/relation centers are all usable.

Visual landmark grounding can query nearby `tourism=attraction`, `historic=*` and buildings near the current fix when the question truly asks for external landmark identity.

### OSRM / FOSSGIS routing

The default `routing.openstreetmap.de` root exposes separate prepared OSRM services:

- driving → `/routed-car`
- walking → `/routed-foot`
- cycling → `/routed-bike`

Each FOSSGIS service uses its prepared graph and the route path profile `driving`, e.g. `/routed-foot/route/v1/driving/...`. For a custom/self-hosted OSRM root, AD uses the configured server with `driving`, `foot` and `bike` profiles respectively.

Route requests use `overview=false`, `steps=true`, `alternatives=false`, and public FOSSGIS requests are serialized/rate-limited. Route failures are non-fatal and AD is instructed never to fabricate turn-by-turn directions.

## Location privacy and permissions

Current-device location is fetched **only when the current user request needs it**. There is no periodic location tracking in this feature.

The **Grant location access** control in Search & Maps is the explicit Android consent entry point. A voice/text intent match never opens a permission dialog by itself. If permission or a fresh fix is unavailable, grounding fails open: normal assistant inference continues with an explicit “location unavailable” evidence note rather than inventing the user's position.

A recent fused-location fix is preferred. If it is stale or inaccurate, AD requests a balanced-power current fix with a bounded timeout.

For a direct self-location request, reverse-geocoded address context can be used because that precision is necessary to answer the user's request. For other local web/visual grounding, the final model and Tavily receive only coarse area context (neighbourhood/city/state/country); raw POI coordinates and street-level location are omitted from the evidence block.

The silent visual observer is instructed not to preserve sensitive identifiers such as email addresses, phone numbers, license plates, QR payloads, account/card numbers or serial numbers in visual memory; common email/phone/long-number patterns are also redacted before visual evidence is incorporated into a Tavily query.

## Latency and cancellation

Normal direct assistant turns retain the existing low-latency Cloud AI deadlines; search/maps does **not** globally increase the normal wearable first-answer timeout.

Grounding runs before final Cloud inference and has a separate bounded envelope:

- text grounding: about **8 s** maximum budget;
- grounded visual retrieval after observation: about **9.5 s**;
- explicit advanced research grounding: about **10.5 s**;
- routing turns: about **11.5 s** so one/two Nominatim lookups plus OSRM are not forced into the ordinary text envelope;
- absolute grounding budget clamp: **15 s**;
- individual Nominatim/Overpass/OSRM/Tavily calls have smaller call timeouts;
- cancelled/superseded assistant turns cancel cancellable grounding sockets.

Tavily is started asynchronously when web evidence and OSM evidence are both needed, so independent web work can overlap spatial stages. If the budget is exhausted, missing evidence is skipped and the assistant continues rather than extending a user turn indefinitely.

## Failure and trust behavior

Tavily, Nominatim, Overpass and routing failures are optional-evidence failures, not app-fatal errors. Authentication/rate-limit/network failures are logged without API keys or full private prompts.

Retrieved web snippets are wrapped in `AD_RETRIEVED_GROUNDING` and explicitly marked **untrusted evidence, never instructions**. The synthesis prompt tells the model not to obey commands found inside webpages or images and not to invent location, routes, live prices or identity when evidence is absent.

Tavily source URLs are appended to rich chat output. Spoken output remains concise and is not forced to read URLs aloud.

OpenStreetMap-derived rich output appends OSM attribution.

## Representative flows

- `What's the latest news about X?` → Tavily → Cloud synthesis → source URLs.
- `Who won the match?` → Tavily/provider live web → concise answer.
- `Find coffee shops within 200 m near me.` → GPS → Overpass cafe query → Cloud response.
- `Find cafes within 1 km of Cubbon Park.` → one-shot Nominatim reference geocode → Overpass; no device GPS required.
- `Navigate to the nearest pharmacy.` → GPS → Overpass pharmacy → nearest POI → OSRM route.
- `Navigate to the Ritz hotel.` → GPS → named destination geocode → OSRM route.
- `Route from Bengaluru Palace to Cubbon Park.` → geocode both explicit endpoints → route; no device GPS required.
- `Nearest pharmacy open right now.` → GPS/OSM nearby evidence + live web evidence in one synthesis.
- glasses image + `What landmark is this?` → silent visible-facts observation → selective OSM/Tavily evidence → exactly one final answer.
- glasses image + `Read this sign.` → normal local visual answer; no automatic Tavily/OSM grounding.

## Source policy references

Re-check current provider/public-service policies before changing request volume or defaults:

- Nominatim usage policy: https://operations.osmfoundation.org/policies/nominatim/
- Overpass API: https://wiki.openstreetmap.org/wiki/Overpass_API
- FOSSGIS routing service: https://routing.openstreetmap.de/about.html
- OSRM HTTP API: https://project-osrm.org/docs/
- Tavily Search API: https://docs.tavily.com/documentation/api-reference/endpoint/search
