from pathlib import Path

screen = Path('android/AD-Glasses/app/src/main/java/com/ad_glasses/ui/adglasses/ADNativeConversationScreen.kt')
text = screen.read_text()
old = '''    }
    }
}

@Composable
private fun ADLiveAudioState'''
new = '''    }
}

@Composable
private fun ADLiveAudioState'''
if old in text:
    text = text.replace(old, new, 1)
    screen.write_text(text)
    print('repaired ADNativeConversationScreen function boundary')
elif new not in text:
    raise SystemExit('unexpected ADNativeConversationScreen boundary')
else:
    print('function boundary already repaired')
