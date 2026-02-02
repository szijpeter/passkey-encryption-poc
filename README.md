# Passkey PRF Encryption POC

A proof of concept demonstrating that Android passkeys can derive encryption keys using the WebAuthn PRF extension for client-side encryption/decryption.

## Overview

This project validates that:
1. Android's Credential Manager can request the PRF (Pseudo-Random Function) extension during passkey authentication
2. The PRF output is deterministic (same salt + same passkey = same key)
3. The PRF output can be used to derive encryption keys for AES-GCM encryption/decryption
4. Encrypted data survives app restarts and can be decrypted in a new session

## Architecture

```
┌──────────────────┐         ┌──────────────────┐
│   Android App    │   HTTP  │   Ktor Server    │
│                  │ ◄─────► │                  │
│ - Passkey Mgr    │         │ - WebAuthn4j     │
│ - Encryption     │         │ - Credential     │
│ - Local Storage  │         │   Storage        │
└──────────────────┘         └──────────────────┘
         │                            │
         ▼                            ▼
┌──────────────────┐         ┌──────────────────┐
│ Google Password  │         │  ngrok (HTTPS)   │
│    Manager       │         │                  │
└──────────────────┘         └──────────────────┘
```

## What is the WebAuthn PRF Extension?

The **[PRF (Pseudo-Random Function) extension](https://w3c.github.io/webauthn/#prf-extension)** is a WebAuthn Level 3 feature that enables authenticators to generate cryptographically strong secret keys for client-side encryption. Unlike standard WebAuthn authentication (which generates unique signatures each time), PRF produces **deterministic outputs** that can be used for deriving encryption keys.

### Technical Details

- **Underlying Algorithm**: HMAC-SHA-256
- **Output Size**: 32 bytes (256 bits)
- **Input**: Salt value (provided by the relying party)
- **Deterministic**: Same salt + same passkey credential = same PRF output
- **Security**: PRF output is derived from a credential-specific internal key that never leaves the authenticator's secure element

### How It Works

When you invoke the PRF extension during WebAuthn authentication:

1. **Input**: You provide a salt (arbitrary byte sequence)
2. **Computation**: The authenticator computes `HMAC-SHA-256(credential_internal_key, salt)`
3. **Output**: Returns a 32-byte pseudo-random value
4. **Key Derivation**: This output can be used directly or as input to a KDF (like HKDF) to derive encryption keys

### Key Benefits for Client-Side Encryption

1. **Zero Server Knowledge**: The PRF output never leaves the client device, enabling true end-to-end encryption where the server cannot access plaintext data
2. **Deterministic Key Generation**: Same passkey + same salt always produces the same encryption key, allowing data to be encrypted once and decrypted later
3. **Hardware-Backed Security**: PRF computation happens within the authenticator's secure element (e.g., TPM, Secure Enclave, Android StrongBox)
4. **No Key Storage**: Encryption keys are derived on-demand rather than stored, reducing attack surface
5. **Phishing Resistant**: Inherits WebAuthn's origin-binding properties—PRF outputs differ across domains

### Use Cases

- **Password Managers**: Encrypt vaults using passkey-derived keys (e.g., Bitwarden)
- **Identity Wallets**: Secure credential storage without server-side decryption
- **Non-Custodial Wallets**: Cryptocurrency wallets where private keys never leave the device
- **Sensitive Document Storage**: Client-side document encryption in cloud storage applications
- **End-to-End Encrypted Messaging**: Derive message encryption keys from passkeys

### Browser and Platform Support

The PRF extension is currently available in:
- **Chromium-based browsers** (Chrome, Edge) on desktop and Android
- **Android 14+** with Google Password Manager and up-to-date Play Services
- **iOS/macOS**: Limited support (check latest compatibility)

> **Note**: PRF support requires both browser/platform support AND authenticator support. Not all authenticators implement the PRF extension.

## Project Structure

```
passkey-encryption-poc/
├── server/                  # Ktor backend (Kotlin)
│   └── src/main/kotlin/
│       ├── Application.kt
│       ├── routes/          # HTTP endpoints
│       ├── webauthn/        # WebAuthn4j integration
│       └── storage/         # In-memory credential store
│
├── app/                     # Android app (Kotlin)
│   └── src/main/kotlin/
│       ├── MainActivity.kt
│       ├── MainViewModel.kt
│       ├── passkey/         # Credential Manager
│       ├── crypto/          # AES-GCM encryption
│       ├── storage/         # SharedPreferences
│       └── ui/              # Compose UI
│
└── gradle/                  # Build configuration
```

## Prerequisites

- Android Studio Hedgehog or newer
- Android device with Android 14+ and Google Password Manager
- ngrok account (free tier works)
- JDK 17+

## Quick Start

### 1. Start ngrok

```bash
ngrok http 8080
```

Note the HTTPS URL (e.g., `https://abc123.ngrok.io`)

### 2. Get your app's SHA256 fingerprint

```bash
cd passkey-encryption-poc
./gradlew :app:signingReport
```

Look for "SHA256:" under "Variant: debug"

### 3. Set environment variables and start server

```bash
export RP_ID="abc123.ngrok.io"  # Your ngrok domain (without https://)
export ORIGIN="https://abc123.ngrok.io"
export APP_SHA256_FINGERPRINT="YOUR:SHA256:FINGERPRINT"
export ANDROID_ORIGIN="android:apk-key-hash:YOUR_BASE64_KEY_HASH"

./gradlew :server:run
```

### 4. Update Android app

In `app/src/main/AndroidManifest.xml`, replace:
```xml
<data android:scheme="https" android:host="YOUR_NGROK_DOMAIN" />
```

### 5. Build and run the Android app

1. Open project in Android Studio
2. Sync Gradle
3. Build and install on your Android 14+ device
4. Enter your ngrok URL and tap "Save"
5. Tap "Create Passkey"
6. Tap "Encrypt"
7. Close the app completely
8. Reopen and tap "Decrypt"
9. Verify the decrypted text matches!

## How It Works

### Registration (Create Passkey)

1. App requests options from server (challenge, RP info)
2. App calls Credential Manager with `extensions: { prf: {} }`
3. User creates passkey with biometrics
4. App sends credential to server for verification
5. Server stores public key

### Encryption

1. App generates/retrieves a random PRF salt (32 bytes)
2. App requests auth options from server with salt
3. App calls Credential Manager with `extensions: { prf: { eval: { first: salt } } }`
4. User authenticates with biometrics
5. **PRF extension returns 32-byte output** ← The magic!
6. App derives AES-256 key using HKDF
7. App encrypts data with AES-GCM
8. App stores ciphertext + IV locally

### Decryption

1. App retrieves saved salt and ciphertext
2. App authenticates with **same salt**
3. PRF returns **same 32-byte output** ← Deterministic!
4. App derives **same AES key**
5. App decrypts ciphertext
6. Plaintext restored!

## Key Points

- **PRF output is deterministic**: Same passkey + same salt = same output
- **PRF output never leaves device**: Server only validates the assertion
- **Salt should be stored**: You need the same salt to decrypt
- **Device-bound**: Works across app restarts but tied to the device

## Verification Checklist

- [ ] PRF hash matches between encryption and decryption
- [ ] Key hash matches between sessions
- [ ] Decryption succeeds after app restart
- [ ] Different salts produce different keys (test manually)

## Troubleshooting

### "No PRF output in response"

PRF extension not supported. Check:
- Android 14+ with Google Password Manager
- Passkey stored in Google Password Manager (not Samsung/other)
- Device has up-to-date Play Services

### "Digital Asset Links verification failed"

- Verify `.well-known/assetlinks.json` is accessible at your ngrok URL
- Check SHA256 fingerprint matches your debug keystore
- Try clearing app data and reinstalling

### "Challenge not found"

- Challenges expire quickly, try again
- Server may have restarted (in-memory storage)

## Resources

### WebAuthn PRF Extension

- [W3C WebAuthn PRF Extension Specification](https://w3c.github.io/webauthn/#prf-extension) - Official technical specification
- [PRF, WebAuthn and its Role in Passkeys](https://bitwarden.com/blog/prf-webauthn-and-its-role-in-passkeys/) - Bitwarden's comprehensive guide to PRF
- [passkeyprf.com](https://passkeyprf.com) - Interactive PRF extension playground for testing

### Android Development

- [Android Credential Manager](https://developer.android.com/training/sign-in/passkeys) - Official Android passkey documentation
- [Android Credential Manager API Reference](https://developer.android.com/reference/androidx/credentials/CredentialManager)

### Server-Side Libraries

- [webauthn4j](https://github.com/webauthn4j/webauthn4j) - Java/Kotlin WebAuthn library used in this project


## License

MIT
