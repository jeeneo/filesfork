#!/data/data/com.termux/files/usr/bin/bash
set -e

if ! ping -c 1 -W 5 1.1.1.1 &> /dev/null; then
    echo "no internet, exiting"
    exit 0
fi

clear

ignore_not_termux=false
purge_sdk=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --ignore-not-termux)
        ignore_not_termux=true
        ;;
        --dangerously-purge-existing-sdk)
        purge_sdk=true
        ;;
        *)
        echo "unknown argument: $1"
        exit 0
        ;;
    esac
    shift
done

if ! command -v termux-setup-storage &> /dev/null && ! $ignore_not_termux; then  
    echo "this environment doesn't look like termux"
    echo "if you are certain (or is a false negative), run with the argument --ignore-not-termux"
    exit 0
fi

JAVA_HOME="/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk"
GRADLE_OPTS='"-Dorg.gradle.project.android.aapt2FromMavenOverride=${ANDROID_HOME}/build-tools/36.0.0/aapt2"'

purge_sdk() {
    if [[ -d "$ANDROID_HOME" ]]; then
        rm -rf "$ANDROID_HOME"
    else
        found=$(find "$HOME" -maxdepth 2 -ipath "*/android/sdk" -type d 2>/dev/null | head -1)
        rm -rf $found
    fi
    fix_bashrc
}

fix_bashrc() {
    bashrc="$HOME/.bashrc"
    if [ ! -f "$bashrc" ]; then
        touch "$bashrc"
    fi
    set_export() {
        local var="$1" val="$2" file="$3"
        if grep -q "^export ${var}=" "$file"; then
            sed -i "s|^export ${var}=.*|export ${var}=${val}|" "$file"
        else
            echo "export ${var}=${val}" >> "$file"
        fi
    }
    set_export ANDROID_HOME "\"$ANDROID_HOME\"" "$bashrc"
    set_export JAVA_HOME "$JAVA_HOME" "$bashrc"
    set_export GRADLE_OPTS "$GRADLE_OPTS" "$bashrc"
}

found=$(find "$HOME" -maxdepth 2 -ipath "*/android/sdk" -type d 2>/dev/null | head -1)
if [[ -d "$ANDROID_HOME" || -n "$found" ]]; then
    if $ignore_not_termux && $purge_sdk; then
        echo "purging existing SDK"
        echo "it seems you've also skipped the termux check, purging the SDK on a non-termux enviorment is dangerous"
        read -r -p "press CTRL+C to cancel, or enter to continue..." _
        purge_sdk
    elif $purge_sdk; then
        echo "purging existing SDK then..."
        purge_sdk
    else
        echo "it seems you already have an SDK installed (or partially installed), you can choose to run with the argument --dangerously-purge-existing-sdk to attempt to fix it"
        echo "but be aware this can result in data loss"
        echo "else, try deleting your ~/android/sdk folder and removing any related ~/.bashrc exports"
        exit 0
    fi
fi

ANDROID_HOME="$HOME/android/sdk"
mkdir -p "$ANDROID_HOME"
fix_bashrc

if ! command -v bsdtar &> /dev/null; then
    # install bsdtar temporarily
    pkg install bsdtar -y &> /dev/null
    installed_bsdtar=true
else
    installed_bsdtar=false
fi

# assume these aren't installed
packages="cmake ninja openjdk-21"
echo "installing $packages"
pkg install $packages -y &> /dev/null

# === NDK ===

# https://github.com/lzhiyong/termux-ndk
if [ ! -f "./ndk.7z" ]; then
    echo "downloading NDK"
    curl --progress-bar -L -o ndk.7z https://github.com/lzhiyong/termux-ndk/releases/download/android-ndk/android-ndk-r29-aarch64.7z
    echo "extracting NDK"
    mkdir -p "$HOME/.installtemp/ndk"
    bsdtar -C "$HOME/.installtemp/ndk" -xf ndk.7z
    rm ndk.7z
else
    echo "extracting NDK"
    mkdir -p "$HOME/.installtemp/ndk"
    bsdtar -C "$HOME/.installtemp/ndk" -xf ndk.7z
fi

mkdir -p "$ANDROID_HOME/ndk"
mv "$HOME/.installtemp/ndk/android-ndk-r29" "$ANDROID_HOME/ndk/29.0.14206865"

# === /NDK/ ===

# === BUILD TOOLS ===

if [ ! -f "./android-build-tools.rpm" ]; then
    echo "downloading build-tools"
    curl --progress-bar -L -o android-build-tools.rpm "https://download.copr.fedorainfracloud.org/results/curtisy/android-build-tools/fedora-44-aarch64/Packages/a/android-build-tools-36.1.0-1.fc44.aarch64.rpm"
    echo "extracting build-tools"
    mkdir -p "$HOME/.installtemp/build-tools"
    bsdtar -C "$HOME/.installtemp/build-tools" -xf android-build-tools.rpm
    rm android-build-tools.rpm
else
    echo "extracting build-tools"
    mkdir -p "$HOME/.installtemp/build-tools"
    bsdtar -C "$HOME/.installtemp/build-tools" -xf android-build-tools.rpm
fi

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
if [ ! -f "./commandlinetools.zip" ]; then
    echo "downloading cmdline-tools"
    cmdlinetoolsURL=$(curl -s https://developer.android.com/studio | grep -oE "https://dl.google.com/android/repository/commandlinetools-linux-[0-9]+_latest\.zip")
    curl --progress-bar -L -o commandlinetools.zip "$cmdlinetoolsURL"
    echo "extracting cmdline-tools"
    bsdtar -C "$HOME/.installtemp" -xf commandlinetools.zip
    num=$(awk -F= '/Pkg.Revision/{print $2}' "$HOME/.installtemp/cmdline-tools/source.properties")
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    mv "$HOME/.installtemp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/$num"
    rm commandlinetools.zip
else
    echo "extracting cmdline-tools"
    bsdtar -C "$HOME/.installtemp" -xf commandlinetools.zip
    num=$(awk -F= '/Pkg.Revision/{print $2}' "$HOME/.installtemp/cmdline-tools/source.properties")
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    mv "$HOME/.installtemp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/$num"
fi

echo "accepting licenses"
yes | $ANDROID_HOME/cmdline-tools/$num/bin/sdkmanager --licenses &> /dev/null

echo "cleaning up"

rm -rf "$HOME/.installtemp"
if $installed_bsdtar; then
    pkg uninstall bsdtar -y  &> /dev/null
fi
apt autoremove -y &> /dev/null

echo
echo "important: if you are using the NDK in your project, you will need to manually add:"
echo "   cmake.dir=/data/data/com.termux/files/usr"
echo "to your local.properties file inside your project" 
echo "and either install the required cmake version in termux, or remove the version declaration from your app-level gradle.kts and hope termux's cmake works"
echo

echo "you may need to run 'source ~/.bashrc' or restart termux for the env vars to take affect"
