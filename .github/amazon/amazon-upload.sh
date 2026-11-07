#!/usr/bin/env bash
# Upload test outputs to Tab's private test drop (key-protected, 48 h TTL). usage: amazon-upload.sh <dir> <prefix>
set -u
D="$1"; P="$2"; U=https://hs-apk-drop.charles-h-hartmann1.workers.dev
[ -z "${DROP_KEY:-}" ] && { echo "no DROP_KEY"; exit 0; }
cd "$D"; tar czf "/tmp/$P.tgz" .; ls -la "/tmp/$P.tgz"
split -b 20m -d -a 3 "/tmp/$P.tgz" "/tmp/$P.part."
n=0; for f in /tmp/$P.part.*; do curl -sf -m 120 -X PUT -H "X-Key: $DROP_KEY" --data-binary @"$f" "$U/c/$P/$n" >/dev/null && echo "up $n"; n=$((n+1)); done
sha=$(sha256sum "/tmp/$P.tgz" | cut -d' ' -f1)
echo "{\"chunks\":$n,\"sha256\":\"$sha\"}" | curl -sf -m 60 -X PUT -H "X-Key: $DROP_KEY" --data-binary @- "$U/c/$P.info/0"; echo
