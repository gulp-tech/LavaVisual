#!/usr/bin/env bash
# Installs the app on the running emulator, walks through the setup screen, the badge and the menu, and keeps the
# screenshots. Fails if the app crashes or a window never appears.
set -euo pipefail

PKG=tech.gulp.lavavisual.app
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SHOTS="$ROOT/build/shots"
mkdir -p "$SHOTS"
exec > >(tee "$SHOTS/log.txt") 2>&1
set -x

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$SHOTS/$1.png"; echo "shot $1"; }

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb install -r -g "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

adb shell am start -W -n $PKG/.MainActivity
sleep 3
adb shell input keyevent KEYCODE_BACK   # any permission dialog
shot 1-setup 3

# The overlay: badge plus HUD, then the menu.
adb shell am start -n $PKG/.MainActivity --ez autostart true
shot 2-badge 4
adb shell am start -n $PKG/.MainActivity --ez menu true
shot 3-menu 4

# Every section of the menu, tapped through by hand.
size=$(adb shell wm size | tr -d '\r' | awk '{print $3}')
width=${size%x*}
height=${size#*x}
tab_y=$(python3 -c "print(int($height * 0.145))")
for index in 1 2 3; do
  x=$(python3 -c "print(int($width * (0.145 + 0.235 * $index)))")
  adb shell input tap "$x" "$tab_y"
  shot "4-menu-tab$index" 2
done

adb shell input keyevent KEYCODE_BACK
shot 5-badge-only 3

# The app picker.
adb shell am start -n $PKG/.MainActivity
sleep 3
adb shell am start -n $PKG/.MainActivity --ez picker true
shot 6-apps 4
adb shell input keyevent KEYCODE_BACK

# Nothing crashed and the overlay is still alive.
if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  echo "the app crashed:"
  adb logcat -d | grep -A 25 "FATAL EXCEPTION" | head -60
  exit 1
fi
adb shell pidof $PKG > /dev/null || { echo "the overlay service is not running"; exit 1; }
adb shell dumpsys window windows | grep -q "$PKG" || { echo "no overlay window"; exit 1; }
echo "LavaVisual app ok"
