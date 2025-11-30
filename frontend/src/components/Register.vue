<template>
  <v-container class="fill-height" fluid>
    <v-row align="center" justify="center">
      <v-col cols="12" sm="8" md="6" lg="4">
        <v-card>
          <v-card-title class="text-h5 text-center pa-4">
            Criar Conta
          </v-card-title>
          <v-card-text>
            <v-form ref="form" v-model="valid" lazy-validation>
              <v-text-field
                v-model="name"
                :rules="nameRules"
                label="Nome"
                required
                prepend-inner-icon="mdi-account"
              ></v-text-field>

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
              ></v-text-field>

              <v-text-field
                v-model="confirmPassword"
                :rules="confirmPasswordRules"
                label="Confirmar Senha"
                required
                prepend-inner-icon="mdi-lock-check"
                :type="showPassword ? 'text' : 'password'"
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
                @click="register"
              >
                Registrar
              </v-btn>
            </v-form>
          </v-card-text>
          <v-card-actions>
            <v-spacer></v-spacer>
            <v-btn text to="/login">
              Já tem uma conta? Faça login
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import { orionUsersService } from '../services/orionUsers';
import { authService } from '../services/auth';

export default {
  name: 'Register',
  data() {
    return {
      valid: false,
      name: '',
      email: '',
      password: '',
      confirmPassword: '',
      showPassword: false,
      loading: false,
      error: null,
      nameRules: [
        v => !!v || 'Nome é obrigatório',
        v => (v && v.length >= 3) || 'Nome deve ter pelo menos 3 caracteres'
      ],
      emailRules: [
        v => !!v || 'Email é obrigatório',
        v => /.+@.+\..+/.test(v) || 'Email deve ser válido'
      ],
      passwordRules: [
        v => !!v || 'Senha é obrigatória',
        v => (v && v.length >= 6) || 'Senha deve ter pelo menos 6 caracteres'
      ],
      confirmPasswordRules: [
        v => !!v || 'Confirmação de senha é obrigatória',
        v => v === this.password || 'Senhas não coincidem'
      ]
    };
  },
  methods: {
    async register() {
      if (!this.$refs.form.validate()) {
        return;
      }

      this.loading = true;
      this.error = null;

      try {
        // Usar createAuthenticate para autenticação automática
        const response = await orionUsersService.createAndAuthenticate(
          this.name,
          this.email,
          this.password
        );

        // Salvar token e dados do usuário
        if (response.authentication && response.authentication.token) {
          authService.setToken(response.authentication.token);
          if (response.user) {
            authService.setUser(response.user);
          }
          
          // Redirecionar para chat
          this.$router.push('/chat');
        } else {
          this.error = 'Erro ao criar conta. Tente novamente.';
        }
      } catch (error) {
        console.error('Erro ao registrar:', error);
        this.error = error.response?.data?.message || 'Erro ao criar conta. Tente novamente.';
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>

