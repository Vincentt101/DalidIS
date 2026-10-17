#!/usr/bin/env bash
# Builds NetPrime.apk without the Android SDK.
# Needs: Java 17+, Python 3 with Pillow, curl.
set -euo pipefail
cd "$(dirname "$0")"
T=build/tools; mkdir -p "$T" build
get() { [ -f "$T/$1" ] || curl -sSL -o "$T/$1" "$2"; }
get apktool.jar https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar
get dx.jar https://repo1.maven.org/maven2/com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar
get android.jar https://repo1.maven.org/maven2/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar
get signer.jar https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar
get hosts.txt https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts

# Assemble the app folder that apktool packs.
P=build/pkg; rm -rf "$P" build/classes; mkdir -p "$P/assets" build/classes
cp AndroidManifest.xml apktool.yml "$P/"; cp -r res "$P/"
python3 tools/make_images.py "$P"
python3 tools/make_blocklist.py "$T/hosts.txt" "$P/assets/adhosts.txt"

# Compile Java -> classes.dex
javac -nowarn --release 8 -cp "$T/android.jar" -d build/classes $(find src -name '*.java')
java -cp "$T/dx.jar" com.android.dx.command.Main --dex --min-sdk-version=21 --output="$P/classes.dex" build/classes

# Pack, align and sign
java -jar "$T/apktool.jar" b -o build/unsigned.apk "$P"
rm -rf build/signed
java -jar "$T/signer.jar" -a build/unsigned.apk -o build/signed
mkdir -p ../apk && cp build/signed/*.apk ../apk/NetPrime.apk
echo "Built ../apk/NetPrime.apk"
