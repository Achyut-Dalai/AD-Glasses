# Search & Maps integration

AD Glasses uses two independent grounding layers before the selected Cloud AI profile produces the final answer:

- **Tavily Search** for live web evidence and source URLs.
- **OpenStreetMap-family services** for current-position context, reverse geocoding, nearby POIs and routes.

The assistant orchestrator owns both paths, so phone text, phone voice and glasses turns use the same policies. Visual identification is a two-stage path: the vision provider first describes the frame, then AD combines that observation with relevant Tavily/OSM evidence and asks the active Cloud AI profile for one final answer.

## Configure on device

Open **Cloud AI → Configure Realtime service**. The activity is now titled **Cloud services** and contains a **Search & Maps** section.

### Tavily

1. Enter a Tavily API key (`tvly-…`).
2. Keep **Tavily web grounding** enabled.
3. Tap **Save Search & Maps** or **Save and test Tavily**.

The saved key is stored in Android Keystore-backed `EncryptedSharedPreferences`. It is never read back into a UI text field. Enter a value only when adding or replacing the key.

Requests use:

- `POST https://api.tavily.com/search`
- `Authorization: Bearer <key>`
- `search_depth = basic` for normal low-latency turns
- `search_depth = advanced` for explicit research/verification requests
- `include_answer = true`
- `include_raw_content = false`
- at most five results in assistant grounding

When Tavily succeeds, AD disables the Cloud provider's native web tool for that generation to avoid duplicate searches. If Tavily is disabled, unconfigured or temporarily fails, the existing provider-native web path remains available when the active provider supports it.

## When web grounding runs

Web search runs when any of these is true:

- the Ask UI explicitly enables web for the turn;
- the user says to search/browse the web;
- the question is inherently fresh, such as latest/current news, weather, prices, availability or opening status;
- a visual question asks to identify or verify a landmark, product, plant, price or similar object and Tavily is configured.

A visible per-turn web choice of **off** suppresses automatic freshness search. An explicit spoken/typed command such as “search the web” still enables it.

Retrieved snippets are inserted inside an `AD_RETRIEVED_GROUNDING` block that tells the model to treat them as untrusted evidence and never as instructions. Source URLs are appended to rich chat output; spoken output is not forced to read URLs aloud.

## OpenStreetMap stack

Default development endpoints:

- Nominatim: `https://nominatim.openstreetmap.org`
- Overpass: `https://overpass-api.de/api/interpreter`
- OSRM: `https://router.project-osrm.org`

All three are editable on-device. This is intentional: production deployments can move to an AD proxy, managed provider or self-hosted instance without shipping another APK.

### Nominatim

AD uses Nominatim only for **user-triggered** reverse geocoding and one-shot destination geocoding. It is never used for autocomplete.

The client:

- sends an identifying `User-Agent`;
- globally serializes Nominatim calls;
- enforces at least 1.05 seconds between calls;
- caches reverse-geocode results in memory;
- never performs periodic/background geocoding.

The shared `nominatim.openstreetmap.org` service has an absolute maximum of one request per second and requires attribution, caching and an identifying User-Agent. For a scaled/commercial rollout, use a proxy/self-hosted or suitable third-party Nominatim service.

### Overpass

Nearby requests are deterministic and use a fixed whitelist of OSM tags rather than arbitrary LLM-generated Overpass QL. Supported conversational categories include:

- cafe/coffee
- pharmacy
- restaurant
- hospital/clinic/doctors
- ATM/bank
- supermarket
- fuel
- toilets
- police
- parking
- bus stops
- hotels
- parks

User-specified radii are parsed from meters/kilometres and clamped to **50–5000 m**. Overpass calls are serialized to avoid parallel load on public instances. Visual landmark grounding can query nearby `tourism=attraction`, `historic=*` and named buildings near the current fix.

### OSRM

Routing uses:

`GET /route/v1/{profile}/{lon,lat;lon,lat}?overview=false&steps=true&alternatives=false`

AD understands:

- driving → `driving`
- walking → `foot`
- cycling → `bike`

OSRM profiles are determined by how the target OSRM server was prepared. The public demo commonly demonstrates `driving`; a production endpoint must actually expose the desired `foot`/`bike` profiles if walking/cycling routes are required. Route failures are non-fatal: the assistant still receives the resolved destination and can explain that the configured routing server did not provide that mode.

## Location privacy and permissions

Location is fetched **only when the current user request needs spatial context** (for example “near me”, “where am I?”, a nearby category, directions, or visual landmark grounding). There is no periodic location tracking in this feature.

AD uses the existing Android coarse/fine location permission. If permission or a fresh fix is unavailable, map grounding fails open: normal assistant inference still runs and the grounding context records that current location was unavailable.

The preferred location source is a recent fused-location fix. If that is stale or inaccurate, AD requests a fresh balanced-power fix with a bounded timeout.

## Examples

- “What’s the latest news about X?” → Tavily → Cloud AI synthesis → source URLs.
- “Is this store open now?” → fresh web grounding (and location if the wording also requests nearby/local context).
- “Find coffee shops within 200 m near me.” → GPS → Nominatim → Overpass cafe query → Cloud AI response.
- “Navigate to the nearest pharmacy.” → GPS → Overpass pharmacy query → nearest POI → OSRM route → concise directions.
- “Give me walking directions to Cubbon Park.” → GPS → one-shot Nominatim destination geocode → OSRM `foot` route (when configured server supports it).
- Glasses image + “What landmark is this?” → vision observation → GPS/Nominatim + nearby OSM landmarks + Tavily → final grounded answer.

## Failure behavior

Tavily, Nominatim, Overpass and OSRM failures never crash or block the assistant's normal answer path. Each service is treated as optional evidence. Authentication/rate-limit/network errors are logged without API keys or full private prompts.

OpenStreetMap-derived rich output appends:

`Map data © OpenStreetMap contributors`

## Source policy references

Before changing shared-public endpoints or request volume, re-check the current service policies:

- Nominatim usage policy: https://operations.osmfoundation.org/policies/nominatim/
- Overpass API: https://wiki.openstreetmap.org/wiki/Overpass_API
- OSRM HTTP API: https://project-osrm.org/docs/
- Tavily Search API: https://docs.tavily.com/documentation/api-reference/endpoint/search
