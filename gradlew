#!/usr/bin/env sh
# SPDX-License-Identifier: GPL-3.0-only
# Gradle Wrapper fallback script for ETS2 TruckSim GPS

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
else
  echo "Error: 'gradle' is not installed in PATH. Please install Gradle 8.7+ or use the GitHub Actions workflow." >&2
  exit 1
fi
