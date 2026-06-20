import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import Toast from './components/Toast.vue'
import './styles/global.less'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.component('GlobalToast', Toast)
app.mount('#app')
