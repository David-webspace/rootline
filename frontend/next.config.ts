import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  // 純導向的路徑用設定處理，不必為了 redirect() 渲染一個空頁面
  async redirects() {
    return [
      { source: "/", destination: "/dashboard", permanent: false },
      { source: "/dashboard", destination: "/dashboard/org-tree", permanent: false },
    ];
  },
};

export default nextConfig;
