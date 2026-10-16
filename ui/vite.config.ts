/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type ProxyOptions } from 'vite'

/**
 * UI tarayıcıda çalışır, üç servise Vite'ın proxy'si üzerinden ulaşır: aynı origin, CORS ayarı gerekmez.
 * Adresler ortam değişkeniyle değiştirilebilir (bkz. README).
 */
export default defineConfig(({ mode }) => {
  const env = { ...process.env, ...loadEnv(mode, process.cwd(), '') }
  const service = (target: string, prefix: string, rewriteTo = ''): ProxyOptions => ({
    target,
    changeOrigin: true,
    rewrite: (path) => rewriteTo + path.slice(prefix.length),
  })
  // Yönetim uç noktaları (/admin/**) HTTP Basic ister; proxy ekler, şifre tarayıcıya gitmez.
  // Açık uç noktalar (/products, /catalog) başlığı görmezden gelir.
  const admin = `${env.ADMIN_USER ?? 'admin'}:${env.ADMIN_PASSWORD ?? 'admin'}`
  const proxy: Record<string, ProxyOptions> = {
    '/svc/backoffice': { ...service(env.BACKOFFICE_URL ?? 'http://localhost:8081', '/svc/backoffice'), auth: admin },
    '/svc/storefront': { ...service(env.STOREFRONT_URL ?? 'http://localhost:8082', '/svc/storefront'), auth: admin },
    // RabbitMQ yönetim API'si: kimlik bilgisi tarayıcıya hiç gitmez, proxy ekler.
    '/svc/rabbitmq': {
      ...service(env.RABBITMQ_URL ?? 'http://localhost:15672', '/svc/rabbitmq', '/api'),
      auth: `${env.RABBITMQ_USER ?? 'rabbitlab'}:${env.RABBITMQ_PASSWORD ?? 'rabbitlab'}`,
    },
  }
  return {
    plugins: [react()],
    server: { port: 5173, proxy },
    preview: { port: 4173, proxy },
    test: { environment: 'node' },
  }
})
