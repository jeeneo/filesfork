#!/usr/bin/sh bash

ANDROID_HOME="$HOME/android/sdk"

if [ -d "$ANDROID_HOME" ]; then
    echo "it seems you already have an SDK installed,"
    echo "running this script over an existing install can result in a broken SDK, exiting"
    echo "(try deleting your ~/android/sdk folder and removing any old .bashrc exports)"
    exit 1
fi

if ! ping -c 1 -W 5 1.1.1.1 > /dev/null 2>&1; then
    echo "no internet, exiting"
    exit 1
fi

mkdir -p "$ANDROID_HOME"

echo "export ANDROID_HOME=$ANDROID_HOME" >> "$HOME/.bashrc"
echo "export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk" >> "$HOME/.bashrc"
echo 'export GRADLE_OPTS="-Dorg.gradle.project.android.aapt2FromMavenOverride=${ANDROID_HOME}/build-tools/36.0.0/aapt2"' >> "$HOME/.bashrc"
source "$HOME/.bashrc"

if ! command -v bsdtar >/dev/null 2>&1; then
    # install bsdtar temporarily
    pkg install bsdtar -y  &> /dev/null
    installed_bsdtar=true
fi

# assume these aren't installed
packages="cmake ninja openjdk-21"
echo "installing $packages"
pkg install $packages -y &> /dev/null

# === NDK ===

# https://github.com/lzhiyong/termux-ndk
echo "downloading NDK"
# curl --progress-bar -L -o ndk.7z https://github.com/lzhiyong/termux-ndk/releases/download/android-ndk/android-ndk-r29-aarch64.7z
echo "extracting NDK"
# echo "extracting NDK" >> "$logfile"
mkdir -p "$HOME/.installtemp/ndk"
bsdtar -C "$HOME/.installtemp/ndk" -xf ndk.7z

mkdir -p "$ANDROID_HOME/ndk"
mv "$HOME/.installtemp/ndk/android-ndk-r29" "$ANDROID_HOME/ndk/29.0.14206865"

# === /NDK/ ===

# === BUILD TOOLS ===

echo "downloading build-tools"
# curl --progress-bar -L -o android-build-tools.rpm "https://download.copr.fedorainfracloud.org/results/curtisy/android-build-tools/fedora-44-aarch64/Packages/a/android-build-tools-36.1.0-1.fc44.aarch64.rpm"
echo "installing build-tools"
mkdir -p "$HOME/.installtemp/build-tools"
bsdtar -C "$HOME/.installtemp/build-tools" -xf android-build-tools.rpm

TOOLS="$ANDROID_HOME/build-tools/36.0.0"

# HACK; FIX SOMETIME: this overwrites build-tools to v36.0.0 (even though from rpm it's v36.1.0)
mkdir -p "$ANDROID_HOME/build-tools"
mv "$HOME/.installtemp/build-tools/usr/libexec/android-tools" "$TOOLS"
echo -e "Pkg.UserSrc=false\nPkg.Revision=36.0.0" > "$ANDROID_HOME/build-tools/36.0.0/source.properties"

for bin in aidl aapt aapt2 dexdump zipalign split-select; do
    mv "$TOOLS/$bin" "$TOOLS/${bin}.bin"
    cat > "$TOOLS/${bin}_wrap.c" << EOF
#include <unistd.h>
#include <string.h>

int main(int argc, char *argv[], char *envp[]) {
    int i, j;
    for (i = j = 0; envp[i]; i++)
        if (strncmp(envp[i], "LD_PRELOAD=", 11) != 0 &&
            strncmp(envp[i], "TERMUX_EXEC__PROC_SELF_EXE=", 27) != 0)
            envp[j++] = envp[i];
    envp[j] = NULL;
    execve("$TOOLS/${bin}.bin", argv, envp);
    return 1;
}
EOF
    clang -o "$TOOLS/$bin" "$TOOLS/${bin}_wrap.c"
    rm "$TOOLS/${bin}_wrap.c"
    echo "patched: $bin"
done

# === /BUILD TOOLS/ ===

# === CMDLINE TOOLS

echo "downloading cmdline-tools"
# cmdlinetoolsURL=$(curl -s https://developer.android.com/studio | grep -oE "https://dl.google.com/android/repository/commandlinetools-linux-[0-9]+_latest\.zip")
# curl --progress-bar -L -o commandlinetools.zip "$cmdlinetoolsURL"

echo "extracting cmdline-tools"
bsdtar -C "$HOME/.installtemp" -xf commandlinetools.zip
num=$(awk -F= '/Pkg.Revision/{print $2}' "$HOME/.installtemp/cmdline-tools/source.properties")
mkdir -p "$ANDROID_HOME/cmdline-tools"
mv "$HOME/.installtemp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/$num"

echo "accepting licenses"
yes | $ANDROID_HOME/cmdline-tools/$num/bin/sdkmanager --licenses &> /dev/null

echo "cleaning up"
rm -rf "$HOME/.installtemp"
if $installed_bsdtar; then
    pkg uninstall bsdtar -y  &> /dev/null
fi
apt autoremove -y &> /dev/null

if [ ! -f ./gradlew ]; then
    echo
    echo "important: if you are using the NDK, you will need to manually add:"
    echo "   cmake.dir=/data/data/com.termux/files/usr   "
    echo "to your local.properties file inside your project" 
    echo
else
    echo "cmake.dir=/data/data/com.termux/files/usr" >> local.properties
fi
