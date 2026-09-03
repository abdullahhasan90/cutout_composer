# Cutout Composer (POC)

**Cutout Composer** is an Android Proof-of-Concept (POC) app that allows users to capture objects from the real world and "compose" them into a room photo with realistic occlusion. Built using a Test-Driven Development (TDD) approach, this project explores the intersection of Jetpack Compose, CameraX, and on-device Machine Learning.

## 🚀 Key Features

*   **AI Object Extraction**: Instantly cut out subjects from photos using ML Kit's on-device Subject Segmentation.
*   **Intuitive Interaction**: Drag, pinch-to-scale, and rotate your cutouts with smooth gesture handling.
*   **Assisted Occlusion (Smart Snap)**: Move your cutout *behind* furniture in the room photo. The "Smart Brush" automatically identifies objects in the background and snaps the occlusion mask to their edges.
*   **Debug Visualization**: Briefly highlights detected room objects upon loading to guide the user's "Smart Snap" actions.
*   **High-Res Export**: Save your finished composition to the system gallery.
*   **100% Offline**: All AI processing happens on-device. No data is sent to the cloud.

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose |
| **AI / ML** | Google ML Kit (Subject Segmentation) |
| **Camera** | CameraX |
| **Rendering** | Compose Canvas API (3-Layer Compositor) |
| **State Management** | ViewModel + StateFlow |

## 🏗️ Architecture

The app uses a 3-layer rendering pipeline to create the illusion of depth in a 2D space:

1.  **Layer 0 (Bottom)**: The full room background image.
2.  **Layer 1 (Middle)**: The transformed cutout object (Object photo ⊙ AI Mask).
3.  **Layer 2 (Top)**: The "Foreground" layer. This draws parts of the room background again, but only where the user has "masked" them (Room photo ⊙ Occlusion Mask).

## 🛣️ Roadmap (TDD Phases)

- [x] **Phase 0: "It Moves"** — Implemented core transformation engine (drag/scale/rotate) with hardcoded assets.
- [x] **Phase 1: Real Inputs** — Integrated CameraX, Photo Picker, and AI Segmentation for object extraction. Added Gallery Export.
- [x] **Phase 2: Assisted Occlusion** — Implemented the "Smart Snap" brush and 3-layer compositor.
- [ ] **Phase 3 (Future)** — Integration with Segment Anything Model (SAM) for complex repair work.

## 🚦 Getting Started

1.  Clone the repository.
2.  Open in **Android Studio (Ladybug or newer)**.
3.  Sync Gradle and deploy to a **physical Android device** (Camera required).
4.  Ensure Google Play Services are updated (required for ML Kit model download).

---

*This project was developed as a learning exercise in idiomatic Kotlin and modern Android development.*
