# CRT-angarine AI Agent Reference Manual (gemini.md)

Welcome! This file is designed to get you (Gemini or other AI assistants) instantly up to speed on the **CRT-angarine** project. It serves as a central registry of the codebase architecture, modules, dependencies, network protocols, setup commands, and development guidelines.

---

## 🛠️ Project Architecture & Modules

The repository is organized as a Kotlin-based Gradle multi-project, with a React+TypeScript web client built inside the root directory.

```
CRT-angarine/
├── shared/             # Kotlin multiplatform-like library (shared models, serializable packets)
├── backend/            # Kotlin Ktor Server (Netty, WebSockets, HTTP API, static resources router)
├── mod/                # Kotlin NeoForge Minecraft Mod (Client-side camera data collection, Aim controls)
└── webapp/             # Vite + React + Three.js CRT surveillance matrix frontend
```

### 1. `:shared` Module
Contains shared data models annotated with `@Serializable` for communication between the Minecraft mod, backend server, and (indirectly, via JSON stringification) the webapp.
* **Key Files:**
  * [`shared/src/main/kotlin/me/orange/crtangarine/shared/Packets.kt`](file:///C:/Users/Marcos/Documents/Dev/CRT-angarine/shared/src/main/kotlin/me/orange/crtangarine/shared/Packets.kt): Contains `AuthTokenPacket`, `TerrainFrustumPayload`, `EntityDeltaStream`, `CameraStreamCommand`, and the wrapper sealed class `ModMessage`.
  * [`shared/src/main/kotlin/me/orange/crtangarine/shared/CameraData.kt`](file:///C:/Users/Marcos/Documents/Dev/CRT-angarine/shared/src/main/kotlin/me/orange/crtangarine/shared/CameraData.kt): Contains `CameraData`, `CameraInfo`, `StationInfo`, and `CameraRegistryUpdate`.
  * **Encryption:** `CryptoUtils` uses a simple xor key (`"CRTangarineSecret"`) to encrypt/decrypt tokens between the client and server.

### 2. `:backend` Module
A Ktor Server powered by Netty that acts as the messaging broker and static file server.
* **Port:** `8080` (configured in `application.yaml`).
* **Static Assets:** Serves compiled React assets from the `webapp/dist` folder via `singlePageApplication` routing.
* **API Endpoints:**
  * `POST /api/login`: Validates the token provided by the webapp dashboard.
  * `POST /api/register-token`: Registers player UUID and encrypted token. Called by the Minecraft mod.
  * `GET /api/cameras`: Returns all cameras associated with a user's token.
* **WebSocket Routes:**
  * `/api/mod/stream`: Connection from the Minecraft mod. Listens for `RegistryUpdateMessage`, `FrustumPayloadMessage`, and `EntityStreamMessage`.
  * `/api/webapp/view`: Connection from the web dashboard. Sends a camera subscription request and receives streamed frustum payload and entity messages.
* **Authentication Storage:** Flat-file JSON database (`auth_tokens.json` in the root) managed by `TokenRegistry`.

### 3. `:mod` Module
A Minecraft 1.21.1 NeoForge mod using Kotlin. Connects to the Ktor server as a Ktor client to stream data and register players.
* **Core Registration:**
  * Blocks: `Camera Block` (directional orientation) and `Camera Station Block` (owner UUID + custom name).
  * Items: `Security Keycard Item` (stores the player profile UUID).
* **Key Mechanisms:**
  * **Station Identity Binding:** Burning a player's identity from a `Security Keycard` into the station block entity.
  * **Camera Aiming Pipeline:** Fired from the Station UI. Possesses camera entity view, binds rotation pitch/yaw values to block states, and releases possession.
  * **Surveillance Frustum Cone Math:** Streams blocks and entities within a 70-degree frustum up to 32 blocks away *only* when the backend signals `isActive = true`.

### 4. `webapp` (Vite / React + Three.js)
A React dashboard built using raw Three.js rendering techniques to display environment grids and track entities in a retro neon/CRT scanline shader interface.
* **Performance:** Utilizes `THREE.InstancedMesh` for rendering static blocks without causing React re-render lag.
* **Shading Pipeline:** A post-processing `EffectComposer` pipeline applying CRT scanlines, phosphorus curves, chromatic aberration, and screen-flicker.

---

## ⚡ Development & Orchestration Commands

> [!IMPORTANT]
> **Workflow Constraint:** Do NOT run heavy Gradle compilation or build commands (like `./gradlew.bat build`) after every small edit. Only build when testing end-to-end integration or when requested.

### 1. Build the Complete Project
Building the backend automatically triggers `buildWebapp` task, compiles Vite production assets, and embeds them inside the Ktor server jar.
```powershell
./gradlew build
```

### 2. Run the Backend Ktor Server
Launches the Ktor application on `http://localhost:8080`.
```powershell
./gradlew :backend:run
```

### 3. Launch the Minecraft Mod Client
Launches a client-side Minecraft environment in dev mode with the NeoForge mod injected.
```powershell
./gradlew :mod:runClient
```

### 4. Run the Vite Webapp in Dev Mode
For rapid web UI iterations (independent of Ktor static serving). Run from the `/webapp` directory.
```powershell
cd webapp
npm run dev
```

---

## 📡 Data Flow & WebSockets Protocol

```
[ Minecraft Mod Client ] <====== (WS /api/mod/stream) ======> [ Ktor Backend ] <====== (WS /api/webapp/view) ======> [ Webapp Dashboard ]
```

### Protocol Details

1. **Registry Synchronization:**
   * When the mod connects, it sends `RegistryUpdateMessage` containing all station information and linked cameras.
   * Webapp polls `/api/cameras` (authenticated) to populate the dashboard selector.

2. **On-Demand Streaming:**
   * When a user opens a camera feed in the webapp:
     * Webapp initiates a WebSocket connection to `ws://localhost:8080/api/webapp/view?token=<token>&cameraId=<cameraId>`.
     * Ktor increments the watcher count for that `cameraId`. If it goes from `0 -> 1`, Ktor sends a `CameraStreamCommand(cameraId, isActive = true)` packet to the mod.
     * The mod starts calculating frustum coordinates and sending static terrain blocks and live entity deltas.
   * When the user leaves or closes the tab:
     * WebSocket closes; Ktor decrements the watcher count. If it hits `0`, Ktor sends `CameraStreamCommand(cameraId, isActive = false)`.
     * Mod pauses logic computations to preserve client/server TPS.

---

## 📐 Surveillance & Mathematical Configurations

### 1. Camera Projection Angle
* **Viewing Angle:** 70-degree horizontal and vertical projection cone.
* **Maximum Distance:** 32-block threshold.
* **Static Block Classification IDs:**
  * `0`: Air / Passable
  * `1`: Solid Obstacle / Opaque
  * `2`: Hazard (Lava / Fire)
  * `3`: Interactable (Doors / Buttons / Chests)

### 2. Entity Representation Legend
Dynamic entity coordinates are color-coded inside the retro Three.js painter:
* `PLAYER` ➡️ **Electric Blue**
* `MONSTER` ➡️ **Crimson Red**
* `PASSIVE` ➡️ **Matrix Green**
* `ITEM` ➡️ **Amber Yellow**
* Line vectors must project from each tracking node showing their look-direction (`yaw` and `pitch`).

---

## 🧑‍💻 Coding Standards & Design Guidelines

### 1. Visual Aesthetics & UI Design (CRT Theme)
The dashboard webapp **must look premium and immersive**. 
* Use high-quality fonts (e.g., *Outfit*, *Space Mono*, or *Share Tech Mono* for the retro feel).
* Enforce retro CRT post-processing effects (chromatic aberration, scanline grids, curved monitor corners).
* Avoid flat browser colors. Use styled CSS variables for neon gradients, transparency levels, and shadows.

### 2. Kotlin / NeoForge Conventions
* **Registry System:** Keep block/item registries aligned using the `KDeferredRegister` system found in [`ModBlocks.kt`](file:///C:/Users/Marcos/Documents/Dev/CRT-angarine/mod/src/main/kotlin/me/orange/crtangarine/block/ModBlocks.kt).
* **NBT Storage:** Player UUID metadata baked onto the `Security Keycard` should use persistent tags that survive inventory drops/transfers.

### 3. Three.js Code Practices
* **Performance:** Never recreate materials or geometries inside high-frequency animation loops.
* **InstancedMesh:** All blocks must be painted using `THREE.InstancedMesh` with translation matrices to minimize draw calls.
* **Cleanup:** Properly handle garbage collection of meshes, composers, and controls on React component unmount (`useEffect` return wrapper).
