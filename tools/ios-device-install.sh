#!/bin/sh
# Build the iOS release and install it on a phone over the network, signed by
# Xcode itself with the household's free Apple ID (the profile lasts 7 days;
# rerun to renew).
#
#   tools/ios-device-install.sh             every phone in ios-devices
#   tools/ios-device-install.sh <udid> [bundle id]    one phone
#
# `ios-devices` (gitignored: UDIDs and team ids are the household's) holds one
# `<udid>  [bundle id]  # name` per line; the UDID is `xcrun devicectl list devices`'s.
# The bundle id is the one AltStore installed the app under (`<id>.<team id>`,
# `xcrun devicectl device info apps`): built with it, this install replaces
# AltStore's copy and keeps its data instead of adding a second app, which a
# free Apple ID's three-app limit refuses. Left out, the project's own id is used.
set -eu

here=$(cd "$(dirname "$0")/.." && pwd)
. "$here/env.sh" >/dev/null 2>&1 || exit 1

derived=/Volumes/Data2/dev/dd-r1control-device
mkdir -p "$here/build"

install_one() {
    device=$1
    bundle=${2:-}
    log=$here/build/ios-device-install-$device.log
    echo "==> $(date '+%F %T') $device: building"
    xcodebuild -project "$here/iosApp/iosApp.xcodeproj" -scheme iosApp \
        -configuration Release -destination "id=$device" \
        -derivedDataPath "$derived" -allowProvisioningUpdates \
        ${bundle:+PRODUCT_BUNDLE_IDENTIFIER=$bundle} build \
        >"$log" 2>&1 || { tail -20 "$log"; return 1; }
    app=$(ls -d "$derived"/Build/Products/Release-iphoneos/*.app | head -1)
    echo "==> $(date '+%F %T') $device: installing $(basename "$app")"
    xcrun devicectl device install app --device "$device" "$app" >>"$log" 2>&1 \
        || { tail -20 "$log"; return 1; }
    expires=$(security cms -D -i "$app/embedded.mobileprovision" 2>/dev/null \
        | plutil -extract ExpirationDate raw -o - - 2>/dev/null || echo unknown)
    version=$(plutil -extract CFBundleShortVersionString raw -o - "$app/Info.plist" 2>/dev/null || echo "?")
    echo "==> $device: installed $version, signed until $expires"
}

if [ $# -gt 0 ]; then install_one "$@"; exit; fi
[ -f "$here/ios-devices" ] || { echo "no ios-devices file, see the top of this script"; exit 1; }
grep -v '^[[:space:]]*#' "$here/ios-devices" | while read -r line; do
    fields=${line%%#*}
    # shellcheck disable=SC2086
    set -- $fields
    [ $# -gt 0 ] && install_one "$@"
done
