#!/bin/bash
# Builds Seek without Gradle: aapt2 → javac → dx → zipalign → apksigner.
# Needs: apt install android-sdk-build-tools aapt dalvik-exchange apksigner zipalign, and
# $HOME/android/all.jar = org.robolectric:android-all:14-robolectric-10818077 from Maven Central (API 34 classes + framework resources).
set -euo pipefail
cd "$(dirname "$0")"
BT=/usr/lib/android-sdk/build-tools/debian
JAR=$HOME/android/all.jar
rm -rf out && mkdir -p out/res out/gen out/classes
aapt2 compile --dir res -o out/res/res.zip
aapt2 link -I "$JAR" --manifest AndroidManifest.xml --min-sdk-version 23 --target-sdk-version 34 \
  --version-code 4 --version-name 1.3 --java out/gen -o out/base.apk out/res/res.zip --auto-add-overlay -A assets
javac -nowarn --release 8 -cp "$JAR" -d out/classes $(find src out/gen -name '*.java') 2>&1 | grep -v "^warning\|^Note\|bootstrap" || true
ls out/classes/app/seek/Main.class >/dev/null
$BT/dx --dex --min-sdk-version=23 --output=out/classes.dex out/classes
cp out/base.apk out/unsigned.apk
(cd out && zip -q -j unsigned.apk classes.dex)
zipalign -f -p 4 out/unsigned.apk out/aligned.apk
[ -f seek.keystore ] || keytool -genkeypair -keystore seek.keystore -storepass "${SEEK_KS_PASS:-seekseek}" -keypass "${SEEK_KS_PASS:-seekseek}" -alias seek \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Seek" >/dev/null 2>&1
apksigner sign --ks seek.keystore --ks-pass pass:"${SEEK_KS_PASS:-seekseek}" --key-pass pass:"${SEEK_KS_PASS:-seekseek}" --out out/Seek.apk out/aligned.apk
apksigner verify --print-certs out/Seek.apk | head -2
ls -la out/Seek.apk
