#!/usr/bin/env bash
# Preserve the exit status while exposing diagnostics through the GitHub Checks API.
set +e
"$@" > ci-command.log 2>&1
result=$?
cat ci-command.log
if [ "$result" -ne 0 ]; then
  python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
for report in Path('tools/bundle-test/build/test-results').rglob('*.xml'):
    for failure in ET.parse(report).iter('failure'):
        text = failure.text or failure.attrib.get('message', '')
        print('::error title=Test failure::' + text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
lines = Path('ci-command.log').read_text(errors='replace').splitlines()[-150:]
for i in range(0, len(lines), 15):
    message = '\n'.join(lines[i:i+15]).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print('::error title=Build diagnostics::' + message)
PY
fi
exit "$result"
