# CRT-angarine

A full-stack, real-time surveillance network system that streams live voxel environment matrices and entity coordinates from a **Minecraft** world to an immersive, retro **CRT surveillance dashboard** in your web browser.

Built with a Kotlin multi-project architecture (Ktor, WebSockets, NeoForge) and a Vite + React + Three.js frontend.

### Version Compatibility

| Mod Version | Minecraft Version | Loader / Platform    | Java Version | Status          |
|:------------|:------------------|:---------------------|:-------------|:----------------|
| **`1.0.x`** | `1.21.1`          | NeoForge (21.1.234+) | JDK 21       | Active / Stable |

## Quick Start Guide

To run the surveillance network system, you will need to set up both the webserver and the Minecraft client mod.

### 1. Webserver Setup
The webserver runs the backend database and serves the CRT browser dashboard.

1. Go to the Releases page and download the latest `crtangarine-webserver-v*.zip` package.
2. Extract the ZIP file to a folder on your computer.
3. Launch the webserver:
   * Windows: Double-click `start.bat`.
   * Linux / macOS: Run `./start.sh` in your terminal.
   *(Note: This webserver requires Java 21. If Java is not detected, the startup script will open a link to download it.)*
4. Open your web browser and navigate to `localhost:8080` (or the IP address of the server running the webserver) to access the surveillance dashboard.

### 2. Minecraft Client/Server Setup
We provide two versions of the mod:
* **Client-Only Mod (`crtangarine-client-*.jar`)**: Uses a 3-part setup. You must run the separate Ktor webserver process. Recommended for large servers or dedicated setups.
* **Standalone Mod (`crtangarine-standalone-*.jar`)**: Uses a 2-part setup. The Ktor server and web dashboard run *directly inside* the Minecraft process. No external webserver setup needed!

