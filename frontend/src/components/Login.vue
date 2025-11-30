<template>
  <v-container class="fill-height" fluid>
    <v-row align="center" justify="center">
      <v-col cols="12" sm="8" md="6" lg="4">
        <v-card>
          <v-card-title class="text-h5 text-center pa-4">
            Login
          </v-card-title>
          <v-card-text>
            <v-form ref="form" v-model="valid" lazy-validation>
              <v-text-field
                v-model="email"
                :rules="emailRules"
                label="Email"
                required
                prepend-inner-icon="mdi-email"
                type="email"
              ></v-text-field>

              <v-text-field
                v-model="password"
                :rules="passwordRules"
                label="Senha"
                required
                prepend-inner-icon="mdi-lock"
                :type="showPassword ? 'text' : 'password'"
                :append-inner-icon="showPassword ? 'mdi-eye' : 'mdi-eye-off'"
                @click:append-inner="showPassword = !showPassword"
                @keyup.enter="login"
              ></v-text-field>

              <v-alert v-if="error" type="error" class="mt-4">
                {{ error }}
              </v-alert>

              <v-btn
                :disabled="!valid || loading"
                :loading="loading"
                color="primary"
                block
                class="mt-4"
                @click="login"
              >
                Entrar
              </v-btn>
            </v-form>
          </v-card-text>
          <v-card-actions>
            <v-spacer></v-spacer>
            <v-btn text to="/register">
              Não tem uma conta? Registre-se
            </v-btn>
          </v-card-actions>
        </v-card>

        <!-- Componente 2FA se necessário -->
        <TwoFactorAuth
          v-if="requires2FA"
          :email="email"
          @authenticated="handle2FAAuthenticated"
          @cancel="requires2FA = false"
        />
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import { orionUsersService } from '../services/orionUsers';
import { authService } from '../services/auth';
import TwoFactorAuth from './TwoFactorAuth.vue';

export default {
  name: 'Login',
  components: {
    TwoFactorAuth
  },
  data() {
    return {
      valid: false,
      email: '',
      password: '',
      showPassword: false,
      loading: false,
      error: null,
      requires2FA: false,
      emailRules: [
        v => !!v || 'Email é obrigatório',
        v => /.+@.+\..+/.test(v) || 'Email deve ser válido'
      ],
      passwordRules: [
        v => !!v || 'Senha é obrigatória'
      ]
    };
  },
  methods: {
    async login() {
      if (!this.$refs.form.validate()) {
        return;
      }

      this.loading = true;
      this.error = null;

      try {
        const response = await orionUsersService.login(this.email, this.password);

        // Verificar se 2FA é necessário
        if (response.requires2FA) {
          this.requires2FA = true;
          this.loading = false;
          return;
        }

        // Login bem-sucedido
        if (response.authentication && response.authentication.token) {
          authService.setToken(response.authentication.token);
          if (response.user) {
            authService.setUser(response.user);
          }
          
          this.$router.push('/chat');
        } else {
          this.error = 'Erro ao fazer login. Tente novamente.';
        }
      } catch (error) {
        console.error('Erro ao fazer login:', error);
        this.error = error.response?.data?.message || 'Erro ao fazer login. Verifique suas credenciais.';
      } finally {
        this.loading = false;
      }
    },

    handle2FAAuthenticated(token, user) {
      authService.setToken(token);
      if (user) {
        authService.setUser(user);
      }
      this.$router.push('/chat');
    }
  }
};
</script>

