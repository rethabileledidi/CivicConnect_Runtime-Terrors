// @lovable.dev/vite-tanstack-config already includes the following — do NOT add them manually
// or the app will break with duplicate plugins:
//   - TanStack devtools (dev-only, first), tanstackStart, viteReact, tailwindcss, tsConfigPaths,
//     nitro (build-only using cloudflare as a default target), VITE_* env injection, @ path alias,
//     React/TanStack dedupe, error logger plugins, and sandbox detection (port/host/strictPort).
// You can pass additional config via defineConfig({ vite: { ... }, etc... }) if needed.
import { defineConfig } from "@lovable.dev/vite-tanstack-config";

// The Java backend runs on Tomcat (default http://localhost:8081, context path /civicconnect).
// In development, the Vite dev server forwards /api/* to it, so the browser only talks to one
// origin: no CORS set-up, and the session cookie is sent automatically.
//   browser  /api/requests  ->  http://localhost:8081/civicconnect/api/requests
// Override the target with CIVIC_API_TARGET=http://host:port if Tomcat runs elsewhere.
const apiTarget = process.env.CIVIC_API_TARGET ?? "http://localhost:8081";

export default defineConfig({
  tanstackStart: {
    // Redirect TanStack Start's bundled server entry to src/server.ts (our SSR error wrapper).
    // nitro/vite builds from this
    server: { entry: "server" },
  },
  vite: {
    server: {
      proxy: {
        "/api": {
          target: apiTarget,
          changeOrigin: true,
          rewrite: (path: string) => "/civicconnect" + path,
          // Tomcat issues the session cookie for path /civicconnect; the browser sees /api.
          cookiePathRewrite: { "/civicconnect": "/" },
        },
      },
    },
  },
});
