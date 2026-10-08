#!/usr/bin/env python3
"""Checks every stream address of the city radio candidates (tools/radio_candidates.tsv).

Usage: radio_check.py CANDIDATES.tsv OUT.tsv

Writes each candidate line with an extra column: 1 when the address answers with audio (MP3 frame sync or ID3),
0 when it does not. Only the 1 rows go into the mod. Uses the same probe as radio_catalog.py.
"""
import concurrent.futures
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from radio_catalog import probe  # noqa: E402


def main():
    source, out = sys.argv[1], sys.argv[2]
    lines = open(source, encoding='utf-8').read().splitlines()
    rows = [line for line in lines if line.strip() and not line.startswith('#')]
    urls = sorted({row.split('\t')[-1].strip() for row in rows})
    with concurrent.futures.ThreadPoolExecutor(max_workers=24) as pool:
        results = dict(zip(urls, pool.map(probe, urls)))
    with open(out, 'w', encoding='utf-8') as file:
        for line in lines:
            if not line.strip():
                continue
            if line.startswith('#'):
                file.write(line + '\n')
                continue
            url = line.split('\t')[-1].strip()
            file.write(line + '\t' + ('1' if results[url] else '0') + '\n')
    working = sum(1 for v in results.values() if v)
    print(f'checked {len(urls)} addresses, playing {working}', file=sys.stderr)


if __name__ == '__main__':
    main()
