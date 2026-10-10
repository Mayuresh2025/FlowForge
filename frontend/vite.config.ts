import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({ plugins: [react()], server: { port: 5173, proxy: { '/api': 'http://localhost:8080', '/actuator': 'http://localhost:8080', '/v3/api-docs': 'http://localhost:8080', '/swagger-ui.html': 'http://localhost:8080', '/swagger-ui': 'http://localhost:8080' } } });
