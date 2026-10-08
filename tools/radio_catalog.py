#!/usr/bin/env python3
"""Lists the Russian internet stations of the radio-browser.info directory and checks that each stream really plays.

Usage: radio_catalog.py OUT.tsv [countrycode]

Every line of the output is: state, name, codec, bitrate, votes, tags, url, ok (1 when the stream answered with audio
and the MP3 frame sync is there). Only MP3 streams can be played by the mod, so AAC is marked but not checked as MP3.
Runs in CI, where the internet is open; the result is uploaded as a workflow artifact.
"""
import concurrent.futures
import json
import sys
import urllib.request

API = ['https://all.api.radio-browser.info', 'https://de1.api.radio-browser.info', 'https://de2.api.radio-browser.info',
       'https://nl1.api.radio-browser.info', 'https://fi1.api.radio-browser.info']
UA = 'LavaVisual (Minecraft mod) radio check'


def directory(country):
    for server in API:
        try:
            request = urllib.request.Request(f'{server}/json/stations/bycountrycodeexact/{country}?hidebroken=true',
                                             headers={'User-Agent': UA, 'Accept': 'application/json'})
            with urllib.request.urlopen(request, timeout=40) as response:
                return json.loads(response.read().decode('utf-8'))
        except (OSError, ValueError) as failure:
            print('directory', server, repr(failure), file=sys.stderr, flush=True)
    raise SystemExit('radio-browser is not reachable')


def probe(url):
    """True when the address answers 200 with audio and the first bytes look like MP3 (ID3 tag or frame sync)."""
    try:
        request = urllib.request.Request(url, headers={'User-Agent': UA, 'Icy-MetaData': '0', 'Accept': '*/*'})
        with urllib.request.urlopen(request, timeout=8) as response:
            if response.status != 200:
                return False
            kind = response.headers.get('Content-Type', '')
            if 'html' in kind or 'json' in kind or 'text/plain' in kind:
                return False
            data = response.read(8192)
            if len(data) < 512:
                return False
            if data[:3] == b'ID3':
                return True
            for i in range(len(data) - 1):
                if data[i] == 0xFF and (data[i + 1] & 0xE0) == 0xE0:
                    return True
            return False
    except (OSError, ValueError):
        return False


def clean(text):
    return ' '.join(str(text or '').replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').split())


def main():
    out = sys.argv[1]
    country = sys.argv[2] if len(sys.argv) > 2 else 'RU'
    stations = directory(country)
    rows = []
    for station in stations:
        url = station.get('url_resolved') or station.get('url') or ''
        if not url.startswith(('http://', 'https://')):
            continue
        rows.append((clean(station.get('state')), clean(station.get('name')), clean(station.get('codec')),
                     str(station.get('bitrate') or 0), str(station.get('votes') or 0), clean(station.get('tags')), url))
    print(f'{len(rows)} stations, checking', file=sys.stderr)
    checks = {}
    mp3 = [row[6] for row in rows if row[2].upper() == 'MP3']
    with concurrent.futures.ThreadPoolExecutor(max_workers=48) as pool:
        for url, ok in zip(mp3, pool.map(probe, mp3)):
            checks[url] = ok
    with open(out, 'w', encoding='utf-8') as file:
        file.write('state\tname\tcodec\tbitrate\tvotes\ttags\turl\tok\n')
        for row in sorted(rows, key=lambda r: (r[0], -int(r[4]))):
            ok = checks.get(row[6])
            mark = '' if ok is None else ('1' if ok else '0')
            file.write('\t'.join(row + (mark,)) + '\n')
    print(f'wrote {out}: mp3 {len(mp3)}, playing {sum(1 for v in checks.values() if v)}', file=sys.stderr)


if __name__ == '__main__':
    main()