1. Download your preferred version from the Releases page and place the JAR file inside your client or server `mods` folder.
2. **Required Dependency:** Download the **Kotlin for Forge** mod (version `5.3.0` or higher for NeoForge) and place it in the same `mods` folder.
   * You can download it from [Modrinth](https://modrinth.com/mod/kotlin-for-forge) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge).
   * Any version `5.3.0` or higher will satisfy this dependency (for Minecraft 1.21.1, `5.4.0` or newer is recommended).
3. Launch Minecraft 1.21.1 using the NeoForge mod loader.
4. Once in-game, place down a Camera Station and use a Security Keycard to configure your camera feed.
5. **Configuration**:
   * If running the **Client-Only** version: open `config/crtangarine-common.toml` in your Minecraft directory and set `backendUri` to the address of your external webserver (e.g., `192.168.1.100:8080`).
   * If running the **Standalone** version: the server starts automatically on port `8080`. You can configure the port by editing `embeddedServerPort` in `config/crtangarine-common.toml`. Open `http://localhost:<embeddedServerPort>` in your browser to view the dashboard!

---

## System Architecture & Modules

The project is structured as a Kotlin-based Gradle multi-project, with a React+TypeScript web application inside the root.

```
CRT-angarine/
├── shared/             # Shared packets, encryption, and models (dep-free)
├── backend/            # Ktor Server logic, routes, and React webapp static assets wrapper
├── mod-core/           # Core mod logic, block registrations, aiming, math, rendering, and networking
├── mod-client/         # Client-only wrapper configuration and packaging (generates crtangarine-client.jar)
├── mod-standalone/     # Standalone wrapper packaging (bundles Ktor CIO server + static assets inside the JAR)
└── webapp/             # Vite + React + Three.js CRT surveillance matrix frontend
```

### 1. [Shared](shared) Module
Contains shared data models annotated with `@Serializable` for communication between the Minecraft mod, backend server, and the webapp.
* **Key Files:**
  * [Packets.kt](shared/src/main/kotlin/me/orange/crtangarine/shared/Packets.kt): Sealed class hierarchy representing message envelopes, containing `AuthTokenPacket`, `TerrainFrustumPayload`, `EntityDeltaStream`, `CameraStreamCommand`, and the wrapper `ModMessage`.
  * [CameraData.kt](shared/src/main/kotlin/me/orange/crtangarine/shared/CameraData.kt): Data transfer structures like `CameraData`, `CameraInfo`, `StationInfo`, and `CameraRegistryUpdate`. Includes detailed state (`ONLINE`, `UNLOADED`, `OFFLINE`) tracking.
* **Obfuscation Utility:** `CryptoUtils` uses a fast XOR-based cipher token to mask player credentials before writing to the local flat-file storage.

### 2. [Backend](backend) Module
A Ktor Server powered by Netty that acts as the messaging broker and static file server.
* **Network Port:** Runs on port `8080` (configured in `application.yaml`).
* **Static Assets Server:** Serves compiled React assets from the `webapp/dist` folder using single-page application (SPA) fallback routing.
* **API Endpoints:**
  * `POST /api/login`: Validates the token provided by the webapp dashboard.
  * `POST /api/register-token`: Registers player UUID and encrypted token (invoked by the Minecraft mod).
  * `GET /api/cameras`: Returns all cameras associated with a user's token.
* **WebSocket Routes:**
  * `/api/mod/stream`: Connection endpoint for the Minecraft mod to stream `RegistryUpdateMessage`, `FrustumPayloadMessage`, and `EntityStreamMessage` (handled in [Websockets.kt](file:///C:/Users/Marcos/Documents/Dev/CRT-angarine/backend/src/main/kotlin/Websockets.kt)).
  * `/api/webapp/view`: Connection endpoint for the web dashboard. Receives camera subscriptions and forwards live frustum payloads and entity streams.
* **Storage:** Manages a flat-file JSON database (`auth_tokens.json`) via [TokenRegistry.kt](file:///C:/Users/Marcos/Documents/Dev/CRT-angarine/backend/src/main/kotlin/TokenRegistry.kt).
* **Standalone Server Launcher:** [EmbeddedServerLauncher.kt](file:///C:/Users/Marcos/Documents/Dev/CRT-angarine/backend/src/main/kotlin/EmbeddedServerLauncher.kt) compiles with the coroutine-based **CIO** engine to run Ktor directly inside the standalone mod without clashing with Netty classloaders.

### 3. Mod Modules (`mod-core`, `mod-client`, `mod-standalone`)
These modules contain the Minecraft NeoForge logic, divided to cleanly separate client and server features:
* **[:mod-core](mod-core)**: Handles core registrations:
  * **Blocks:** `Camera Block` (directional blockstates) and `Camera Station Block` (custom names + linked camera positions) registered via [ModBlocks.kt](file:///C:/Users/Marcos/Documents/Dev/CRT-angarine/mod-core/src/main/kotlin/me/orange/crtangarine/block/ModBlocks.kt).
  * **Items:** `Security Keycard Item` (stores the player profile UUID).
  * **Aiming Math & Raycasting:** Handled in [CameraStreamTask.kt](file:///C:/Users/Marcos/Documents/Dev/CRT-angarine/mod-core/src/main/kotlin/me/orange/crtangarine/aim/CameraStreamTask.kt).
  * **Streaming Client:** Handles real-time frustum block scanning (up to 32 blocks FOV) in [CameraStreamingClient.kt](file:///C:/Users/Marcos/Documents/Dev/CRT-angarine/mod-core/src/main/kotlin/me/orange/crtangarine/network/CameraStreamingClient.kt).
  * **SavedData Persistence**: Uses `StationRegistrySavedData` to keep track of placed stations globally, ensuring cameras survive chunk unloads.
* **[:mod-client](mod-client)**: Light wrapper compiling to the client mod. Registers client configs (`backendUri`).
* **[:mod-standalone](mod-standalone)**: Standalone wrapper compiling to the embedded server mod. Packages the backend + static web assets and boots Ktor on server startup.

### 4. [Webapp](webapp) Module
A React dashboard built using raw Three.js rendering techniques to display environment grids and track entities in a retro neon/CRT scanline shader interface.
* **Production Deployment:** This module compiles into a highly optimized, single-page application static bundle via Vite, which is automatically ingested and served by the `backend` module.

---

## Protocol & Live Streaming Lifecycle

```mermaid
sequenceDiagram
    participant Mod as Minecraft Mod Client
    participant Server as Ktor Backend Server
    participant Web as Webapp Dashboard

    Note over Web,Server: Web client connects to view camera
    Web->>Server: WS /api/webapp/view?token=<token>&cameraId=<id>
    Server->>Server: Validate token & increment watcher count (0 -> 1)
    Server->>Mod: CameraStreamCommand(cameraId, isActive = true)
    
    loop Active Streaming Loop
        Mod->>Mod: Calculate 70° Frustum cone up to 32 blocks
        Mod->>Mod: Scan blocks and track dynamic entities
        Mod->>Server: WS /api/mod/stream (TerrainFrustumPayload / EntityDeltaStream)
        Server->>Web: Broadcast payloads to web client
        Web->>Web: Update Three.js scene (InstancedMesh + neon paths)
    end

    Note over Web,Server: Web client closes tab or changes feed
    Web-xServer: WebSocket connection closed
    Server->>Server: Decrement watcher count (1 -> 0)
    Server->>Mod: CameraStreamCommand(cameraId, isActive = false)
    Note over Mod: Mod suspends frustum logic & packet stream
```

---

## Configurations & System Constants

### 1. Surveillance Math & Frustum Parameters
* **Field of View (FOV):** 70-degree horizontal and vertical projection cone.
* **Maximum Range:** 32-block radius from the camera coordinates.
* **Static Block Classification IDs:**
  | ID | Category | Examples / Representation in Webapp |
  |----|----------|-------------------------------------|
  | `0` | Air / Passable | Transparent (not rendered) |
  | `1` | Solid Obstacle | Translucent Voxel Mesh |
  | `2` | Hazard | Red glowing block (Lava / Fire) |
  | `3` | Interactable | Yellow wireframe (Doors / Buttons / Chests) |

### 2. Entity Radar Mappings
Dynamic entity coordinates are tracked and color-coded within the Three.js viewport:
* **PLAYER** -> 🔵 **Electric Blue**
* **MONSTER** -> 🔴 **Crimson Red**
* **PASSIVE** -> 🟢 **Matrix Green**
* **ITEM** -> 🟡 **Amber Yellow**

> Every tracked entity node projects a forward line vector showing its active gaze direction (`yaw` and `pitch`).

---

## Development & Orchestration Commands

### 1. Build the Complete Project
Compiles Vite production assets, runs the backend build, and packages both the client-only and standalone mod JARs.
```powershell
./gradlew build
```

### 2. Run the Backend Ktor Server (Separate Process)
Launches the standalone Ktor application on `http://localhost:8080` (requires a client-only mod to connect to it).
```powershell
./gradlew :backend:run
```

### 3. Launch the Minecraft Mod Client in Dev Mode
* **Client-Only mod**:
  ```powershell
  ./gradlew :mod-client:runClient
  ```
* **Standalone mod**:
  ```powershell
  ./gradlew :mod-standalone:runClient
  ```

### 4. Run Frontend in Hot-Reload Dev Mode
For rapid UI/CSS styling iterations without constantly rebuilding the Ktor fat JAR.
```bash
cd webapp
npm install
npm run dev
```

---

## Technical Highlights & Guidelines

* **Instanced Rendering:** All blocks parsed in [Viewport.tsx](webapp/src/components/Viewport.tsx) are rendered using a single `THREE.InstancedMesh` with translation matrices. This keeps GPU draw calls extremely low, avoiding CPU overhead on state updates.
* **On-Demand Performance:** The mod only runs heavy coordinate checking and frustum computations for cameras currently being watched. This preserves the server tickrate (TPS).
* **NBT Profile Binding:** The keycard burns physical player UUID metadata into station tiles, preventing unauthorised players from tampering with security terminals.