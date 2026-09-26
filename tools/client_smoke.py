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


def capture(target):
    # Loom may start its own Xvfb. Find the innermost display, rather than guessing :99.
    for proc in sorted(Path('/proc').glob('[0-9]*'), key=lambda p: int(p.name), reverse=True):
        try:
            args = (proc / 'cmdline').read_bytes().decode().split('\0')
            if Path(args[0]).name != 'Xvfb' or '-auth' not in args:
                continue
            env = {**os.environ, 'DISPLAY': args[1], 'XAUTHORITY': args[args.index('-auth') + 1]}
            shot = subprocess.run(['import', '-window', 'root', str(target)], env=env, timeout=10)
            if shot.returncode == 0:
                return True
        except (OSError, ValueError, subprocess.TimeoutExpired):
            continue
    return False


# Bigger window and GUI scale 2 so screenshots show the UI at a realistic pixel density.
run_dir = project / 'run'
run_dir.mkdir(parents=True, exist_ok=True)
options = run_dir / 'options.txt'
if not options.exists():
    options.write_text('guiScale:2\n')
shots = set()
with log.open('w') as output:
    process = subprocess.Popen(['xvfb-run', '-a', './gradlew', 'runClient', '--no-daemon'],
                               cwd=project, stdout=output, stderr=subprocess.STDOUT,
                               start_new_session=True,
                               env={**os.environ, 'ALSOFT_DRIVERS': 'null',  # OpenAL without a sound card: real playback, no output
                                    'JAVA_TOOL_OPTIONS': os.environ.get('JAVA_TOOL_OPTIONS', '') + ' -Dlavavisual.uiSmoke=true'
                                    + ' -Dlavavisual.testAudio=' + str(Path(__file__).resolve().parent / 'test-audio')})
    ready_since = None
    try:
        deadline = time.monotonic() + 660
        while time.monotonic() < deadline:
            text = log.read_text(errors='replace')
            if process.poll() is not None:
                raise RuntimeError(f'Client exited before completing startup: {process.returncode}')
            if re.search(r'InjectionError|InvalidMixinException|MixinApplyError|IllegalClassLoadError|Mixin transformation .* failed|Exception in thread|Reported exception thrown', text):
                raise RuntimeError('Client or mixin initialization failed')
            if re.search(r'Failed to load font|Unable to load font|Failed to load[^\n]*lavavisual|Couldn\'t load font|Unable to load[^\n]*lavavisual', text):
                raise RuntimeError('LavaVisual resource loading failed')
            if 'LavaVisual smoke resource reload failed' in text:
                raise RuntimeError('Resource reload failed')
            for name in re.findall(r'LavaVisual smoke shot (\w+)', text):
                if name not in shots:
                    shots.add(name)
                    capture(project / 'build' / f'shot-{name}.png')
            # Atlas creation follows model/shader loading. Stay alive for a few seconds afterwards.
            if re.search(r'LavaVisual [0-9]+\.[0-9]+\.[0-9]+', text) and 'LavaVisual smoke resource reload ok' in text and re.search(r'Created:.*(atlas|textures)', text) and 'LavaVisual UI smoke complete' in text and 'LavaVisual audio regression passed' in text and 'LavaVisual badge marker on' in text and 'LavaVisual badge glyph ok' in text and 'LavaVisual hat sync self-test passed' in text and 'LavaVisual hats ready' in text and 'LavaVisual accessory physics ok' in text and 'LavaVisual menu restore page 9' in text and 'LavaVisual smoke dummy ok' in text and 'LavaVisual smoke hand editor ok' in text and 'LavaVisual smoke zoom ok' in text and 'LavaVisual smoke freelook ok' in text and 'LavaVisual smoke minimap fast' in text and 'LavaVisual smoke custom sounds ok' in text and 'LavaVisual smoke music ok' in text and 'LavaVisual smoke formats ok' in text and 'LavaVisual smoke minimap fps ok' in text and 'LavaVisual smoke wings editor ok' in text and 'LavaVisual smoke time ok' in text and 'LavaVisual smoke item physics ok' in text and 'LavaVisual smoke projectile trails ok' in text and 'LavaVisual smoke outfit ok' in text and 'LavaVisual smoke trail clear ok' in text and 'LavaVisual smoke cape cloth ok' in text and 'LavaVisual title screen replaced=true' in text and 'LavaVisual title screen ready' in text:
                ready_since = ready_since or time.monotonic()
                if time.monotonic() - ready_since >= 12:
                    capture(project / 'build' / 'ui-smoke.png')
                    reload = re.search(r'LavaVisual smoke resource reload ok: ([0-9]+) ms', text)
                    if reload:
                        print(f'::notice title=Resource reload::{reload.group(1)} ms')
                    print('Client startup smoke passed; in-world visual correctness is NOT asserted.')
                    result = 0
                    break
            time.sleep(0.5)
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
# Every in-world check with its numbers, pass or fail, readable through the Checks API.
checks = [line for line in log.read_text(errors='replace').splitlines() if re.search(r'LavaVisual smoke (?!shot)', line)]
if checks:
    print('::notice title=Smoke checks::' + '\n'.join(line[-400:] for line in checks[-40:]).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
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
