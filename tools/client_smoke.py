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
                               start_new_session=True)
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
            if 'LavaVisual 1.1.0' in text and re.search(r'Created:.*(atlas|textures)', text):
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
    lines = log.read_text(errors='replace').splitlines()[-120:]
    for start in range(0, len(lines), 15):
        message = '\n'.join(lines[start:start + 15]).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
        print('::error title=Client startup diagnostics::' + message)
sys.exit(result)
