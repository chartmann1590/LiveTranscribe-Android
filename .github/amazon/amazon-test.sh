#!/usr/bin/env bash
# Amazon-flavor smoke test on a CI emulator (no Google Play services, like a Fire device).
# usage: amazon-test.sh <apk> <package> <steps-file> <label>
# Writes screenshots, UI dumps and logcat to $OUT and uploads them to Tab's private test drop.
set -u
exec </dev/null  # adb shell reads stdin; the steps file is on fd 3
APK="$1"; PKG="$2"; STEPS="$3"; LABEL="$4"
OUT="${OUT:-/tmp/amz-out}"; mkdir -p "$OUT"
log(){ echo "[$(date +%H:%M:%S)] $*" | tee -a "$OUT/steps.log"; }
dump(){ adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null; }
nodes(){ perl -ne 'while(/<node ([^>]*)>/g){my $a=$1; my ($t)=$a=~/ text="([^"]*)"/; my ($d)=$a=~/content-desc="([^"]*)"/; my ($c)=$a=~/clickable="(\w+)"/; my ($p)=$a=~/package="([^"]*)"/; my ($b)=$a=~/bounds="([^"]*)"/; print "$p|$t|$d|$c|$b\n" if ($t ne "" || $d ne "");}' "$OUT/ui.xml"; }
tapre(){ dump; xy=$(perl -se 'local $/; $_=<STDIN>; while(/<node ([^>]*)>/g){my $a=$1; my ($t)=$a=~/ text="([^"]*)"/; my ($d)=$a=~/content-desc="([^"]*)"/; if(($t ne "" && $t=~/$re/)||($d ne "" && $d=~/$re/)){my ($x1,$y1,$x2,$y2)=$a=~/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/; print int(($x1+$x2)/2)," ",int(($y1+$y2)/2); exit}}' -- -re="$1" < "$OUT/ui.xml"); if [ -n "$xy" ]; then adb shell input tap $xy; log "tapped /$1/ at $xy"; return 0; else log "not found /$1/"; return 1; fi; }
shot(){ adb exec-out screencap -p > "$OUT/$LABEL-$1.png"; dump; cp "$OUT/ui.xml" "$OUT/$LABEL-$1.ui.xml"; nodes > "$OUT/$LABEL-$1.txt"; log "shot $1 (fg: $(adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | tr -s ' '))"; }
adb logcat -c || true
WM=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); WM_W=${WM%x*}; WM_H=${WM#*x}; log "screen ${WM_W}x${WM_H}"
X50=$((WM_W/2)); Y85=$((WM_H*85/100)); Y30=$((WM_H*30/100)); X90=$((WM_W*9/10)); X10=$((WM_W/10)); Y50=$((WM_H/2))
log "install $APK"
adb install -r -g "$APK" 2>&1 | tee -a "$OUT/steps.log"
adb shell settings put global window_animation_scale 0 </dev/null >/dev/null 2>&1 || true
sleep 15  # let the freshly booted emulator settle
while IFS= read -r -u 3 line || [ -n "$line" ]; do
  line="${line%%#*}"; line="$(echo "$line" | sed -e 's/[[:space:]]*$//')"; [ -z "$line" ] && continue
  cmd="${line%% *}"; arg="${line#* }"; [ "$cmd" = "$line" ] && arg=""
  case "$cmd" in
    launch) adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; log "launch";;
    stop) adb shell am force-stop "$PKG"; log "force-stop";;
    wait) sleep "$arg";;
    shot) shot "$arg";;
    tap) tapre "$arg" || true; sleep 2;;
    taploop) for i in 1 2 3 4 5 6 7 8; do tapre "$arg" || break; sleep 2; done;;
    adclose) for i in 1 2 3 4; do dump; if grep -qiE 'Test Ad|AdActivity|interstitial|com.google.android.gms.ads' "$OUT/ui.xml" || adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | grep -qi 'AdActivity'; then log "ad on screen (try $i)"; sleep 8; tapre "(?i)^(close|close ad|skip ad|skip video|dismiss|interstitial close button)$" || adb shell input tap $((WM_W*96/100)) $((WM_H*3/100)); sleep 3; else break; fi; done;;
    scrollto) found=0; for i in 1 2 3 4 5 6 7 8 9 10 11 12; do if tapre "$arg"; then found=1; break; fi; adb shell input swipe $X50 $Y85 $X50 $Y30 400; sleep 1; done; [ $found = 0 ] && log "scrollto: never found /$arg/"; sleep 2;;
    tapxy) set -- $arg; adb shell input tap $((WM_W*$1/1000)) $((WM_H*$2/1000)); log "tapxy permille $arg"; sleep 2;;
    type) adb shell input text "$arg"; log "type"; sleep 1;;
    back) adb shell input keyevent 4; log "back"; sleep 2;;
    home) adb shell input keyevent 3; log "home"; sleep 1;;
    swipeup) adb shell input swipe $X50 $Y85 $X50 $Y30 300; sleep 1;;
    swipedown) adb shell input swipe $X50 $Y30 $X50 $Y85 300; sleep 1;;
    swipeleft) adb shell input swipe $X90 $Y50 $X10 $Y50 250; sleep 1;;
    am) adb shell am $arg; log "am $arg"; sleep 3;;
    sh) adb shell "$arg" 2>&1 | tee -a "$OUT/steps.log";;
    *) log "unknown step: $line";;
  esac
done 3< "$STEPS"
adb logcat -d > "$OUT/$LABEL-logcat.txt" 2>/dev/null || true
adb logcat -d -b crash > "$OUT/$LABEL-crash.txt" 2>/dev/null || true
PID_NOTE=$(adb shell pidof "$PKG" || true); log "pidof $PKG: ${PID_NOTE:-not running}"
grep -E "FATAL EXCEPTION|AndroidRuntime: Process: $PKG|ANR in $PKG" "$OUT/$LABEL-logcat.txt" > "$OUT/$LABEL-fatal.txt" || true
grep -iE "AmazonBilling|AmazonIap|com.amazon.device.iap|sdktestclient|Appstore|IAP" "$OUT/$LABEL-logcat.txt" | head -300 > "$OUT/$LABEL-iap.txt" || true
log "fatal lines: $(wc -l < "$OUT/$LABEL-fatal.txt") crash-buffer lines: $(grep -c "$PKG" "$OUT/$LABEL-crash.txt" || true)"
adb uninstall "$PKG" >/dev/null 2>&1 || true
