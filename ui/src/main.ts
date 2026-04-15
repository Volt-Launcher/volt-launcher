import { createApp } from 'vue'
import App from './App.vue'
import "@/assets/base.css"
import router from './router'
import VoltUI from '@volt-launcher/volt-ui'

const app = createApp(App)

app.use(router)
app.use(VoltUI)

app.mount('#app')
