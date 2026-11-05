import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Styles for Vant's function APIs (components are auto-imported with their CSS by unplugin-vue-components).
import 'vant/es/toast/style'
import 'vant/es/dialog/style'
import 'vant/es/notify/style'
import './styles/global.css'
import App from './App.vue'
import { router } from './router'
import { i18n, currentLocale } from './i18n'
import { onUnauthorized, setLocaleProvider } from './api/client'
import { useAuthStore } from './stores/auth'
import { vTestid } from './directives/testid'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(i18n)
app.use(router)
app.directive('testid', vTestid)

setLocaleProvider(currentLocale)
onUnauthorized(() => {
  const auth = useAuthStore(pinia)
  auth.clear()
  const current = router.currentRoute.value
  if (current.meta.auth) {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
