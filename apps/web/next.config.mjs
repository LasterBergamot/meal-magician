import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Transpile the shared workspace package (it ships TypeScript source, not a build).
  transpilePackages: ["@meal-magician/core"],
  // Emit a self-contained server bundle (node server.js) for the Podman image.
  // Only enabled during the container build: the standalone trace step creates
  // symlinks, which native Windows blocks without Developer Mode/admin. Gating
  // on this env var keeps `pnpm build` working on Windows while still producing
  // the standalone output on Linux/WSL and inside the Containerfile.
  output: process.env.BUILD_STANDALONE === "true" ? "standalone" : undefined,
  // In a monorepo, trace files from the workspace root so the standalone output
  // includes the shared `packages/*`, not just apps/web.
  outputFileTracingRoot: path.join(__dirname, "../../"),
};

export default nextConfig;
