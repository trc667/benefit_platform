import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import 'element-plus/dist/index.css'
import '@/styles/index.css'
import '@/styles/layout.css'

import App from './App.vue'
import router from './router'
import { installGlobalErrorHandler } from '@/utils/errorHandler'

const app = createApp(App)

// Element Plus 图标全量注册，模板里可直接 <el-icon><Coin /></el-icon>
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn, size: 'default' })

// 全局异常兜底：任何组件异常都不至于白屏
installGlobalErrorHandler(app)

app.mount('#app')
