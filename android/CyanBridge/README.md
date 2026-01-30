# CyanBridge

CyanBridge is an alternative Android app for HeyCyan-compatible / generic AI smartglasses.

## Key Notes
- **Android-only assistant support**: Gemini/ChatGPT workflows are supported on Android only.
- **Image queries require automation**: Tasker (paid) + AutoInput (paid plugin) + the CyanBridge Tasker profile enabled.
  - **TaskerNet**: [Tasker AI Profile](https://taskernet.com/shares/?user=AS35m8m%2BZfcOI%2FAn4TYXwIRGXRuXzE9zXexYgafojsO%2FQSXgVbu8nOiYo%2BLhLj1izKWhtzdxI6eOvMI%3D&id=Profile%3ATasker+AI)
  - **Repo profile (.xml)**: [tasker/Tasker_AI.xml](tasker/Tasker_AI.xml)

## Tasker Integration (Intents)
- **Broadcast action from app**: `com.fersaiyan.cyanbridge.AI_EVENT`
- **Command intent to app**: `com.fersaiyan.cyanbridge.ACTION_TASKER_COMMAND`
