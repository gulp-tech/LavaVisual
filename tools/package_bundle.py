#!/usr/bin/env python3
"""Package exact-version adapters; never broaden their Minecraft dependency ranges."""
import argparse
import io
import json
from pathlib import Path
import zipfile

ADAPTERS = {
    '1.20.4': 'lavavisual-1.0.0+mc1.20.4.jar',
    '1.21.4': 'lavavisual-1.1.0+mc1.21.4.jar',
    '26.2': 'lavavisual-1.1.0+mc26.2.jar',
}


def package(directory: Path, output: Path):
    payloads = {}
    for mc, name in ADAPTERS.items():
        data = (directory / name).read_bytes()
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            assert jar.testzip() is None, name
            manifest = json.loads(jar.read('fabric.mod.json'))
            assert manifest['id'] == 'lavavisual', name
            assert manifest['depends']['minecraft'] == mc, name
            assert any(n.endswith('.class') for n in jar.namelist()), name
        payloads['META-INF/jars/' + name] = data
    manifest = {
        'schemaVersion': 1,
        'id': 'lavavisual_bundle',
        'version': '1.1.0',
        'name': 'LavaVisual Multi-Version',
        'description': 'Fabric selects one exact-version LavaVisual adapter: 1.20.4, 1.21.4 or 26.2.',
        'environment': 'client',
        'license': 'MIT',
        'authors': ['gulp-tech'],
        'depends': {'fabricloader': '>=0.19.5', 'java': '>=17',
                    'minecraft': list(ADAPTERS), 'lavavisual': '*'},
        'jars': [{'file': name} for name in payloads],
    }
    payloads['fabric.mod.json'] = (json.dumps(manifest, indent=2) + '\n').encode()
    payloads['LICENSE'] = Path('LICENSE').read_bytes()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as jar:
        for name, data in sorted(payloads.items()):
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            jar.writestr(info, data)
    print(f'Packaged {output}: {output.stat().st_size} bytes; exact targets {list(ADAPTERS)}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', type=Path, default=Path('artifacts'))
    parser.add_argument('--output', type=Path, default=Path('artifacts/lavavisual-1.1.0-multiversion.jar'))
    args = parser.parse_args()
    package(args.directory, args.output)
