#!/usr/bin/env python3
"""CI check: the extension loads in the current Geyser Standalone and finds every part of Geyser it relies on.

    python3 bedrock/tools/geyser_smoke.py path/to/LavaVisual-Bedrock.jar [geyser.jar]

Downloads the latest Geyser Standalone unless a jar is given, starts it with the extension in extensions/, and
waits for the extension's ready line (it is only printed after the check of Geyser's internals passed).
"""
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

GEYSER = 'https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/standalone'
READY, BROKEN = 'LavaVisual Bedrock ready', ('LavaVisual:', 'LavaVisual Bedrock stopped', 'Error loading extension', 'Could not load extension')


def main():
    extension = Path(sys.argv[1]).resolve()
    work = Path(tempfile.mkdtemp(prefix='geyser-'))
    geyser = work / 'Geyser-Standalone.jar'
    if len(sys.argv) > 2:
        shutil.copy(sys.argv[2], geyser)
    else:
        with urllib.request.urlopen(GEYSER, timeout=120) as response, open(geyser, 'wb') as out:
            shutil.copyfileobj(response, out)
    (work / 'extensions').mkdir()
    shutil.copy(extension, work / 'extensions' / extension.name)
    log = work / 'console.log'
    with open(log, 'w') as out:
        process = subprocess.Popen(['java', '-Xmx1G', '-jar', geyser.name, '--nogui'], cwd=work, stdout=out, stderr=subprocess.STDOUT,
                                   stdin=subprocess.PIPE)
    ok, deadline = False, time.time() + 150
    try:
        while time.time() < deadline and process.poll() is None:
            text = log.read_text(errors='replace')
            if READY in text:
                ok = True
                break
            if any(marker in text for marker in BROKEN):
                break
            time.sleep(1)
        time.sleep(2)
    finally:
        try:
            process.stdin.write(b'geyser stop\n')
            process.stdin.flush()
            process.wait(timeout=20)
        except Exception:
            process.kill()
    text = log.read_text(errors='replace')
    lines = [line for line in text.splitlines() if 'LavaVisual' in line or 'xtension' in line or 'Done' in line or 'ERROR' in line]
    print('\n'.join(lines[-40:]))
    version = next((line for line in text.splitlines() if 'Geyser' in line and ('version' in line.lower() or 'Loading' in line)), '')
    print('Geyser:', version.strip()[:200])
    if not ok:
        print(text[-6000:])
        print('LavaVisual Bedrock did not start in Geyser Standalone')
        return 1
    print('LavaVisual Bedrock smoke ok: loaded by Geyser Standalone, Geyser internals found')
    return 0


if __name__ == '__main__':
    sys.exit(main())
