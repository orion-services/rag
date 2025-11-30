<template>
  <v-container fluid class="fill-height pa-0">
    <v-row no-gutters class="fill-height">
      <v-col cols="12" class="d-flex flex-column" style="height: calc(100vh - 64px);">
        <!-- Área de mensagens -->
        <v-card class="flex-grow-1 d-flex flex-column" flat>
          <v-card-text class="chat-container flex-grow-1 overflow-y-auto">
            <div v-for="(message, index) in messages" :key="index" class="message-container">
              <v-card
                :class="message.type === 'user' ? 'user-message ml-auto' : 'bot-message mr-auto'"
                :color="message.type === 'user' ? 'primary' : 'grey lighten-4'"
                :dark="message.type === 'user'"
                class="pa-3"
                style="max-width: 80%;"
              >
                <div v-if="message.type === 'user'" class="text-body-1">
                  {{ message.content }}
                </div>
                <div v-else class="text-body-1 markdown-content" v-html="renderMarkdown(message.content)"></div>
              </v-card>
            </div>
            <div v-if="isLoading" class="text-center mt-4">
              <v-progress-circular indeterminate color="primary"></v-progress-circular>
            </div>
          </v-card-text>

          <!-- Input de mensagem -->
          <v-card-actions class="pa-3">
            <v-text-field
              v-model="prompt"
              label="Digite sua mensagem..."
              outlined
              dense
              hide-details
              @keyup.enter="sendMessage"
              :disabled="isLoading"
            ></v-text-field>
            <v-btn
              color="primary"
              icon
              @click="sendMessage"
              :disabled="!prompt.trim() || isLoading"
              :loading="isLoading"
            >
              <v-icon>mdi-send</v-icon>
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import { marked } from 'marked';
import { apiService } from '../services/api';
import { authService } from '../services/auth';

export default {
  name: 'ChatInterface',
  data() {
    return {
      prompt: '',
      messages: [],
      isLoading: false,
      conversationId: null,
      userId: null
    };
  },
  async mounted() {
    const user = authService.getUser();
    if (!user || !user.id) {
      this.$router.push('/login');
      return;
    }

    this.userId = user.id;
    this.conversationId = this.$route.params.conversationId;

    // Se não há conversationId, criar nova conversa
    if (!this.conversationId) {
      try {
        const conversation = await apiService.createConversation(this.userId, 'Nova Conversa');
        this.conversationId = conversation.id;
        this.$router.replace(`/chat/${this.conversationId}`);
      } catch (error) {
        console.error('Erro ao criar conversa:', error);
        this.$router.push('/conversations');
      }
    } else {
      // Carregar histórico de mensagens
      await this.loadHistory();
    }

    // Mensagem de boas-vindas
    if (this.messages.length === 0) {
      this.messages.push({
        type: 'bot',
        content: 'Olá! Como posso ajudar você hoje?'
      });
    }
  },
  methods: {
    renderMarkdown(text) {
      try {
        return marked.parse(text);
      } catch (error) {
        console.error('Erro ao renderizar markdown:', error);
        return text;
      }
    },

    async loadHistory() {
      try {
        const memory = await apiService.getMemory(this.userId, this.conversationId);
        if (memory && memory.messages) {
          this.messages = memory.messages.map(msg => ({
            type: msg.type.toLowerCase(),
            content: msg.content
          }));
        }
      } catch (error) {
        console.error('Erro ao carregar histórico:', error);
      }
    },

    async sendMessage() {
      if (!this.prompt.trim() || this.isLoading) {
        return;
      }

      const userMessage = this.prompt.trim();
      this.prompt = '';
      
      // Adicionar mensagem do usuário
      this.messages.push({
        type: 'user',
        content: userMessage
      });

      this.isLoading = true;

      // Adicionar mensagem do bot (vazia inicialmente)
      const botMessageIndex = this.messages.length;
      this.messages.push({
        type: 'bot',
        content: ''
      });

      try {
        await apiService.createChatbotStream(
          this.userId,
          this.conversationId,
          userMessage,
          (data) => {
            // Atualizar mensagem do bot incrementalmente
            if (this.messages[botMessageIndex]) {
              this.messages[botMessageIndex].content += data;
            }
          },
          (error) => {
            console.error('Erro no stream:', error);
            if (this.messages[botMessageIndex]) {
              this.messages[botMessageIndex].content = 'Erro ao processar mensagem. Tente novamente.';
            }
            this.isLoading = false;
          },
          () => {
            this.isLoading = false;
          }
        );
      } catch (error) {
        console.error('Erro ao enviar mensagem:', error);
        if (this.messages[botMessageIndex]) {
          this.messages[botMessageIndex].content = 'Erro ao enviar mensagem. Tente novamente.';
        }
        this.isLoading = false;
      }
    }
  }
};
</script>

<style scoped>
.chat-container {
  padding: 1rem;
}

.message-container {
  margin-bottom: 1rem;
}
</style>

