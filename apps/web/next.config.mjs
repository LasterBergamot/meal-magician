/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Transpile the shared workspace package (it ships TypeScript source, not a build).
  transpilePackages: ["@meal-magician/core"],
};

export default nextConfig;
