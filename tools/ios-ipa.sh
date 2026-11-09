#!/bin/sh
# Build the iOS release and package it as an IPA for AltStore or Sideloadly.
#
# Packaged by hand, as in mytube-app, not with `xcodebuild -exportArchive`: the
# household's Apple ID is a free one, so an exported profile lasts seven days,
# and the installer re-signs the app with the Apple ID on the phone anyway.
set -eu

here=$(cd "$(dirname "$0")/.." && pwd)
. "$here/env.sh" >/dev/null 2>&1 || true

out=${1:-$here/dist}
work=${TMPDIR:-/tmp}/r1control-ios-ipa.$$
archive=$work/R1Control.xcarchive

mkdir -p "$work" "$out"
trap 'rm -rf "$work"' EXIT

echo "==> archiving"
xcodebuild \
    -project "$here/iosApp/iosApp.xcodeproj" \
    -scheme iosApp \
    -configuration Release \
    -destination 'generic/platform=iOS' \
    -derivedDataPath "$work/dd" \
    -allowProvisioningUpdates \
    -archivePath "$archive" \
    archive \
    >"$work/archive.log" 2>&1 \
  || { tail -40 "$work/archive.log"; exit 1; }

app=$archive/Products/Applications/R1Control.app
[ -d "$app" ] || { echo "no R1Control.app in the archive"; exit 1; }

echo "==> packaging"
mkdir -p "$work/Payload"
cp -R "$app" "$work/Payload/"
# The profile carries the UDID of every registered phone and the account holder's
# name in plain text, and this IPA goes on a public release. The installer re-signs.
rm -rf "$work/Payload/R1Control.app/embedded.mobileprovision" \
       "$work/Payload/R1Control.app/_CodeSignature"
(cd "$work" && zip -qry "$out/R1Control.ipa" Payload)

ls -lh "$out/R1Control.ipa"
