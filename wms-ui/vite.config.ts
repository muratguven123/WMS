import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const coreProxy = { target: 'http://localhost:8081', changeOrigin: true }
const localizationProxy = { target: 'http://localhost:8082', changeOrigin: true }
const financeProxy = { target: 'http://localhost:8083', changeOrigin: true }
const billingProxy = { target: 'http://localhost:8084', changeOrigin: true }
const integrationProxy = { target: 'http://localhost:8085', changeOrigin: true }
const inboundProxy = { target: 'http://localhost:8086', changeOrigin: true }
const inventoryProxy = { target: 'http://localhost:8087', changeOrigin: true }
const outboundProxy = { target: 'http://localhost:8088', changeOrigin: true }

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/auth': coreProxy,
      '/api/users': coreProxy,
      '/api/org': coreProxy,
      '/api/admin': coreProxy,
      '/api/address': coreProxy,
      '/api/ui': coreProxy,
      '/api/process-config': coreProxy,
      '/api/approvals': coreProxy,
      '/api/locations': coreProxy,
      '/api/audit': coreProxy,
      '/api/stocks': coreProxy,
      '/api/v1': localizationProxy,
      '/api/addresses': localizationProxy,
      '/api/rates': financeProxy,
      '/api/taxes': financeProxy,
      '/api/finance': financeProxy,
      '/api/billing': billingProxy,
      '/api/integrations': integrationProxy,
      '/api/inbound': inboundProxy,
      '/api/inventory': inventoryProxy,
      '/api/picking': outboundProxy,
      '/api/packing': outboundProxy,
      '/api/shipping': outboundProxy,
      '/ws': { target: 'http://localhost:8089', changeOrigin: true, ws: true },
    },
  },
})
