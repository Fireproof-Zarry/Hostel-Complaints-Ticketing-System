import { readFileSync } from 'node:fs'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'
import tailwindcss from '@tailwindcss/vite'

const SW_PATH = 'firebase-messaging-sw.js'
const SW_TEMPLATE = new URL('./src/notifications/firebase-messaging-sw.template.js', import.meta.url)

// Creates the Firebase service worker from the same .env values as src/firebase.js.
function firebaseMessagingSw(env) {
  const config = {
    apiKey: env.VITE_FIREBASE_API_KEY,
    authDomain: env.VITE_FIREBASE_AUTH_DOMAIN,
    projectId: env.VITE_FIREBASE_PROJECT_ID,
    storageBucket: env.VITE_FIREBASE_STORAGE_BUCKET,
    messagingSenderId: env.VITE_FIREBASE_MESSAGING_SENDER_ID,
    appId: env.VITE_FIREBASE_APP_ID,
  }

  const buildWorker = () => {
    const missing = Object.keys(config).filter((key) => !config[key])

    // A worker without these would build, then fail silently in the browser.
    if (missing.length > 0) {
      throw new Error(`Missing Firebase values for ${SW_PATH}: ${missing.join(', ')}`)
    }

    return readFileSync(SW_TEMPLATE, 'utf8').replace(
      '__FIREBASE_CONFIG__',
      () => JSON.stringify(config)
    )
  }

  return {
    name: 'firebase-messaging-sw',
    configureServer(server) {
      server.middlewares.use(`/${SW_PATH}`, (_request, response) => {
        response.setHeader('Content-Type', 'text/javascript')
        response.end(buildWorker())
      })
    },
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: SW_PATH, source: buildWorker() })
    },
  }
}

export default defineConfig(({ mode }) => ({
  plugins: [
    react(),
    tailwindcss(),
    firebaseMessagingSw(loadEnv(mode, process.cwd(), 'VITE_')),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'Hostel Complaints Ticketing System',
        short_name: 'Hostel Complaints',
        description: 'Hostel Complaints Ticketing System',
        theme_color: '#ffffff',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
      },
    }),
  ],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/auth': 'http://localhost:8080'
    }
  }
}))
