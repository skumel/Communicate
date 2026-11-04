# Communicate — Offline E-Bike Communicator & Telemetry

**Communicate** is an offline peer-to-peer (P2P) voice intercom and real-time GPS telemetry Android application built for e-bike and motorcycle riders.

## Features

- **Instant QR Code Pairing**: Host a session and generate a QR code with an embedded mutual authentication token, or scan a riding partner's QR code to connect without internet or cell service.
- **Offline P2P Mesh Audio & Telemetry**: Powered by Google Nearby Connections (`Strategy.P2P_STAR`) for low-latency PCM audio streaming and live telemetry packets.
- **Push-To-Talk (PTT) & Open Mic Modes**: High-visibility rider dashboard with a large Push-To-Talk hold target and Open Mic mute/unmute toggle, hardware Acoustic Echo Cancellation (`AcousticEchoCanceler`), and Noise Suppression (`NoiseSuppressor`).
- **Bluetooth Headset & Loudspeaker Routing**: Supports Bluetooth SCO / BLE Audio headsets, wired/USB headsets, and built-in loudspeaker switching.
- **Live GPS Speedometer & Heading-Up Radar Map**: Displays real-time riding speed (MPH) and a heading-up relative radar view showing peer rider distance and direction.
- **Screen-Off Background Operation**: Uses a foreground service (`RiderForegroundService`) to keep voice communication and GPS telemetry active while the phone screen is off.

## Building

1. Open the project in **Android Studio** (or set `JAVA_HOME` to JDK 17/21).
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
