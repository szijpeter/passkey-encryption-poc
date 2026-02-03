#!/bin/bash
set -euo pipefail

PROJECT_ROOT=$(cd "$(dirname "$0")/.." && pwd)
LOCAL_PROPERTIES="$PROJECT_ROOT/local.properties"
ENTITLEMENTS_FILE="$PROJECT_ROOT/iosApp/iosApp/iosApp.generated.entitlements"

ASSOCIATED_DOMAINS_ENABLED="true"
DOMAIN=""

if [ -f "$LOCAL_PROPERTIES" ]; then
  raw_assoc=$(grep -E "^ios.associatedDomains=" "$LOCAL_PROPERTIES" | tail -n 1 | cut -d= -f2- || true)
  if [ -n "$raw_assoc" ]; then
    raw_assoc=$(echo "$raw_assoc" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')
    if [ "$raw_assoc" = "false" ] || [ "$raw_assoc" = "0" ] || [ "$raw_assoc" = "no" ]; then
      ASSOCIATED_DOMAINS_ENABLED="false"
    fi
  fi

  server_url=$(grep -E "^server.url=" "$LOCAL_PROPERTIES" | tail -n 1 | cut -d= -f2- || true)
  if [ -n "$server_url" ]; then
    DOMAIN="${server_url#https://}"
    DOMAIN="${DOMAIN#http://}"
    DOMAIN="${DOMAIN%%/*}"
  fi
else
  echo "⚠️  local.properties not found; generating empty entitlements"
  ASSOCIATED_DOMAINS_ENABLED="false"
fi

if [ -z "$DOMAIN" ]; then
  DOMAIN="example.com"
fi

if [ "$ASSOCIATED_DOMAINS_ENABLED" = "true" ]; then
  cat > "$ENTITLEMENTS_FILE" <<EOF2
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>com.apple.developer.associated-domains</key>
  <array>
    <string>webcredentials:${DOMAIN}</string>
  </array>
</dict>
</plist>
EOF2
  echo "✅ Wrote entitlements with webcredentials:${DOMAIN}"
else
  cat > "$ENTITLEMENTS_FILE" <<EOF2
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
</dict>
</plist>
EOF2
  echo "✅ Wrote empty entitlements (Associated Domains disabled)"
fi
