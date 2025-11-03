import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import path from 'node:path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@fullcalendar/daygrid/main.css': path.resolve(__dirname, 'node_modules/@fullcalendar/daygrid/index.css'),
      '@fullcalendar/timegrid/main.css': path.resolve(__dirname, 'node_modules/@fullcalendar/timegrid/index.css')
    }
  }
})
