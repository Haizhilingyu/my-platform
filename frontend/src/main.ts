import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { vPermission } from '@/shared/directives/permission'
import i18n from './i18n'
import './styles/index.css'

const app = createApp(App)
app.use(createPinia())
app.use(i18n)
app.use(router)
app.directive('permission', vPermission)
app.mount('#app')
