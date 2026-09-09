import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    host: true,
    open: false,
    watch: {
      // 某些编辑器/工具用"临时目录 + 原子替换"写文件，会在 src 下留下
      // .xxx.tmpdir/xxx.tmp，vite 的 watcher 去 watch 这些瞬间消失的文件会
      // 直接抛 EBUSY 把 dev server 干掉。忽略掉即可。
      ignored: ['**/.*.tmpdir/**', '**/*.tmp']
    },
    proxy: {
      // 后端单体应用：SpringBoot3 @ 8090（8080 常被其他本地服务占用），统一前缀 /api
      '/api': {
        target: 'http://127.0.0.1:8090',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        // 拆包：Element Plus 体积大，单独成块，避免业务代码改动导致整包失效
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          element: ['element-plus', '@element-plus/icons-vue']
        }
      }
    }
  }
})
