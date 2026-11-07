#!/usr/bin/env bash
# Runs inside the emulator step. Each phase is its own steps file in .github/amazon/.
set -u
export OUT=/tmp/amz-out; mkdir -p $OUT
D=.github/amazon
PKG=$(cat $D/package.txt)
adb wait-for-device; adb shell getprop ro.build.version.release | sed 's/^/android /' | tee $OUT/device.txt
adb shell pm list packages | grep -iE "google|gms|vending|amazon" | tee -a $OUT/device.txt
if [ -f /tmp/apptester.apk ] && [ -f $D/amazon.sdktester.json ]; then
  adb install -r -g /tmp/apptester.apk | tee $OUT/apptester-install.txt
  adb shell appops set com.amazon.sdktestclient SYSTEM_ALERT_WINDOW allow || true
  adb push $D/amazon.sdktester.json /sdcard/amazon.sdktester.json
  adb shell setprop debug.amazon.sandboxmode debug
  echo "sandbox: $(adb shell getprop debug.amazon.sandboxmode)" | tee -a $OUT/device.txt
fi
# 1) debug build: the full walk-through (App Tester answers IAP calls only for debug builds)
bash $D/amazon-test.sh amz-apk/*-amazon-debug.apk "$PKG" $D/steps-debug.txt debug || true
# 2) release build (R8/signing as shipped): launch + main screens
bash $D/amazon-test.sh amz-apk/*-amazon-release.apk "$PKG" $D/steps-release.txt release || true
ls -la $OUT | tail -60
cat $OUT/*-fatal.txt 2>/dev/null | head -40
