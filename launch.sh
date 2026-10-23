#!/bin/sh
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
charles_home=${CHARLES_HOME:-}
if [ -z "$charles_home" ]; then
    for candidate in "$script_dir" "$script_dir/.." /opt/charles /usr/lib/charles /Applications/Charles.app/Contents/Java; do
        if [ -f "$candidate/lib/charles.jar" ]; then charles_home=$candidate; break; fi
    done
fi
if [ -z "$charles_home" ] || [ ! -f "$charles_home/lib/charles.jar" ]; then
    echo 'Set CHARLES_HOME to the directory containing lib/charles.jar.' >&2
    exit 1
fi
charles_home=$(CDPATH= cd -- "$charles_home" && pwd)
addon_jar="$script_dir/charles-pem-addon.jar"
if [ ! -f "$addon_jar" ]; then
    echo 'Download charles-pem-addon.jar from release v0.1 and place it beside launch.sh.' >&2
    exit 1
fi
if [ -n "${JAVA_HOME:-}" ]; then java_bin="$JAVA_HOME/bin/java"
elif [ -x "$charles_home/jdk/bin/java" ]; then java_bin="$charles_home/jdk/bin/java"
elif [ -x "$charles_home/jdk/Contents/Home/bin/java" ]; then java_bin="$charles_home/jdk/Contents/Home/bin/java"
else java_bin=$(command -v java || true)
fi
if [ -z "$java_bin" ] || [ ! -x "$java_bin" ]; then echo 'Install Java 25+ and set JAVA_HOME.' >&2; exit 1; fi
case $(uname -s) in
    Darwin) logs="$HOME/Library/Logs/Charles-PEM" ;;
    *) logs="${XDG_STATE_HOME:-$HOME/.local/state}/charles-pem" ;;
esac
mkdir -p "$logs"
cd "$charles_home"
nohup "$java_bin" -Dsun.java2d.d3d=false "-Dcharles.pem.home=$charles_home" \
    "-Djava.library.path=$charles_home/lib" "-Djna.boot.library.path=$charles_home/lib" -Djna.nounpack=true \
    --add-opens=java.base/sun.security.ssl=ALL-UNNAMED \
    --add-opens=java.desktop/java.awt.event=ALL-UNNAMED \
    --add-opens=java.base/java.io=ALL-UNNAMED --enable-native-access=ALL-UNNAMED \
    -cp "$addon_jar:$charles_home/lib/*" addon.Launcher "$@" >"$logs/launcher-$$.log" 2>&1 </dev/null &
