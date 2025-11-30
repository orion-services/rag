import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { createVuetify } from 'vuetify';
import { createRouter, createWebHistory } from 'vue-router';
import 'vuetify/styles';
import '@mdi/font/css/materialdesignicons.css';
import './style.css';

import App from './App.vue';
import { useAuthStore } from './stores/auth';
import router from './router';

// Configurar Vuetify
const vuetify = createVuetify({
  theme: {
    defaultTheme: 'light'
  }
});

// Criar Pinia
const pinia = createPinia();

// Guard de autenticação
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore();
  
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next('/login');
  } else {
    next();
  }
});

// Criar e montar aplicação
const app = createApp(App);
app.use(pinia);
app.use(router);
app.use(vuetify);

app.mount('#app');

