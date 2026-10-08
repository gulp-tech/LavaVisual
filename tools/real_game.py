#!/usr/bin/env python3
"""Start the real game (vanilla + Fabric Loader + the published jar) and look at the main menu.

The development client runs with named mappings and never proves that the shipped, remapped jar works.
This script installs the game exactly the way a player does, drops the published jar into mods/, starts it
offline under a virtual display and fails when the menu is a black screen or the log carries a mixin error.
"""
import os
import re
import shutil
import signal
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

mc = sys.argv[1] if len(sys.argv) > 1 else '1.21.11'
root = Path(__file__).resolve().parent.parent
build = root / 'build'
build.mkdir(exist_ok=True)
log_path = build / f'real-{mc}.log'
shot = build / f'real-{mc}.png'
home = Path.home() / '.lavavisual-game' / mc
mods = home / 'mods'
mods.mkdir(parents=True, exist_ok=True)

# The jar of the version in the sources (mod_version, e.g. 1.0.0-mc1.21.11), not the newest file in artifacts/.
mod_version = next(line.split('=', 1)[1].strip() for line in (root / 'ports' / f'mc{mc}' / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
jar = root / 'artifacts' / f'lavavisual-{mod_version}.jar'
if not jar.exists():
    sys.exit(f'::error::no published jar for {mc}: {jar.name}')
shutil.copy(jar, mods / jar.name)

# Fabric API, the same build the mod is compiled against.
props = (root / 'ports' / f'mc{mc}' / 'gradle.properties').read_text()
api = re.search(r'^fabric_version=(.+)$', props, re.M).group(1)
api_jar = mods / f'fabric-api-{api}.jar'
if not api_jar.exists():
    url = f'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{api}/fabric-api-{api}.jar'
    print('fabric api:', url)
    urllib.request.urlretrieve(url, api_jar)
print('mods:', [p.name for p in mods.iterdir()])

(home / 'options.txt').write_text('guiScale:2\n')
env = {**os.environ, 'ALSOFT_DRIVERS': 'null'}
command = ['xvfb-run', '-a', '-s', '-screen 0 1280x800x24',
           'portablemc', '--main-dir', str(home), '--work-dir', str(home),
           'start', f'fabric:{mc}', '--username', 'Tester', '--uuid', '11111111-1111-1111-1111-111111111111']


def capture(target):
    """Grab the window of the Xvfb the game runs on."""
    for proc in sorted(Path('/proc').glob('[0-9]*'), key=lambda p: int(p.name), reverse=True):
        try:
            args = (proc / 'cmdline').read_bytes().decode().split('\0')
            if Path(args[0]).name != 'Xvfb' or '-auth' not in args:
                continue
            shell = {**os.environ, 'DISPLAY': args[1], 'XAUTHORITY': args[args.index('-auth') + 1]}
            if subprocess.run(['import', '-window', 'root', str(target)], env=shell, timeout=20).returncode == 0:
                return True
        except (OSError, ValueError, subprocess.TimeoutExpired):
            continue
    return False


result = 1
with log_path.open('w') as output:
    game = subprocess.Popen(command, cwd=home, stdout=output, stderr=subprocess.STDOUT,
                            start_new_session=True, env=env)
    try:
        deadline = time.monotonic() + 900
        menu_since = None
        while time.monotonic() < deadline:
            text = log_path.read_text(errors='replace')
            if game.poll() is not None and 'Stopping!' not in text:
                raise RuntimeError(f'the game exited on its own: {game.returncode}')
            for bad in ('InjectionError', 'InvalidMixinException', 'MixinApplyError', 'Mixin apply failed',
                        'Exception in thread "Render thread"', 'Reported exception thrown'):
                if bad in text:
                    raise RuntimeError(f'the game failed: {bad}')
            # "Created: ... atlas" is printed when the reload that precedes the main menu finishes.
            if re.search(r'Created:.*(atlas|textures)', text) or 'Backend library: LWJGL' in text:
                menu_since = menu_since or time.monotonic()
                if time.monotonic() - menu_since >= 45:
                    break
            time.sleep(1)
        else:
            raise RuntimeError('the game never reached the main menu')
        if not capture(shot):
            raise RuntimeError('could not take a screenshot of the game window')
        mean = subprocess.run(['identify', '-format', '%[fx:mean]', str(shot)],
                              capture_output=True, text=True, check=True).stdout.strip()
        colours = subprocess.run(['identify', '-format', '%k', str(shot)],
                                 capture_output=True, text=True, check=True).stdout.strip()
        print(f'main menu brightness {mean}, distinct colours {colours}')
        text = log_path.read_text(errors='replace')
        fell_back = 'falling back to the vanilla main menu' in text
        if float(mean) <= 0.02 or int(colours) < 50:
            raise RuntimeError(f'the real game shows a black screen (brightness {mean}, colours {colours})')
        if fell_back:
            raise RuntimeError('the custom menu failed and the watchdog had to fall back')
        print('The real game reaches a drawn main menu.')
        result = 0
    except Exception as error:  # noqa: BLE001 - the message is the CI report
        print(f'::error::{error}')
        capture(shot)
    finally:
        try:
            os.killpg(game.pid, signal.SIGTERM)
            game.wait(timeout=20)
        except ProcessLookupError:
            pass
        except subprocess.TimeoutExpired:
            os.killpg(game.pid, signal.SIGKILL)
            game.wait()

tail = log_path.read_text(errors='replace').splitlines()[-60:]
print('\n'.join(tail))
sys.exit(result)
