#!/usr/bin/env python3
"""Builds the LavaVisual Client packages next to the mod jar.

  LavaVisual-Client-<version>-mc<mc>.mrpack  Modrinth modpack (Modrinth App, Prism Launcher, ATLauncher): Fabric Loader,
                                             Fabric API (downloaded by the launcher from Modrinth), LavaVisual and the
                                             client marker config/lavavisual-client.json.
  LavaVisual-Client-<version>-mc<mc>.zip     the same files as folders (mods/, config/) for TLauncher or a manual install.

Usage: make_client.py <lavavisual jar> <output dir> [fabric-api jar]. Versions come from ports/mc26.2/gradle.properties.
"""
import hashlib
import json
import sys
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MARKER = json.dumps({"edition": "client", "about": "LavaVisual Client: window title and the LavaVisual main menu."}, ensure_ascii=False, indent=2) + "\n"
INSTALL = """LavaVisual Client {version} для Minecraft {mc}

Лаунчер с модпаками (Modrinth App, Prism Launcher, ATLauncher)
  Импортируйте файл LavaVisual-Client-{version}-mc{mc}.mrpack: лаунчер сам поставит Fabric Loader {loader} и Fabric API.

TLauncher или обычный лаунчер
  1. Установите Minecraft {mc} с Fabric Loader {loader} (в TLauncher: версия «Fabric {mc}»).
  2. Откройте папку игры (.minecraft) и скопируйте туда папки mods и config из этого архива.
  3. Запустите версию Fabric {mc}.

Меню LavaVisual открывается клавишей Right Shift. Только мод, без клиента: lavavisual-{version}-mc{mc}.jar в папку mods.
Fabric API распространяется по лицензии Apache-2.0 (licenses/fabric-api-LICENSE.txt).
"""


def properties():
    values = {}
    for line in (ROOT / "ports/mc26.2/gradle.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def fetch(url, timeout=60):
    request = urllib.request.Request(url, headers={"User-Agent": "gulp-tech/LavaVisual (client packaging)"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read()


def modrinth_fabric_api(version, mc):
    """The Fabric API file on Modrinth: url, hashes and size (None if the lookup fails)."""
    try:
        query = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(["fabric"])})
        for entry in json.loads(fetch("https://api.modrinth.com/v2/project/P7dR8mSH/version?" + query)):
            if entry.get("version_number") == version:
                file = next((f for f in entry["files"] if f.get("primary")), entry["files"][0])
                return file
    except Exception as error:  # network or API change: fall back to embedding the jar
        print("Modrinth lookup failed:", error)
    return None


def main():
    jar, out = Path(sys.argv[1]), Path(sys.argv[2])
    props = properties()
    mc, loader, api = props["minecraft_version"], props["loader_version"], props["fabric_version"]
    version = props["mod_version"].split("-mc")[0]
    local = Path(sys.argv[3]) if len(sys.argv) > 3 else None
    remote = modrinth_fabric_api(api, mc)
    if local and local.is_file():
        # The jar the client tests ran with; Modrinth only provides the download link if it is the very same file.
        api_jar = local.read_bytes()
        if remote and hashlib.sha1(api_jar).hexdigest() != remote["hashes"]["sha1"]:
            print("Modrinth Fabric API differs from the tested jar: embedding the tested one")
            remote = None
    elif remote:
        api_jar = fetch(remote["url"], timeout=120)
        assert hashlib.sha1(api_jar).hexdigest() == remote["hashes"]["sha1"], "Fabric API download does not match Modrinth"
    else:
        api_jar = fetch(f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{api}/fabric-api-{api}.jar", timeout=120)
    api_name = f"fabric-api-{api}.jar"
    with zipfile.ZipFile(__import__("io").BytesIO(api_jar)) as fabric:
        licence = next((fabric.read(n) for n in fabric.namelist() if n.upper().startswith("LICENSE")), None)
    licence = licence or b"Fabric API is licensed under the Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0\n"

    base = f"LavaVisual-Client-{version}-mc{mc}"
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": version,
        "name": "LavaVisual Client",
        "summary": f"Minecraft {mc} с LavaVisual: HUD, эффекты, косметика и своё главное меню",
        "files": [],
        "dependencies": {"minecraft": mc, "fabric-loader": loader},
    }
    if remote:
        index["files"].append({"path": f"mods/{api_name}", "hashes": {"sha1": remote["hashes"]["sha1"], "sha512": remote["hashes"]["sha512"]},
                               "env": {"client": "required", "server": "unsupported"}, "downloads": [remote["url"]], "fileSize": remote["size"]})
    out.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out / f"{base}.mrpack", "w", zipfile.ZIP_DEFLATED) as pack:
        pack.writestr("modrinth.index.json", json.dumps(index, ensure_ascii=False, indent=2))
        pack.write(jar, f"overrides/mods/{jar.name}")
        if not remote:
            pack.writestr(f"overrides/mods/{api_name}", api_jar)
            pack.writestr("overrides/licenses/fabric-api-LICENSE.txt", licence)
        pack.writestr("overrides/config/lavavisual-client.json", MARKER)
    with zipfile.ZipFile(out / f"{base}.zip", "w", zipfile.ZIP_DEFLATED) as bundle:
        bundle.write(jar, f"mods/{jar.name}")
        bundle.writestr(f"mods/{api_name}", api_jar)
        bundle.writestr("config/lavavisual-client.json", MARKER)
        bundle.writestr("licenses/fabric-api-LICENSE.txt", licence)
        bundle.writestr("УСТАНОВКА.txt", INSTALL.format(version=version, mc=mc, loader=loader))
    # Self-check: both packages open and carry the mod, Fabric API and the marker.
    with zipfile.ZipFile(out / f"{base}.mrpack") as pack:
        meta = json.loads(pack.read("modrinth.index.json"))
        assert meta["dependencies"] == {"minecraft": mc, "fabric-loader": loader}
        assert f"overrides/mods/{jar.name}" in pack.namelist() and "overrides/config/lavavisual-client.json" in pack.namelist()
        assert meta["files"] or f"overrides/mods/{api_name}" in pack.namelist()
    with zipfile.ZipFile(out / f"{base}.zip") as bundle:
        assert {f"mods/{jar.name}", f"mods/{api_name}", "config/lavavisual-client.json"} <= set(bundle.namelist())
    print(f"{base}: mrpack + zip, Fabric API {api} {'from Modrinth' if remote else 'embedded'}")


if __name__ == "__main__":
    main()
