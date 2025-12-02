<template>
  <v-container>
    <v-row>
      <v-col cols="12">
        <v-card>
          <v-card-title>Configuração de Autenticação em Dois Fatores</v-card-title>
          <v-card-text>
            <v-alert v-if="message" :type="messageType" class="mb-4">
              {{ message }}
            </v-alert>

            <!-- Se 2FA não está habilitado -->
            <div v-if="!twoFAEnabled">
              <p class="mb-4">Para habilitar a autenticação em dois fatores, você precisa:</p>
              <ol>
                <li>Fornecer seu email e senha para gerar o QR code</li>
                <li>Escanear o QR code com um aplicativo autenticador (Google Authenticator, Authy, etc.)</li>
                <li>Inserir o código gerado pelo aplicativo para validar</li>
              </ol>

              <v-form ref="qrForm" v-model="qrFormValid" class="mt-4">
                <v-text-field
                  v-model="qrEmail"
                  label="Email"
                  required
                  prepend-inner-icon="mdi-email"
                  :rules="emailRules"
                ></v-text-field>

                <v-text-field
                  v-model="qrPassword"
                  label="Senha"
                  required
                  prepend-inner-icon="mdi-lock"
                  :type="showPassword ? 'text' : 'password'"
                  :append-inner-icon="showPassword ? 'mdi-eye' : 'mdi-eye-off'"
                  @click:append-inner="showPassword = !showPassword"
                  :rules="passwordRules"
                ></v-text-field>

                <v-btn
                  :disabled="!qrFormValid || loadingQR"
                  :loading="loadingQR"
                  color="primary"
                  @click="generateQRCode"
                >
                  Gerar QR Code
                </v-btn>
              </v-form>

              <!-- Exibir QR Code -->
              <div v-if="qrCodeUrl" class="mt-4 text-center">
                <p class="mb-2">Escaneie este QR code com seu aplicativo autenticador:</p>
                <img :src="qrCodeUrl" alt="QR Code 2FA" style="max-width: 300px;" />
                
                <v-form ref="validateForm" v-model="validateFormValid" class="mt-4">
                  <v-text-field
                    v-model="validationCode"
                    label="Código de 6 dígitos"
                    required
                    prepend-inner-icon="mdi-shield-lock"
                    maxlength="6"
                    :rules="codeRules"
                  ></v-text-field>

                  <v-btn
                    :disabled="!validateFormValid || loadingValidate"
                    :loading="loadingValidate"
                    color="success"
                    @click="validateCode"
                  >
                    Validar e Habilitar 2FA
                  </v-btn>
                </v-form>
              </div>
            </div>

            <!-- Se 2FA está habilitado -->
            <div v-else>
              <v-alert type="success" class="mb-4">
                2FA está habilitado para sua conta.
              </v-alert>

              <v-form ref="settingsForm" v-model="settingsFormValid">
                <v-checkbox
                  v-model="require2FAForBasicLogin"
                  label="Exigir 2FA para login com email/senha"
                ></v-checkbox>

                <v-checkbox
                  v-model="require2FAForSocialLogin"
                  label="Exigir 2FA para login social"
                ></v-checkbox>

                <v-btn
                  :disabled="!settingsFormValid || loadingSettings"
                  :loading="loadingSettings"
                  color="primary"
                  @click="updateSettings"
                >
                  Salvar Configurações
                </v-btn>
              </v-form>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import { orionUsersService } from '../services/orionUsers';
import { authService } from '../services/auth';

export default {
  name: 'TwoFactorSettings',
  data() {
    return {
      twoFAEnabled: false, // TODO: Verificar status real do 2FA
      qrFormValid: false,
      validateFormValid: false,
      settingsFormValid: true,
      qrEmail: '',
      qrPassword: '',
      showPassword: false,
      qrCodeUrl: null,
      validationCode: '',
      require2FAForBasicLogin: false,
      require2FAForSocialLogin: false,
      loadingQR: false,
      loadingValidate: false,
      loadingSettings: false,
      message: null,
      messageType: 'info',
      emailRules: [
        v => !!v || 'Email é obrigatório',
        v => /.+@.+\..+/.test(v) || 'Email deve ser válido'
      ],
      passwordRules: [
        v => !!v || 'Senha é obrigatória'
      ],
      codeRules: [
        v => !!v || 'Código é obrigatório',
        v => (v && v.length === 6) || 'Código deve ter 6 dígitos'
      ]
    };
  },
  methods: {
    async generateQRCode() {
      if (!this.$refs.qrForm.validate()) {
        return;
      }

      this.loadingQR = true;
      this.message = null;

      try {
        const blob = await orionUsersService.getQRCode(this.qrEmail, this.qrPassword);
        this.qrCodeUrl = URL.createObjectURL(blob);
        this.message = 'QR code gerado com sucesso. Escaneie com seu aplicativo autenticador.';
        this.messageType = 'success';
      } catch (error) {
        console.error('Erro ao gerar QR code:', error);
        this.message = error.response?.data?.message || 'Erro ao gerar QR code. Verifique suas credenciais.';
        this.messageType = 'error';
      } finally {
        this.loadingQR = false;
      }
    },

    async validateCode() {
      if (!this.$refs.validateForm.validate()) {
        return;
      }

      this.loadingValidate = true;
      this.message = null;

      try {
        const response = await orionUsersService.validate2FA(
          this.qrEmail,
          this.qrPassword,
          this.validationCode
        );

        if (response.authentication && response.authentication.token) {
          // 2FA habilitado com sucesso
          this.twoFAEnabled = true;
          this.message = '2FA habilitado com sucesso!';
          this.messageType = 'success';
          this.qrCodeUrl = null;
          this.validationCode = '';
        } else {
          this.message = 'Código inválido. Tente novamente.';
          this.messageType = 'error';
        }
      } catch (error) {
        console.error('Erro ao validar código:', error);
        this.message = error.response?.data?.message || 'Código inválido. Tente novamente.';
        this.messageType = 'error';
      } finally {
        this.loadingValidate = false;
      }
    },

    async updateSettings() {
      this.loadingSettings = true;
      this.message = null;

      try {
        const token = authService.getToken();
        const user = authService.getUser();
        const email = user?.email || this.qrEmail;

        await orionUsersService.update2FASettings(
          email,
          this.require2FAForBasicLogin,
          this.require2FAForSocialLogin,
          token
        );

        this.message = 'Configurações salvas com sucesso!';
        this.messageType = 'success';
      } catch (error) {
        console.error('Erro ao atualizar configurações:', error);
        this.message = error.response?.data?.message || 'Erro ao salvar configurações.';
        this.messageType = 'error';
      } finally {
        this.loadingSettings = false;
      }
    }
  }
};
</script>

