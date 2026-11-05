import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'dayjs/locale/es'
import './styles/main.css'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { setUnauthorizedHandler } from './api/http'
import { useUserStore } from './stores/user'
import { useAdminStore } from './stores/admin'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(i18n)
app.use(ElementPlus)
app.use(router)

// 401 from the API: clear the matching session and send the person to the right login page.
setUnauthorizedHandler((area) => {
  const current = router.currentRoute.value
  if (area === 'admin') {
    useAdminStore(pinia).clear()
    if (current.name !== 'admin-login') {
      void router.push({ name: 'admin-login', query: { redirect: current.fullPath, expired: '1' } })
    }
  } else {
    useUserStore(pinia).clear()
    if (current.name !== 'login') {
      void router.push({ name: 'login', query: { redirect: current.fullPath, expired: '1' } })
    }
  }
})

app.mount('#app')
