from pathlib import Path

path = Path('android/AD-Glasses/app/src/main/java/com/ad_glasses/MainActivity.kt')
text = path.read_text(encoding='utf-8')
duplicate = 'import android.media.MediaScannerConnection\nimport android.media.MediaScannerConnection\n'
if text.count(duplicate) != 1:
    raise SystemExit(f'expected one duplicate MediaScannerConnection import block, found {text.count(duplicate)}')
text = text.replace(duplicate, 'import android.media.MediaScannerConnection\n', 1)
path.write_text(text, encoding='utf-8')
print('removed duplicate MediaScannerConnection import')
