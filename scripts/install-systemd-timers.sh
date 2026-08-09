#!/usr/bin/env bash
# Install Nomad weather (4h) + map_plotter (30m) systemd timers.
# Usage:
#   sudo bash scripts/install-systemd-timers.sh
#   sudo bash scripts/install-systemd-timers.sh /home/ubuntu-headless/MyProjects/nomad
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
ROOT="$(readlink -f "$ROOT")"

if [[ ! -x "$ROOT/.venv/bin/python" ]]; then
  echo "error: $ROOT/.venv/bin/python not found (create venv first)" >&2
  exit 1
fi
if [[ ! -f "$ROOT/scripts/flush_weather_cache.py" ]]; then
  echo "error: $ROOT does not look like the Nomad backend root" >&2
  exit 1
fi

echo "Installing timers with NOMAD_HOME=$ROOT"

render_service() {
  local src="$1"
  local dest="$2"
  # systemd requires absolute paths; rewrite /opt/nomad placeholders.
  sed "s|/opt/nomad|${ROOT}|g" "$src" > "$dest"
}

render_service "$ROOT/scripts/nomad-flush-weather.service" /etc/systemd/system/nomad-flush-weather.service
cp "$ROOT/scripts/nomad-flush-weather.timer" /etc/systemd/system/nomad-flush-weather.timer
render_service "$ROOT/scripts/nomad-sync-map-plotter.service" /etc/systemd/system/nomad-sync-map-plotter.service
cp "$ROOT/scripts/nomad-sync-map-plotter.timer" /etc/systemd/system/nomad-sync-map-plotter.timer

# Remove broken drop-ins from the previous install attempt (if any).
rm -rf /etc/systemd/system/nomad-flush-weather.service.d
rm -rf /etc/systemd/system/nomad-sync-map-plotter.service.d

systemctl daemon-reload
systemctl enable nomad-flush-weather.timer
systemctl enable nomad-sync-map-plotter.timer
systemctl restart nomad-flush-weather.timer
systemctl restart nomad-sync-map-plotter.timer

# Validate oneshot services now that paths are absolute.
systemctl start nomad-flush-weather.service
systemctl start nomad-sync-map-plotter.service

systemctl list-timers 'nomad-*' --no-pager
echo "OK: timers installed for $ROOT"
