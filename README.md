# Passkey PRF Encryption POC

A proof of concept demonstrating that passkeys can derive encryption keys using the WebAuthn PRF extension for client-side encryption and decryption. This is a Kotlin Multiplatform project with shared logic for Android and iOS.

## Overview

This project validates that:
1. The app can request the PRF (Pseudo-Random Function) extension during passkey authentication
2. The PRF output is deterministic (same salt + same passkey = same key)
3. The PRF output can be used to derive AES-GCM encryption keys
4. Encrypted data survives app restarts and can be decrypted later

## Architecture

```
┌────────────────────────────┐         ┌──────────────────┐
│   Compose Multiplatform    │   HTTP  │   Ktor Server    │
│   App (Android + iOS)      │ ◄─────► │                  │
│ - Passkey Manager          │         │ - WebAuthn4j     │
│ - PRF + Encryption         │         │ - Credential     │
│ - Local Storage            │         │   Storage        │
└────────────────────────────┘         └──────────────────┘
         │                                        │
         ▼                                        ▼
┌────────────────────────────┐         ┌──────────────────┐
│ Android Credential Manager │         │  ngrok (HTTPS)   │
│ Apple AuthenticationServices│        │                  │
└────────────────────────────┘         └──────────────────┘
```

## What is the WebAuthn PRF Extension?

The **[PRF (Pseudo-Random Function) extension](https://w3c.github.io/webauthn/#prf-extension)** is a WebAuthn Level 3 feature that enables authenticators to generate cryptographically strong secret keys for client-side encryption. Unlike standard WebAuthn authentication, PRF produces **deterministic outputs** that can be used for key derivation.

### Technical Details

- Underlying algorithm: HMAC-SHA-256
- Output size: 32 bytes (256 bits)
- Input: Salt value (provided by the relying party)
- Deterministic: Same salt + same passkey credential = same PRF output
- Security: PRF output is derived from a credential-specific internal key that never leaves the authenticator

### Key Benefits for Client-Side Encryption

1. Zero server knowledge: PRF output never leaves the client device
2. Deterministic key generation: Same passkey + same salt produces the same key
3. Hardware-backed security: Uses Secure Enclave or Android StrongBox when available
4. No key storage: Keys are derived on-demand
5. Phishing resistant: Inherits WebAuthn origin binding

### Platform Support

- Android 14+ with Credential Manager and up-to-date Play Services
- iOS 18+ using `ASAuthorizationPublicKeyCredentialPRF*` APIs
- Not all authenticators implement PRF; support varies

## Project Structure

```
passkey-encryption-poc/
├── app/                     # KMP app (Compose UI + shared logic)
│   ├── src/commonMain/       # Shared code for Android + iOS
│   ├── src/androidMain/      # Android-specific integrations
│   └── src/iosMain/          # iOS-specific integrations
├── passkey-encryption/       # Shared passkey + encryption library
├── iosApp/                   # iOS Xcode wrapper project
├── server/                   # Ktor backend (Kotlin)
└── start-server.sh           # Starts ngrok + server, updates local.properties
```

## Prerequisites

- Android Studio Hedgehog or newer
- Android device with Android 14+
- iOS 18+ for PRF support on Apple platforms
- Xcode 15+ for iOS builds
- ngrok account (free tier works)
- JDK 17+

## Quick Start

### 1. Get your app's SHA256 fingerprint

```bash
cd passkey-encryption-poc
./gradlew :app:signingReport
```

Look for `SHA256:` under `Variant: debug`.

### 2. Configure local.properties

Copy `local.properties.example` to `local.properties` and set:

- `android.sha256` to your debug SHA256 fingerprint
- `ngrok.domain` if you want a fixed ngrok domain
- `ios.teamId` and `ios.bundleId` or `ios.appId` if you have Apple Developer Program access

`server.url` is updated automatically by `start-server.sh`.

If you are using a free Apple account, set `ios.associatedDomains=false` to build without the Associated Domains entitlement (passkeys for your domain will not work without it).

### 3. Start the server (starts ngrok + sets env)

```bash
./start-server.sh
```

This updates `server.url` in `local.properties`, which is used by both Android and iOS builds. The Android intent-filter host is derived from `server.url` at build time.

### 4. iOS domain association (required for passkeys)

Run `./scripts/sync-ios-entitlements.sh` (or `./start-server.sh`, which calls it) to generate `iosApp/iosApp/iosApp.generated.entitlements` from `server.url` and add:

```
webcredentials:<YOUR_NGROK_DOMAIN>
```

Ensure the Apple App Site Association file is reachable:

```
https://<YOUR_NGROK_DOMAIN>/.well-known/apple-app-site-association
```

Note: Associated Domains requires Apple Developer Program membership. With a free account, the capability may not appear and installs may fail when entitlements are present.

### 5. Build and run the apps

Android:
1. Open the project in Android Studio
2. Sync Gradle
3. Build and install on your Android 14+ device

iOS:
1. Open `iosApp/iosApp.xcodeproj`
2. Select the `iosApp` target
3. Build and run on an iOS 18+ device

## How It Works

### Registration (Create Passkey)

1. App requests options from server (challenge, RP info)
2. App calls platform passkey API with `extensions: { prf: {} }`
3. User creates passkey with biometrics
4. App sends credential to server for verification
5. Server stores public key

### Encryption

1. App generates or retrieves a random PRF salt
2. App requests auth options from server with salt
3. App calls platform passkey API with `extensions: { prf: { eval: { first: salt } } }`
4. User authenticates with biometrics
5. PRF returns a 32-byte output
6. App derives AES-256 key using HKDF
7. App encrypts data with AES-GCM
8. App stores ciphertext and IV locally

### Decryption

1. App retrieves saved salt and ciphertext
2. App authenticates with the same salt
3. PRF returns the same 32-byte output
4. App derives the same AES key
5. App decrypts ciphertext

## Key Points

- PRF output is deterministic: Same passkey + same salt = same output
- PRF output never leaves the device
- Salt must be stored to decrypt later
- Keys are derived on-demand and not stored

## Troubleshooting

### "No PRF output in response"

- Verify Android 14+ with Google Password Manager
- Verify iOS 18+ with a passkey in iCloud Keychain
- Not all authenticators support PRF

### "Application is not associated with domain" on iOS

- Verify AASA is reachable at `/.well-known/apple-app-site-association`
- Confirm `webcredentials:<YOUR_DOMAIN>` is in the generated entitlements file
- Requires Apple Developer Program membership

### "Digital Asset Links verification failed" on Android

- Verify `/.well-known/assetlinks.json` is reachable
- Check SHA256 fingerprint matches your debug keystore
- Reinstall the app after updating `server.url`

### iOS logs are missing in Xcode

- Open the Debug Area in Xcode (View -> Debug Area -> Activate Console)
- Select your device in the scheme destination
- If running wirelessly, reconnect the device and try again

## Resources

### WebAuthn PRF Extension

- [W3C WebAuthn PRF Extension Specification](https://w3c.github.io/webauthn/#prf-extension)
- [PRF, WebAuthn and its Role in Passkeys](https://bitwarden.com/blog/prf-webauthn-and-its-role-in-passkeys/)
- [passkeyprf.com](https://passkeyprf.com)

### Platform APIs

- [Android Credential Manager](https://developer.android.com/training/sign-in/passkeys)
- [Android Credential Manager API Reference](https://developer.android.com/reference/androidx/credentials/CredentialManager)
- [AuthenticationServices](https://developer.apple.com/documentation/authenticationservices)

### Server-Side Libraries

- [webauthn4j](https://github.com/webauthn4j/webauthn4j)

## License

MIT
