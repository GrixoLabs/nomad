#!/usr/bin/env bash
# Install Nomad weather (4h) + map_plotter (30m) systemd timers.
# Usage:
#   sudo bash scripts/install-systemd-timers.sh
#   sudo bash scripts/install-systemd-timers.sh /home/ubuntu-headless/MyProjects/nomad
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
if [[ ! -x "$ROOT/.venv/bin/python" ]]; then
  echo "error: $ROOT/.venv/bin/python not found (create venv first)" >&2
  exit 1
fi
if [[ ! -f "$ROOT/scripts/flush_weather_cache.py" ]]; then
  echo "error: $ROOT does not look like the Nomad backend root" >&2
  exit 1
fi

echo "Installing timers with NOMAD_HOME=$ROOT"

install -d /etc/systemd/system/nomad-flush-weather.service.d
install -d /etc/systemd/system/nomad-sync-map-plotter.service.d

cp "$ROOT/scripts/nomad-flush-weather.service" /etc/systemd/system/
cp "$ROOT/scripts/nomad-flush-weather.timer" /etc/systemd/system/
cp "$ROOT/scripts/nomad-sync-map-plotter.service" /etc/systemd/system/
cp "$ROOT/scripts/nomad-sync-map-plotter.timer" /etc/systemd/system/

cat > /etc/systemd/system/nomad-flush-weather.service.d/override.conf <<OVERRIDE
[Service]
Environment=NOMAD_HOME=$ROOT
OVERRIDE

cat > /etc/systemd/system/nomad-sync-map-plotter.service.d/override.conf <<OVERRIDE
[Service]
Environment=NOMAD_HOME=$ROOT
OVERRIDE

systemctl daemon-reload
systemctl enable --now nomad-flush-weather.timer
systemctl enable --now nomad-sync-map-plotter.timer

# Validate service units can start (oneshot).
systemctl start nomad-flush-weather.service
systemctl start nomad-sync-map-plotter.service

systemctl list-timers 'nomad-*' --no-pager
echo "OK: timers installed for $ROOT"
