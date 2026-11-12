#!/bin/bash
# Installs the app, opens each tab and saves a screenshot of it, plus the app's log.
set -x
mkdir -p shots
adb install -r app-release.apk
adb logcat -c
P=com.zivi.wxnews/.ui.MainActivity
names=(maldives world flying settings)
for i in 0 1 2 3; do
  adb shell am start -S -n $P --ei tab $i
  sleep 40
  adb exec-out screencap -p > shots/$i-${names[$i]}.png
  # Scroll down once for a second view of the long tabs.
  if [ $i -lt 3 ]; then
    adb shell input swipe 540 1600 540 500 400
    sleep 3
    adb exec-out screencap -p > shots/$i-${names[$i]}-2.png
  fi
done
adb logcat -d > shots/logcat.txt
grep -E "AndroidRuntime|FATAL|WxNews" shots/logcat.txt | head -100 > shots/errors.txt || true
exit 0
