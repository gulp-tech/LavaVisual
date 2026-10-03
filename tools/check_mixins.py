#!/usr/bin/env python3
"""Checks that every mixin target written with a full descriptor really exists in this Minecraft version.

The development run uses named classes, so a wrong package inside a descriptor still starts there, but the released
jar is remapped and the injection then fails at startup ("could not find any targets matching ..."). This check reads
the descriptors straight out of the sources and compares them with javap on the game classpath.

Usage: check_mixins.py <port dir> <classpath>
"""
import re
import subprocess
import sys
from pathlib import Path

METHOD = re.compile(r'method\s*=\s*"([A-Za-z0-9_$<>]+)(\([^"]*\)[^"]*)"')
TARGET = re.compile(r'@Mixin\(\s*([A-Za-z0-9_.]+)\.class')


def resolve(name, source):
    """Full class name for a simple name used in @Mixin, from the imports of the file."""
    if "." in name:
        return name
    found = re.search(r'^import\s+([a-z0-9_.]+\.' + re.escape(name) + r');', source, re.M)
    return found.group(1) if found else None


def descriptors(cls, classpath, cache={}):
    if cls not in cache:
        run = subprocess.run(["javap", "-p", "-s", "-classpath", classpath, cls], capture_output=True, text=True)
        if run.returncode != 0:
            # The reason matters: usually the class is simply not on this version's classpath.
            print(f"javap failed for {cls} (exit {run.returncode}): {(run.stderr or run.stdout).strip()[:400]}", flush=True)
        pairs = set()
        name = None
        for line in run.stdout.splitlines():
            line = line.strip()
            if line.startswith("descriptor: ") and name:
                pairs.add((name, line[len("descriptor: "):]))
                name = None
            else:
                found = re.search(r'([A-Za-z0-9_$<>]+)\s*\(', line)
                if found:
                    name = found.group(1)
                    if name in ("descriptor", "Compiled"):
                        name = None
        cache[cls] = pairs
    return cache[cls]


def main():
    port, classpath = Path(sys.argv[1]), sys.argv[2]
    problems = []
    checked = 0
    for file in sorted((port / "src/main/java/tech/gulp/lavavisual/mixin").glob("*.java")):
        source = file.read_text(encoding="utf-8")
        target = TARGET.search(source)
        if not target:
            continue
        cls = resolve(target.group(1), source)
        if not cls:
            problems.append(f"{file.name}: cannot resolve the target class {target.group(1)}")
            continue
        known = descriptors(cls, classpath)
        if not known:
            problems.append(f"{file.name}: javap found nothing for {cls}")
            continue
        for name, descriptor in METHOD.findall(source):
            checked += 1
            if (name, descriptor) not in known:
                close = sorted(d for n, d in known if n == name)
                problems.append(f"{file.name}: {cls}#{name}{descriptor} does not exist; this version has: "
                                + (", ".join(close) if close else "no method with that name"))
    print(f"mixin descriptors checked: {checked}")
    for problem in problems:
        print("MIXIN PROBLEM:", problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
