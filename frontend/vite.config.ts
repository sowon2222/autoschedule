import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import path from 'node:path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173, // Vite 개발 서버 포트 (기본값)
    strictPort: false, // 포트가 사용 중이면 다음 포트 사용
    host: true // 외부 접근 허용
  },
  resolve: {
    alias: {
      '@fullcalendar/daygrid/main.css': path.resolve(__dirname, 'node_modules/@fullcalendar/daygrid/index.css'),
      '@fullcalendar/timegrid/main.css': path.resolve(__dirname, 'node_modules/@fullcalendar/timegrid/index.css')
    }
  },
  define: {
    global: 'window'
  }
})
