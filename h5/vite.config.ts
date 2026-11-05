import { fileURLToPath, URL } from 'node:url'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from '@vant/auto-import-resolver'

// Same policy nginx sends in production (deploy/nginx/templates/default.conf.template),
// applied to `vite preview` so local checks fail loudly if anything tries to reach another host.
const CSP =
  "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; " +
  "font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"

/**
 * Zero external hosts in the engineer build (they must load from mainland China without a VPN):
 *  - Vant's icon @font-face ships an inlined woff2 (data: URI) plus a woff fallback on
 *    //at.alicdn.com. Every browser we target supports woff2, so the remote fallback is dropped.
 *  - Vue's production runtime builds an error "info" string "https://vuejs.org/error-reference/#..."
 *    (handed to errorHandler, never fetched); the scheme is removed so no URL remains in the bundle.
 * scripts/check-no-external.mjs (npm run check:external) verifies the result after every build.
 */
function noExternalUrls(): Plugin {
  const REMOTE_FONT_SRC = /,\s*url\(\s*(['"]?)(?:https?:)?\/\/[^)]*?\1\s*\)\s*format\([^)]*\)/g
  const VUE_ERROR_REF = 'https://vuejs.org/error-reference/'
  return {
    name: 'qiao:no-external-urls',
    enforce: 'pre',
    transform(code, id) {
      if (!id.includes('node_modules') || !/\.css(?:$|\?)/.test(id)) return null
      const out = code.replace(REMOTE_FONT_SRC, '')
      return out === code ? null : { code: out, map: null }
    },
    renderChunk(code) {
      if (!code.includes(VUE_ERROR_REF)) return null
      return { code: code.split(VUE_ERROR_REF).join('vuejs.org/error-reference/'), map: null }
    },
  }
}

const apiProxy = {
  '/api': { target: 'http://localhost:8080', changeOrigin: false },
}

export default defineConfig({
  base: '/m/',
  plugins: [
    noExternalUrls(),
    vue(),
    // On-demand Vant components + their CSS (only what is used ends up in the bundle).
    Components({ resolvers: [VantResolver()], dts: 'src/components.d.ts' }),
  ],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  define: {
    __VUE_OPTIONS_API__: true,
    __VUE_PROD_DEVTOOLS__: false,
    __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: false,
    __VUE_I18N_FULL_INSTALL__: true,
    __VUE_I18N_LEGACY_API__: false,
    __INTLIFY_PROD_DEVTOOLS__: false,
  },
  server: {
    port: 5174,
    strictPort: true,
    proxy: apiProxy,
  },
  preview: {
    port: 4174,
    headers: { 'Content-Security-Policy': CSP },
    proxy: apiProxy,
  },
  build: {
    // WeChat / older Android WebViews in mainland China.
    target: 'es2019',
    cssTarget: 'chrome61',
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (id.includes('node_modules/vant') || id.includes('node_modules/@vant')) return 'vant'
          if (id.includes('node_modules')) return 'vendor'
          return undefined
        },
      },
    },
  },
})
