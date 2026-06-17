#!/bin/bash
# Genera iconos de la app desde tu imagen PNG (idealmente cuadrada, 512x512 o más).
# Uso: ./scripts/generate-icons.sh ruta/a/tu-logo.png

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Uso: $0 ruta/a/tu-logo.png"
  exit 1
fi

SRC="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
BASE="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/res"

if [ ! -f "$SRC" ]; then
  echo "No existe: $SRC"
  exit 1
fi

for pair in "mdpi:48" "hdpi:72" "xhdpi:96" "xxhdpi:144" "xxxhdpi:192"; do
  folder="${pair%%:*}"
  size="${pair##*:}"
  mkdir -p "$BASE/mipmap-$folder"
  sips -z "$size" "$size" "$SRC" --out "$BASE/mipmap-$folder/ic_launcher.png" >/dev/null
  sips -z "$size" "$size" "$SRC" --out "$BASE/mipmap-$folder/ic_launcher_round.png" >/dev/null
done

for pair in "mdpi:108" "hdpi:162" "xhdpi:216" "xxhdpi:324" "xxxhdpi:432"; do
  folder="${pair%%:*}"
  size="${pair##*:}"
  sips -z "$size" "$size" "$SRC" --out "$BASE/mipmap-$folder/ic_launcher_foreground.png" >/dev/null
done

cp "$SRC" "$BASE/drawable/ic_launcher_legacy.png"
echo "Iconos generados en app/src/main/res/mipmap-*"
