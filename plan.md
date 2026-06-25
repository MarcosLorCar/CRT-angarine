# Implementation Plan: Minecraft Surveillance System (MSS)

This document serves as the master orchestration blueprint for AI engineering agents, organized into sequential, isolated implementation steps. Do not attempt to implement the entire plan at once; execute the milestones in numerical order.

> [!IMPORTANT]
> **Workflow Constraints**: Do NOT run Gradle execution/compilation commands (like `./gradlew.bat compileKotlin` or `build`) after every tweak. Only run them if the user explicitly reports a crash or after a milestone has been completed.

---

## 🛠️ Module Architecture Overview

[ Minecraft Mod (Ktor Client) ] 
               │ ▲
               │ │ (Reactive Toggle Signals FROM Server)
               ▼ │ (Pushes Frustum Data & Entity Streams TO Server ONLY when requested)
     [ Ktor Backend Server ]
               ▲
               │ (Serves compiled HTML/JS & streams live JSON data)
               ▼
[ Compiled React Webapp Dashboard (Vanilla Three.js User View) ]

---

## 📋 Milestone 1: Shared Data & Flat-File Authentication (Core Stack)
*Goal: Establish data structures and security foundations before building any gameplay elements.*

### 1.1 Tasks
1. In the `:shared` Kotlin module, implement the following data models using `@Serializable`:
   * `AuthTokenPacket` (Player UUID, encrypted secret token string, list of assigned station names).
   * `TerrainFrustumPayload` (Camera ID, structural coordinate data lists).
   * `EntityDeltaStream` (Camera ID, high-frequency array containing positional coordinates, classifications, and view angles).
   * `CameraStreamCommand` (Camera ID, boolean field `isActive` to toggle the mod's harvesting loop on/off).
2. In the `Ktor Server` module, implement a `TokenRegistry` manager class.
   * This class must maintain a thread-safe `ConcurrentHashMap<String, String>` mapping Player UUIDs (Keys) to unique Network Tokens (Values).
   * Implement automated hooks to read from and write to a local flat-file storage point (`auth_tokens.json`) whenever changes occur.

---

## ⛏️ Milestone 2: Base Mod Registries & Keycard Identity
*Goal: Implement the mod items and establish the player identity binding system.*

### 2.1 Tasks
1. In the `Minecraft Mod` module, register the three core elements:
   * `Camera Block` (Directional orientation properties).
   * `Camera Station Block` (Block Entity containing empty owner UUID and custom name string variables).
   * `Security Keycard Item`.
2. Code a custom recipe / item creation handler for the `Security Keycard`. The exact moment it is generated, capture the creating player's Profile UUID and bake it into the item's persistent NBT compound data.
3. Code an item interaction handler: Right-clicking the baked `Security Keycard` in an inventory opens a clean, minimal layout text box displaying the player's secret network token and an option to copy it directly to the host computer's clipboard.

---

## 🔒 Milestone 3: Hardware Burning & Station Customization
*Goal: Implement ownership protection and physical deployment parameters.*

### 3.1 Tasks
1. Code an interaction filter for the `Camera Station Block`:
   * If the station block tile data contains an unassigned/empty owner block, permit anyone to open its user interface.
   * If it contains an assigned owner UUID, instantly reject interaction packets from any player whose profile ID does not match.
2. Design the **Station Burning GUI**:
   * Include an inventory slot designed strictly to read a player-bound `Security Keycard`.
   * Add a distinct action button labeled **"Burn Identity"**. Clicking this button flashes the keycard's structural owner data directly to the physical block entity tiles permanently.
3. Once burned, expose a text field widget allowing the verified owner to assign a custom alpha-numeric identifier name (e.g., "Vault Room") to that specific station block.

---

## 🛰️ Milestone 4: Camera Linkage & Possession Aiming Interface
*Goal: Connect cameras to stations and build the spatial perspective aiming controller.*

### 4.1 Tasks
1. Implement a method to link physical `Camera Blocks` to a burned `Camera Station`.
2. Update the main Station Dashboard UI to show a dynamic scrolling grid listing all successfully linked camera entities.
3. For every listed camera card component, render an explicit action button labeled **"Aim Camera"**.
4. Clicking this button handles the **Aim Mode pipeline**:
   * Fire a server packet forcing client entity view possession onto the targeted Camera Block coordinates.
   * Lock the user's positional coordinates safely back at the station console hull while allowing wide look-angle rotation.
   * On mouse left-click, grab the active client `pitch` and `yaw` values, commit them directly to the target Camera Block tile states, sever possession vectors, and return the player to their bodily station console view.

---

## 📡 Milestone 5: On-Demand Frustum Volume Math & Ktor Streaming Client
*Goal: Extract real-time environmental matrix updates and pipe them to the backend server ONLY when an active stream request is signaled.*

### 5.1 Tasks
1. Implement an inbound listener within the Mod's Ktor client to handle incoming `CameraStreamCommand` packets from the Ktor server.
2. Maintain an internal registry of "active streaming cameras" on the Minecraft server side. If a camera is marked inactive (`isActive = false`), bypass all math calculations and packet generation for that block entirely.
3. For active cameras, execute the geometric frustum math filter based on Camera position, stored orientation angles, and a static 70-degree viewing projection cone up to a maximum 32-block tracking threshold.
4. Harvest static block states within this spatial cone matrix. Classify them into simplified IDs (0 = Air/Passable, 1 = Solid Obstacle, 2 = Hazard/Lava, 3 = Interactable/Doors).
5. Gather real-time dynamic positional coordinates for entities within this cone segment (`PLAYER`, `MONSTER`, `PASSIVE`, `ITEM`).
6. Stream these synchronized data packets via the open WebSocket connection to the backend server.

---

## 🚀 Milestone 6: Ktor Reactive Server Sockets & Web App Deployment
*Goal: Receive data requests from the webapp, signal the mod to start/stop data streams, and route traffic securely.*

### 6.1 Tasks
1. Configure a Ktor WebSocket pipeline containing two explicit routing lanes:
   * Inbound Channel (`/api/mod/stream`): Interchanges data with the Minecraft mod.
   * Outbound Channel (`/api/webapp/view`): Interchanges data with web browsers.
2. Build an authentication interceptor step on the outbound channel: Read the client's handshake request parameters, check it against the local flat-file `TokenRegistry` compiled in Milestone 1, and drop connections immediately if credentials fail.
3. **Implement Stream Orchestration:** * When a web client opens a specific camera dashboard feed, Ktor must send an outbound `CameraStreamCommand(cameraId, isActive = true)` packet to the Minecraft mod to turn the camera data collection on.
   * Track the connection state of the web client. When they disconnect, close the tab, or switch feeds, evaluate if any other client is watching that camera. If views hit 0, send `CameraStreamCommand(cameraId, isActive = false)` to stop the mod from harvesting data.
4. Configure static asset host parameters to serve a production-ready single-page React app folder asset straight from the root path.

---

## 💻 Milestone 7: React + Vanilla Three.js CRT Surveillance Matrix
*Goal: Build the visual terminal client using raw Three.js for optimal performance to decode coordinates into a stylized radar visualization.*

### 7.1 Tasks
1. Create a secure login screen requiring the user's secret string token. Save validated configurations to local storage variables.
2. Build a station selector dropdown element feeding into a core HTML `<canvas>` reference managed via a React `useRef` hook. Selecting or changing a camera must fire the server-side activation request created in Milestone 6.
3. **Environment Painter (Vanilla Three.js):** Initialize a raw Three.js scene engine inside the canvas wrapper. Utilize `THREE.InstancedMesh` to map out incoming raw static block coordinate arrays efficiently without causing React re-render lag. Programmatically render them as simple translucent or green wireframe voxel box primitives.
4. **Entity Radar Painter:** Update dynamic entity nodes smoothly using an imperative animation loop. Map real-time entity updates into programmatic neon indicators based on a strict color key legend visible on the UI:
   * `PLAYER` -> Electric Blue
   * `MONSTER` -> Crimson Red
   * `PASSIVE` -> Matrix Green
   * `ITEM` -> Amber Yellow
   * Attach a projection line vector pointing out from each tracking node showing its active orientation.
5. **Retro Shading Pipeline:** Set up a Three.js `EffectComposer` to apply post-processing shaders directly: Inject scanline intervals, subtle screen-flicker noise, chromatic aberrations, and a curved phosphor screen display matrix.