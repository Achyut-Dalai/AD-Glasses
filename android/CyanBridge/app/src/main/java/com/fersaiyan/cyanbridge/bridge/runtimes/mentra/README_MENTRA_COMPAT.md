# MentraOS Compatibility Notes

## What works

- **Local HTTP relay** accepts connections on port 8002 via `POST /display`
- **Connection handshake** (`tpa_connection_init` → `tpa_connection_ack`)
- **Display commands**: `text_wall`, `double_text_wall`, `reference_card`, `dashboard_card`
- **Input events**: `button_press`, `head_position` (forwarded to connected apps via broadcast)
- **Health check**: `GET /health` returns relay status and session count
- **CORS headers**: `Access-Control-Allow-Origin: *` for cross-origin requests

## What's stubbed

- **Subscription filtering** — all events are broadcast to all connected sessions
- **API key validation** — any key is accepted in local/development mode
- **Dashboard view** — mapped to main display commands
- **Input forwarding** — works over the HTTP response stream (best-effort; persistent
  connections or WebSocket upgrade needed for reliable push)

## What's not supported

- Full MentraOS Cloud relay (requires a public server + API key management)
- Audio streaming / microphone capture
- Location or calendar events
- TTS playback
- WebSocket protocol (HTTP POST only for now)

## Message format

### Display message (app → relay)

```json
{
    "type": "display_event",
    "packageName": "com.example.myapp",
    "layout": {
        "layoutType": "text_wall",
        "text": "Hello from MentraOS app"
    },
    "durationMs": 5000,
    "forceDisplay": false
}
```

### Connection init (app → relay)

```json
{
    "type": "tpa_connection_init",
    "packageName": "com.example.myapp",
    "sessionId": "my-session-123",
    "apiKey": "..."
}
```

### Connection ack (relay → app)

```json
{
    "type": "tpa_connection_ack",
    "sessionId": "my-session-123",
    "settings": {},
    "status": "ok"
}
```

### Input event (relay → app)

```json
{
    "type": "data_stream",
    "streamType": "button_press",
    "data": { "buttonId": "main", "pressType": "short" }
}
```

### Subscription update (app → relay)

```json
{
    "type": "subscription_update",
    "sessionId": "my-session-123",
    "subscriptions": ["button_press", "head_position", "transcription"]
}
```

## Layout types

| Type               | Fields                           | Mapped to            |
|--------------------|----------------------------------|----------------------|
| `text_wall`        | `text: string`                   | `DisplayCommand.Text` |
| `double_text_wall` | `topText, bottomText: string`    | `DisplayCommand.Lines` |
| `reference_card`   | `title, text: string`            | `DisplayCommand.Card` |
| `dashboard_card`   | `leftText, rightText: string`    | `DisplayCommand.Text` |

## How to test

1. Start the relay from Bridge Lab (MentraOS tab) — or call `start()` programmatically
2. Note the IP and port shown (default port `8002`)
3. Send a test display command:

   ```bash
   curl -X POST http://<phone-ip>:8002/display \
     -H "Content-Type: application/json" \
     -d '{"type":"display_event","packageName":"test","layout":{"layoutType":"text_wall","text":"Hello from curl"}}'
   ```

4. Check health:

   ```bash
   curl http://<phone-ip>:8002/health
   ```

5. The display content should appear on the glasses (requires active bridge adapter)
