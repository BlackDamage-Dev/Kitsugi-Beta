import json
import os
import pathlib

p = pathlib.Path('archive-device.log')
summary = '\n'.join(p.read_text(errors='replace').splitlines()[-160:]) if p.exists() else 'Build log not created; emulator setup failed.'
for f in pathlib.Path('scripts/subtitle-tests/android/build/outputs/androidTest-results').rglob('*.xml'):
    summary += '\n' + f.read_text(errors='replace')[-16000:]
logcat = pathlib.Path('archive-logcat.txt')
if logcat.exists():
    lines = logcat.read_text(errors='replace').splitlines()
    indexes = [i for i, line in enumerate(lines) if any(k in line for k in ('Fatal signal', 'FATAL EXCEPTION', 'backtrace:', 'libarchive', 'archive-jni'))]
    selected = sorted({j for i in indexes for j in range(max(0, i-5), min(len(lines), i+35))})
    summary += '\nLOGCAT\n' + '\n'.join(lines[j] for j in selected)[-30000:]
payload = {'name': 'rar-device-diagnostics', 'head_sha': os.environ['GITHUB_SHA'], 'status': 'completed', 'conclusion': 'neutral', 'output': {'title': 'Android RAR test diagnostics', 'summary': summary[-60000:]}}
pathlib.Path('/tmp/rar-check.json').write_text(json.dumps(payload))
