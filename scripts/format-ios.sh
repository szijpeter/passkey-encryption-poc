#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
IOS_DIR="${ROOT_DIR}/iosApp"

if ! command -v swiftformat >/dev/null 2>&1; then
  echo "swiftformat is not installed or not on PATH."
  echo "Install with: brew install swiftformat"
  exit 1
fi

swiftformat "${IOS_DIR}/iosApp" --config "${IOS_DIR}/.swiftformat"
