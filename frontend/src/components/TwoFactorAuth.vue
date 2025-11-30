<template>
  <v-card class="mt-4">
    <v-card-title class="text-h6">
      Autenticação em Dois Fatores
    </v-card-title>
    <v-card-text>
      <p>Por favor, insira o código de 6 dígitos do seu aplicativo autenticador.</p>
      <v-text-field
        v-model="code"
        label="Código 2FA"
        required
        prepend-inner-icon="mdi-shield-lock"
        maxlength="6"
        @keyup.enter="validate"
      ></v-text-field>

      <v-alert v-if="error" type="error" class="mt-4">
        {{ error }}
      </v-alert>

      <v-btn
        :disabled="!code || code.length !== 6 || loading"
        :loading="loading"
        color="primary"
        block
        class="mt-4"
        @click="validate"
      >
        Validar
      </v-btn>
      <v-btn
        text
        block
        class="mt-2"
        @click="$emit('cancel')"
      >
        Cancelar
      </v-btn>
    </v-card-text>
  </v-card>
</template>

<script>
import { orionUsersService } from '../services/orionUsers';

export default {
  name: 'TwoFactorAuth',
  props: {
    email: {
      type: String,
      required: true
    }
  },
  emits: ['authenticated', 'cancel'],
  data() {
    return {
      code: '',
      loading: false,
      error: null
    };
  },
  methods: {
    async validate() {
      if (this.code.length !== 6) {
        this.error = 'O código deve ter 6 dígitos';
        return;
      }

      this.loading = true;
      this.error = null;

      try {
        const response = await orionUsersService.loginWith2FA(this.email, this.code);

        if (response.authentication && response.authentication.token) {
          this.$emit('authenticated', response.authentication.token, response.user);
        } else {
          this.error = 'Código inválido. Tente novamente.';
        }
      } catch (error) {
        console.error('Erro ao validar 2FA:', error);
        this.error = error.response?.data?.message || 'Código inválido. Tente novamente.';
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>

