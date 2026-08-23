from pathlib import Path

screen = Path('android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeConversationScreen.kt')
text = screen.read_text()

old = '''    fun startNewPrompt() {
        if (sending) return
        val newThreadId = session.startNewConversation()
'''
new = '''    fun startNewPrompt() {
        if (sending) return
        if (messages.isEmpty() && pendingPrompt == null) {
            message = ""
            webSearch = false
            errorText = null
            lastFailedPrompt = null
            showConversationHistory = false
            scope.launch {
                delay(80)
                focusComposer()
            }
            return
        }
        val newThreadId = session.startNewConversation()
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('startNewPrompt anchor not found')

old = '''            ADConversationComposer(
                message = message,
                onMessageChange = { message = it },
                webSearch = webSearch,
                sending = sending,
                focusRequester = composerFocusRequester,
                onSend = ::send,
            )
'''
new = '''            ADConversationComposer(
                message = message,
                onMessageChange = { message = it },
                webSearch = webSearch,
                onWebSearchChange = { webSearch = it },
                sending = sending,
                focusRequester = composerFocusRequester,
                onSend = ::send,
            )
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('composer call anchor not found')

old = '''private fun ADConversationComposer(
    message: String,
    onMessageChange: (String) -> Unit,
    webSearch: Boolean,
    sending: Boolean,
    focusRequester: FocusRequester,
    onSend: () -> Unit,
) {
'''
new = '''private fun ADConversationComposer(
    message: String,
    onMessageChange: (String) -> Unit,
    webSearch: Boolean,
    onWebSearchChange: (Boolean) -> Unit,
    sending: Boolean,
    focusRequester: FocusRequester,
    onSend: () -> Unit,
) {
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('composer signature anchor not found')

old = '''    ) {
        if (webSearch) {
            Row(
                modifier = Modifier.padding(start = 8.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Outlined.Public, null, tint = ADColors.Ink, modifier = Modifier.size(14.dp))
                Text("Web search", style = MaterialTheme.typography.labelMedium, color = ADColors.Ink)
            }
        }
        Surface(
'''
new = '''    ) {
        Surface(
'''
if old in text:
    text = text.replace(old, new, 1)
elif 'Text("Web search"' in text:
    raise SystemExit('web search label block changed unexpectedly')

old = '''            Row(
                modifier = Modifier.padding(start = 13.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicTextField(
'''
new = '''            Row(
                modifier = Modifier.padding(start = 5.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(
                    onClick = { onWebSearchChange(!webSearch) },
                    enabled = !sending,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = if (webSearch) "Disable web search" else "Enable web search",
                        tint = if (webSearch) ADColors.Blue else ADColors.Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                BasicTextField(
'''
if old in text:
    text = text.replace(old, new, 1)
elif 'contentDescription = if (webSearch) "Disable web search"' not in text:
    raise SystemExit('composer row anchor not found')

screen.write_text(text)

# Update focused source-level guardrails for the preserved functionality.
test = Path('android/AD-Glasses/app/src/test/java/com/ad_glasses/ui/adglasses/ADVisibleProductUiTest.kt')
t = test.read_text()
anchor = '        assertFalse(screen.contains("What did I capture today?"))\n'
insert = anchor + '''        assertTrue(screen.contains("onWebSearchChange = { webSearch = it }"))
        assertTrue(screen.contains("Enable web search"))
        assertTrue(screen.contains("if (messages.isEmpty() && pendingPrompt == null)"))
'''
if anchor in t and 'assertTrue(screen.contains("Enable web search"))' not in t:
    t = t.replace(anchor, insert, 1)
test.write_text(t)

print('AI hub polish applied')
