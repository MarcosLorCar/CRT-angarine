* **Responsive Layout & Mobile UX Refinement**:
  * Integrated safe-area-inset boundaries to notch/status-bar offsets on mobile layout, preventing screen UI overlays.
  * Replaced static viewport heights with CSS dynamic small viewport units (`100svh`), allowing the browser toolbar and system buttons to scale contextually.
  * Docked expanded side drawers exactly below the header, preventing overlap/clutters.
  * Cleaned up baseline body container CSS to prevent empty top gutters above headers.
  * Streamlined sidebar controls with sticky footer disconnect action.
* **Centered Widescreen 16:9 Viewport**:
  * Set WebGL context to lock rendering to standard widescreen `16:9` aspect ratio.
  * Locked horizontal FOV to exactly `80°` (and vertical FOV to `50.53°`), avoiding screen rotation stretch distortion.
  * Centered live and offline noise canvases using CSS/flex layouts.
* **3D Visual Clarity & Grid Realism**:
  * Enabled `flatShading` to render block face triangles with uniform color shades, eliminating diagonal shadow seams.
  * Removed grid block spacing gaps by restoring dummy scale matrix setting to `1.0`.
  * Added a dedicated floating exit-fullscreen button always visible at the bottom-right in fullscreen mode.
