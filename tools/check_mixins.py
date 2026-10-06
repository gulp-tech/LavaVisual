#!/usr/bin/env python3
"""Checks that every mixin target really exists in this Minecraft version.

The development run uses named classes, so a wrong package inside a descriptor still starts there, but the released jar
is remapped and the injection then fails at startup ("could not find any targets matching ..."). This check reads the
targets straight out of the sources and compares them with javap on the game classpath: the methods written with a full
descriptor, and the fields a @Shadow stands for. A shadow written with a source name that this version does not have
would only show up when the game loads the class, which in the released jar means a crash for the player.

Usage: check_mixins.py <port dir> <classpath>
"""
import re
import subprocess
import sys
from pathlib import Path

METHOD = re.compile(r'method\s*=\s*"([A-Za-z0-9_$<>]+)(\([^"]*\)[^"]*)"')
TARGET = re.compile(r'@Mixin\(\s*([A-Za-z0-9_.]+)\.class')
ANNOTATION = re.compile(r'@[A-Za-z0-9_$.]+(?:\([^)]*\))?')
HEADER = re.compile(r'^\s*(?:public |protected |private |abstract |final |static |sealed |non-sealed )*'
                    r'(?:class|interface|enum|record) ([A-Za-z0-9_.$]+)(?:<[^>]*>)?(?: extends ([A-Za-z0-9_.$]+))?')


def shadowed_fields(source):
    """Names of the fields the @Shadow declarations of this file stand for."""
    names = []
    for line in source.splitlines():
        if '@Shadow' not in line or 'class' in line:
            continue
        text = ANNOTATION.sub(' ', line.split(';')[0])
        words = [w for w in text.split() if w not in ('final', 'static')]
        if words:
            names.append(words[-1])
    return names


def class_members(cls, classpath, cache={}):
    """Fields declared by the class and the class it extends, as javap sees them (name -> descriptor)."""
    if cls in cache:
        return cache[cls]
    run = subprocess.run(["javap", "-p", "-s", "-classpath", classpath, cls], capture_output=True, text=True)
    if run.returncode != 0:
        cache[cls] = (None, None)
        return cache[cls]
    parent, fields, pending = None, {}, None
    for line in run.stdout.splitlines():
        header = HEADER.match(line)
        if header and header.group(1) == cls:
            parent = header.group(2)
        stripped = line.strip()
        if stripped.startswith("descriptor: ") and pending:
            fields[pending] = stripped[len("descriptor: "):]
            pending = None
            continue
        if line.startswith("  ") and stripped.endswith(";") and "(" not in stripped:
            pending = stripped[:-1].split()[-1]
            continue
        if stripped and not stripped.startswith(("descriptor", "Compiled")):
            pending = None
    cache[cls] = (parent, fields)
    return cache[cls]


def has_field(cls, field, classpath, cache={}):
    """Where the field lives, False when the whole chain is readable and none of it has it, None when unreadable."""
    seen = []
    while cls:
        parent, fields = cache[cls] if cls in cache else class_members(cls, classpath, cache)
        if fields is None:
            return None
        if field in fields:
            return ' <- '.join(seen + [cls])
        seen.append(cls)
        cls = parent
    return False


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
    fields_checked = 0
    field_cache = {}
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
        for field in shadowed_fields(source):
            fields_checked += 1
            where = has_field(cls, field, classpath, field_cache)
            if where is False:
                problems.append(f"{file.name}: @Shadow {field} is not on {cls}; the mixin would fail when the class loads")
            elif where is None:
                print(f"{file.name}: cannot read the class chain of {cls} for @Shadow {field}", flush=True)
        for name, descriptor in METHOD.findall(source):
            checked += 1
            if (name, descriptor) not in known:
                close = sorted(d for n, d in known if n == name)
                problems.append(f"{file.name}: {cls}#{name}{descriptor} does not exist; this version has: "
                                + (", ".join(close) if close else "no method with that name"))
    print(f"mixin descriptors checked: {checked}, shadow fields checked: {fields_checked}")
    for problem in problems:
        print("MIXIN PROBLEM:", problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
