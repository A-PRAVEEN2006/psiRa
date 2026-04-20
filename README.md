# PsiRa | Secure Communication Network

PsiRa is a privacy-focused Android application designed for secure data transmission, stealth messaging, and decentralized communications. It utilizes high-level encryption and WebRTC protocols to ensure that all user interactions remain strictly private and untraceable.

## Features

- **End-to-End Encryption**: Robust encryption for all messaging and data transfers.
- **Secure WebRTC Communications**: Real-time voice and video capabilities powered by Stream-WebRTC.
- **Biometric Authentication**: Secondary security layer using Android BiometricPrompt.
- **Decentralized Cloud Integration**: Utilizes Firebase for real-time synchronization, secure authentication, and data storage.
- **Privacy-First Architecture**: Designed to minimize the digital footprint and maintain local data integrity.

## Technical Specification

- **Development Platform**: Android
- **Communication Protocol**: WebRTC (via Stream-WebRTC)
- **Cloud Infrastructure**: Firebase (Database, Auth, Messaging, Storage)
- **Security**: Biometric authentication & custom encryption layers
- **UI Framework**: XML Layouts & Android Material Components
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)

## Getting Started

### Prerequisites

- Android Studio.
- JDK 11+.
- A standard Firebase configuration file (`google-services.json`) configured with your project's credentials.

### Installation

1. Clone the repository.
2. Add your `google-services.json` to the `app/` directory.
3. Build the project using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```

---
© 2026 PRIME X Stealth Security Division. All rights reserved.
