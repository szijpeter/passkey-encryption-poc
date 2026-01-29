#!/bin/bash
# Start the Passkey POC Server with ngrok

cd "$(dirname "$0")"

# Load environment variables from .env.local if it exists
if [ -f .env.local ]; then
    export $(grep -v '^#' .env.local | xargs)
    echo "✅ Loaded environment from .env.local"
else
    echo "⚠️  No .env.local found. Copy .env.example to .env.local and fill in your values."
    exit 1
fi

# Check required vars
if [ -z "$APP_SHA256_FINGERPRINT" ]; then
    echo "❌ APP_SHA256_FINGERPRINT not set in .env.local"
    exit 1
fi

# Convert SHA256 fingerprint to Android origin format
# Removes colons, converts to lowercase, base64url encodes
FINGERPRINT_HEX=$(echo "$APP_SHA256_FINGERPRINT" | tr -d ':' | tr '[:upper:]' '[:lower:]')
FINGERPRINT_BYTES=$(echo "$FINGERPRINT_HEX" | xxd -r -p)
FINGERPRINT_B64=$(echo -n "$FINGERPRINT_BYTES" | base64 | tr '+/' '-_' | tr -d '=')
export ANDROID_ORIGIN="android:apk-key-hash:$FINGERPRINT_B64"
echo "📱 Android origin: $ANDROID_ORIGIN"

# Start ngrok in background (uses static domain from NGROK_DOMAIN if set)
echo "🌐 Starting ngrok..."
if [ -n "$NGROK_DOMAIN" ]; then
    ngrok http 8080 --domain="$NGROK_DOMAIN" > /dev/null 2>&1 &
else
    ngrok http 8080 > /dev/null 2>&1 &
fi
NGROK_PID=$!

# Wait for ngrok to start and get the URL
sleep 2
NGROK_URL=$(curl -s http://localhost:4040/api/tunnels | grep -o '"public_url":"https://[^"]*' | cut -d'"' -f4)

if [ -z "$NGROK_URL" ]; then
    echo "❌ Failed to get ngrok URL. Is ngrok installed and authenticated?"
    kill $NGROK_PID 2>/dev/null
    exit 1
fi

# Extract domain from URL
NGROK_DOMAIN_EXTRACTED=$(echo "$NGROK_URL" | sed 's|https://||')

echo "✅ ngrok tunnel: $NGROK_URL"
echo ""

# Set environment variables for the server
export RP_ID="$NGROK_DOMAIN_EXTRACTED"
export ORIGIN="$NGROK_URL"

# Update local.properties with server URL for Android app
echo "📱 Updating local.properties for Android app..."
if [ -f local.properties ]; then
    # Remove existing server.url line if present
    grep -v "^server.url=" local.properties > local.properties.tmp
    mv local.properties.tmp local.properties
fi
echo "server.url=$NGROK_URL" >> local.properties
echo "   server.url=$NGROK_URL"

echo ""
echo "📋 Server configuration:"
echo "   RP_ID: $RP_ID"
echo "   ORIGIN: $ORIGIN"
echo "   ANDROID_ORIGIN: $ANDROID_ORIGIN"
echo "   APP_SHA256_FINGERPRINT: ${APP_SHA256_FINGERPRINT:0:20}..."
echo ""
echo "💡 Rebuild the Android app to pick up the new server URL"
echo ""

# Cleanup on exit
cleanup() {
    echo ""
    echo "🛑 Shutting down..."
    kill $NGROK_PID 2>/dev/null
    exit 0
}
trap cleanup SIGINT SIGTERM

# Start the server
echo "🚀 Starting server..."
./gradlew :server:run
