#!/usr/bin/env python3
"""Parses every Java source of the ports and checks that the UI files only use members that exist.

The build of a port needs the Minecraft and Fabric jars, which are not always at hand (no network, no Gradle), but the
mistakes that bite while editing are local: a missing brace, a stray character, or a call to a helper of UiDraw,
UiFont, Icons or UiSound that was never written. This tool parses each file with the tree-sitter Java grammar, reports
every syntax error with its line, and then walks the UI files for `Class.member` references and fails when a member is
not declared in that class.

    python3 tools/check_java.py [port dir ... (default: every port)]

Needs `pip install tree-sitter tree-sitter-java`; it is a local tool, not part of CI (the CI build compiles for real).
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Members the UI code is allowed to reach for, per class; filled from the sources of the port being checked.
HELPERS = ('UiDraw', 'UiFont', 'Icons', 'UiSound', 'MenuTheme', 'UiButtons')


def port_dir(port):
    """A port may be named ('mc26.2'), given as a path ('ports/mc26.2') or given as an absolute path."""
    path = Path(port)
    if path.is_absolute():
        return path
    return ROOT / path if (ROOT / path).is_dir() else ROOT / 'ports' / path


def java_files(port):
    for folder in ('src/main/java', 'src/client/java', 'src/test/java'):
        base = port_dir(port) / folder
        if base.is_dir():
            yield from sorted(base.rglob('*.java'))


def parse_check(files):
    """Syntax: every file must parse with no error and no missing node."""
    import tree_sitter_java
    from tree_sitter import Language, Parser
    parser = Parser(Language(tree_sitter_java.language()))
    problems = []
    for path in files:
        tree = parser.parse(path.read_bytes())
        if not tree.root_node.has_error:
            continue
        for node in walk(tree.root_node):
            if node.type == 'ERROR' or node.is_missing:
                line = path.read_text(encoding='utf-8').splitlines()[node.start_point[0]] if node.start_point[0] == node.end_point[0] else ''
                problems.append(f"{path.relative_to(ROOT)}:{node.start_point[0] + 1}: {node.type}"
                                f"{f' -> {line.strip()[:90]}' if line else ''}")
    return problems


def walk(node):
    yield node
    for child in node.children:
        yield from walk(child)


def members(source):
    """Names a Java class offers: fields, methods, enum constants and nested type names."""
    names = set(re.findall(r'\b(?:public|protected|static|final|abstract|sealed|non-sealed|record|enum|class|interface)\b[^;{}=()]*?\b(\w+)\s*(?:[=(<]|$)', source))
    names |= set(re.findall(r'^\s*(?:public|protected|private)?\s*static\s+final\s+[\w<>\[\].]+\s+(\w+)\s*=', source, re.M))
    names |= set(re.findall(r'\b(?:public|protected)\s+(?:static\s+)?(?:final\s+)?[\w<>\[\], .]+\s+(\w+)\s*\(', source))
    names |= set(re.findall(r'^\s*([A-Z][A-Z0-9_]{2,})\s*\(', source, re.M))          # enum constants
    names |= set(re.findall(r'^\s*(?:public\s+)?(?:static\s+)?(?:final\s+)?(?:class|record|enum|interface)\s+(\w+)', source, re.M))
    return names


def usage_check(port, files):
    """`Helper.member` in the UI files must be declared by that helper (constants of a nested class count)."""
    sources = {}
    for path in files:
        stem = path.stem
        if stem in HELPERS:
            sources[stem] = path.read_text(encoding='utf-8')
    known = {name: members(source) for name, source in sources.items()}
    problems = []
    for path in files:
        if 'ui/' not in path.as_posix():
            continue
        text = re.sub(r'//[^\n]*', '', path.read_text(encoding='utf-8'))
        for helper, member in re.findall(r'\b(' + '|'.join(HELPERS) + r')\.(\w+)', text):
            if helper not in known:
                continue                                  # class of another port or not read yet: the build will say
            if helper == 'Icons' and member.isupper():
                continue                                  # icons are plain constants; the regex above may miss them
            if member not in known[helper]:
                problems.append(f"{path.relative_to(ROOT)}: {helper}.{member} is not declared in "
                                f"{helper}.java")
    return problems


def main():
    ports = sys.argv[1:] or [p.name for p in sorted((ROOT / 'ports').iterdir()) if (p / 'src').is_dir()]
    problems = []
    for port in ports:
        files = list(java_files(port))
        problems += parse_check(files)
        problems += usage_check(port, files)
    for problem in problems:
        print('JAVA PROBLEM:', problem)
    print(f"{len(ports)} port(s), {sum(1 for p in ports for _ in java_files(p))} files, {len(problems)} problems")
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
