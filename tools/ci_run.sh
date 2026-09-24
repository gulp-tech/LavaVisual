#!/usr/bin/env bash
# Preserve the exit status while exposing diagnostics through the GitHub Checks API.
set +e
"$@" > ci-command.log 2>&1
result=$?
cat ci-command.log
if [ "$result" -ne 0 ]; then
  python3 - <<'PY'
from pathlib import Path
lines = Path('ci-command.log').read_text(errors='replace').splitlines()[-150:]
for i in range(0, len(lines), 15):
    message = '\n'.join(lines[i:i+15]).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print('::error title=Build diagnostics::' + message)
PY
fi
exit "$result"
