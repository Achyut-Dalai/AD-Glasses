from pathlib import Path

APP = Path('android/AD-Glasses/app')
SRC = APP / 'src/main/java/com/ad_glasses'
TEST = APP / 'src/test/java/com/ad_glasses/ui/adglasses'


def replace_if_present(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    if old in text:
        text = text.replace(old, new)
        path.write_text(text)
        print(f'updated {label}')

# Fix the function boundary caught during source review and remove now-unused imports.
screen = SRC / 'ui/adglasses/ADNativeConversationScreen.kt'
text = screen.read_text()
if '\n\n\n@Composable\nprivate fun ADLiveAudioState' in text:
    text = text.replace(
        '\n\n\n@Composable\nprivate fun ADLiveAudioState',
        '\n    }\n}\n\n@Composable\nprivate fun ADLiveAudioState',
        1,
    )
text = text.replace('import android.content.Context\n', '')
text = text.replace('import androidx.compose.ui.text.style.TextAlign\n', '')
screen.write_text(text)

# New AD conversations should auto-title from their first user message via ChatStore.
session = SRC / 'ai/orchestrator/AssistantConversationSession.kt'
replace_if_present(
    session,
    '            title = AssistantConversationPolicy.THREAD_TITLE,\n',
    '            title = "New chat",\n',
    'new conversation auto-title seed',
)

# The public AI compatibility destination now opens the AI conversation tab. Provider configuration
# remains under Device Center; this destination is for using AI, not configuring it.
app = SRC / 'ui/adglasses/ADGlassesApp.kt'
replace_if_present(
    app,
    '            ADExternalDestination.AI -> routeStack = listOf(ADRoute.MAIN, ADRoute.DEVICE_CENTER)\n',
    '''            ADExternalDestination.AI -> {
                routeStack = listOf(ADRoute.MAIN)
                selectedTab = ADTab.AI
            }
''',
    'external AI destination',
)

# Visible settings language follows the new AI conversation naming.
settings = SRC / 'ui/adglasses/ADProductSettingsScreens.kt'
text = settings.read_text()
replacements = {
    'ADSettingsDetailGroup("AD Chats")': 'ADSettingsDetailGroup("AI conversations")',
    'title = "Clear all AD Chats"': 'title = "Clear AI conversations"',
    'title = { Text("Clear all AD Chats?") }': 'title = { Text("Clear AI conversations?") }',
    '"No AD Chats to clear"': '"No AI conversations to clear"',
}
for old, new in replacements.items():
    text = text.replace(old, new)
settings.write_text(text)

# Update product-surface tests to the new Home / AI / Library navigation contract.
inventory = TEST / 'ADScreenInventoryTest.kt'
text = inventory.read_text()
text = text.replace('fun primaryTabsAreHomeChatsAndLibrary()', 'fun primaryTabsAreHomeAiAndLibrary()')
text = text.replace('listOf("Home", "Chats", "Library")', 'listOf("Home", "AI", "Library")')
text = text.replace('assertFalse(ADTab.entries.any { it.name == "AI" })', 'assertTrue(ADTab.entries.any { it.name == "AI" })')
text = text.replace('"ADTab.CHATS -> ADNativeConversationScreen("', '"ADTab.AI -> ADNativeConversationScreen("')
text = text.replace('        assertFalse(app.contains("ADTab.AI"))\n', '        assertTrue(app.contains("ADTab.AI"))\n')
inventory.write_text(text)

visible = TEST / 'ADVisibleProductUiTest.kt'
text = visible.read_text()
text = text.replace('        assertFalse(app.contains("ADTab.AI"))\n', '        assertTrue(app.contains("ADTab.AI"))\n')
# Add focused UI guardrails once.
marker = '    @Test\n    fun mainActivityMountsComposeInsteadOfReactNative() {'
if 'fun aiConversationHubIsMinimalAndManageable()' not in text:
    block = '''    @Test
    fun aiConversationHubIsMinimalAndManageable() {
        val screen = appFile("src/main/java/com/ad_glasses/ui/adglasses/ADNativeConversationScreen.kt").readText()
        val components = appFile("src/main/java/com/ad_glasses/ui/adglasses/ADComponents.kt").readText()

        assertTrue(screen.contains("Text(\"AI\""))
        assertTrue(screen.contains("ADConversationHistory("))
        assertTrue(screen.contains("Rename conversation"))
        assertTrue(screen.contains("Delete conversation?"))
        assertTrue(screen.contains("Clear AI conversations?"))
        assertTrue(screen.contains("session.startNewConversation()"))
        assertFalse(screen.contains("ADConversationRouteDisclosure("))
        assertFalse(screen.contains("ADPromptSuggestion("))
        assertFalse(screen.contains("What did I capture today?"))
        assertFalse(screen.contains("AD-owned ${internalProvider.label} conversation"))
        assertTrue(components.contains("ADTab.AI -> Icons.Rounded.AutoAwesome"))
        assertFalse(components.contains("Icons.Outlined.Terminal"))
    }

'''
    text = text.replace(marker, block + marker, 1)
visible.write_text(text)

isolation = TEST / 'ADProductSurfaceIsolationTest.kt'
text = isolation.read_text()
text = text.replace('fun primaryTabsAreExactlyHomeChatsLibrary()', 'fun primaryTabsAreExactlyHomeAiLibrary()')
text = text.replace('assertEquals(listOf("Home", "Chats", "Library"), ADTab.entries.map { it.label })', 'assertEquals(listOf("Home", "AI", "Library"), ADTab.entries.map { it.label })')
text = text.replace('assertFalse(ADTab.entries.any { it.name == "AI" })', 'assertTrue(ADTab.entries.any { it.name == "AI" })')
text = text.replace('fun aiLivesInsideDeviceCenterAndExternalAiRoutesThere()', 'fun aiConversationTabCoexistsWithDeviceCenterProviderConfiguration()')
text = text.replace(
    'assertTrue(app.contains("ADExternalDestination.AI -> routeStack = listOf(ADRoute.MAIN, ADRoute.DEVICE_CENTER)"))\n        assertFalse(app.contains("ADTab.AI"))',
    'assertTrue(app.contains("ADExternalDestination.AI -> {"))\n        assertTrue(app.contains("selectedTab = ADTab.AI"))',
)
isolation.write_text(text)

# Source-level safety assertions for this change.
checks = {
    'models tab': (SRC / 'ui/adglasses/ADGlassesModels.kt', 'AI("AI")'),
    'AI icon': (SRC / 'ui/adglasses/ADComponents.kt', 'ADTab.AI -> Icons.Rounded.AutoAwesome'),
    'history manager': (screen, 'ADConversationHistory('),
    'rename': (screen, 'Rename conversation'),
    'delete': (screen, 'Delete conversation?'),
    'no suggestions': (screen, 'ADPromptSuggestion('),
}
for label, (path, token) in checks.items():
    body = path.read_text()
    if label == 'no suggestions':
        if token in body:
            raise SystemExit('suggestion UI still present')
    elif token not in body:
        raise SystemExit(f'missing expected {label}: {token}')

print('AI conversation hub follow-up completed')
