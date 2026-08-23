from pathlib import Path

test = Path('android/AD-Glasses/app/src/test/java/com/ad_glasses/ui/adglasses/ADVisibleProductUiTest.kt')
text = test.read_text()
text = text.replace('assertTrue(screen.contains("Text("AI""))', 'assertTrue(screen.contains("Text(\\\"AI\\\""))')
text = text.replace('assertFalse(screen.contains("AD-owned ${internalProvider.label} conversation"))', 'assertFalse(screen.contains("AD-owned "))')
test.write_text(text)

if 'Text("AI""' in text or '${internalProvider.label}' in text:
    raise SystemExit('AI hub test quoting repair did not complete')
print('repaired AI hub test quoting')
