#!/usr/bin/env python3
"""Launch the development client under Xvfb and check initialization (not in-world rendering)."""
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time

project = Path(sys.argv[1]).resolve()
log = project / 'build' / 'client-smoke.log'
log.parent.mkdir(parents=True, exist_ok=True)
result = 1
with log.open('w') as output:
    process = subprocess.Popen(['xvfb-run', '-a', './gradlew', 'runClient', '--no-daemon'],
                               cwd=project, stdout=output, stderr=subprocess.STDOUT,
                               start_new_session=True,
                               env={**os.environ, 'JAVA_TOOL_OPTIONS': os.environ.get('JAVA_TOOL_OPTIONS', '') + ' -Dlavavisual.uiSmoke=true'})
    ready_since = None
    try:
        deadline = time.monotonic() + 300
        while time.monotonic() < deadline:
            text = log.read_text(errors='replace')
            if process.poll() is not None:
                raise RuntimeError(f'Client exited before completing startup: {process.returncode}')
            if re.search(r'InjectionError|InvalidMixinException|MixinApplyError|IllegalClassLoadError|Mixin transformation .* failed|Exception in thread|Reported exception thrown', text):
                raise RuntimeError('Client or mixin initialization failed')
            # Atlas creation follows model/shader loading. Stay alive for a few seconds afterwards.
            if re.search(r'LavaVisual 2\.2\.[0-9]+', text) and re.search(r'Created:.*(atlas|textures)', text) and 'LavaVisual UI smoke complete' in text:
                ready_since = ready_since or time.monotonic()
                if time.monotonic() - ready_since >= 12:
                    print('Client startup smoke passed; in-world visual correctness is NOT asserted.')
                    result = 0
                    break
            time.sleep(2)
        else:
            raise RuntimeError('Timed out waiting for resource initialization')
    except Exception as error:
        print(f'::error::{error}')
    finally:
        try:
            os.killpg(process.pid, signal.SIGTERM)
            process.wait(timeout=15)
        except ProcessLookupError:
            pass
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
if result:
    all_lines = log.read_text(errors='replace').splitlines()
    indices = set(range(max(0, len(all_lines) - 30), len(all_lines)))
    for i, line in enumerate(all_lines):
        if re.search(r'Exception|Error|Caused by:|Failed|crash|Description:', line):
            indices.update(range(max(0, i - 2), min(len(all_lines), i + 12)))
    lines = [all_lines[i] for i in sorted(indices)][:350]
    reports = sorted((project / 'run' / 'crash-reports').glob('*.txt'))
    if reports:
        lines = reports[-1].read_text(errors='replace').splitlines()[:100] + lines
    for start in range(0, len(lines), 15):
        message = '\n'.join(lines[start:start + 15]).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
        print('::error title=Client startup diagnostics::' + message)
sys.exit(result)
