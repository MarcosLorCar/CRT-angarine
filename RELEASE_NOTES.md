* **Fix ClassLoader Resource Resolution in Standalone Mod**:
  * Implemented a custom ClassLoader router (`get("/{path...}")`) in the Ktor backend to stream resources directly from the mod jar.
  * Bypassed Ktor's high-level `singlePageApplication` classpath serving which failed inside NeoForge because transforming classloaders do not index directories.
* **Auto-Cleanup of Legacy ServiceWorkers**:
  * Integrated the `Clear-Site-Data: "cache"` response header across all routes to automatically trigger the browser to unregister any legacy service workers and clear cache.
  * Added a self-unregistering service worker script (`sw.js`) and inline HTML checks to clean up lingering `1.0.3` service workers that were causing `404` or unexpected errors on page load.
