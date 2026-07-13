#!/bin/sh
# Named volume ilk çalıştırmada root sahipliğiyle gelir; libretranslate (uid 1032) yazamaz.
# Bozuk/yarım indirilmiş Argos model paketlerini de temizler (metadata.json yoksa).
set -e

DATA_DIR="/home/libretranslate/.local/share/argos-translate"
PKG_DIR="$DATA_DIR/packages"

if [ "$(id -u)" = "0" ]; then
  mkdir -p "$PKG_DIR"
  chown -R libretranslate:libretranslate "$DATA_DIR"
fi

if [ -d "$PKG_DIR" ]; then
  for dir in "$PKG_DIR"/*; do
    [ -d "$dir" ] || continue
    if [ ! -f "$dir/metadata.json" ]; then
      echo "[libretranslate-init] Bozuk paket siliniyor: $dir"
      rm -rf "$dir"
    fi
  done
fi

if [ "$(id -u)" = "0" ]; then
  exec su -s /bin/sh libretranslate -c 'exec /app/venv/bin/libretranslate --host 0.0.0.0'
fi

exec /app/venv/bin/libretranslate --host 0.0.0.0
