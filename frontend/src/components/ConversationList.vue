<template>
  <v-container>
    <v-row>
      <v-col cols="12">
        <v-card>
          <v-card-title class="d-flex align-center">
            <span>Minhas Conversas</span>
            <v-spacer></v-spacer>
            <v-btn color="primary" @click="createNewConversation">
              <v-icon left>mdi-plus</v-icon>
              Nova Conversa
            </v-btn>
          </v-card-title>
          <v-card-text>
            <v-text-field
              v-model="search"
              label="Buscar conversas"
              prepend-inner-icon="mdi-magnify"
              clearable
              class="mb-4"
            ></v-text-field>

            <v-list v-if="conversations.length > 0">
              <ConversationItem
                v-for="conversation in filteredConversations"
                :key="conversation.id"
                :conversation="conversation"
                @delete="handleDelete"
                @select="handleSelect"
              />
            </v-list>

            <v-alert v-else-if="!loading" type="info">
              Você ainda não tem conversas. Crie uma nova conversa para começar!
            </v-alert>

            <div v-if="loading" class="text-center mt-4">
              <v-progress-circular indeterminate color="primary"></v-progress-circular>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import { apiService } from '../services/api';
import { authService } from '../services/auth';
import ConversationItem from './ConversationItem.vue';

export default {
  name: 'ConversationList',
  components: {
    ConversationItem
  },
  data() {
    return {
      conversations: [],
      loading: false,
      search: ''
    };
  },
  computed: {
    filteredConversations() {
      if (!this.search) {
        return this.conversations;
      }
      const searchLower = this.search.toLowerCase();
      return this.conversations.filter(conv =>
        conv.title.toLowerCase().includes(searchLower)
      );
    }
  },
  async mounted() {
    await this.loadConversations();
  },
  methods: {
    async loadConversations() {
      const user = authService.getUser();
      if (!user || !user.id) {
        this.$router.push('/login');
        return;
      }

      this.loading = true;
      try {
        this.conversations = await apiService.getUserConversations(user.id);
      } catch (error) {
        console.error('Erro ao carregar conversas:', error);
      } finally {
        this.loading = false;
      }
    },

    async createNewConversation() {
      this.$router.push('/chat');
    },

    handleSelect(conversationId) {
      this.$router.push(`/chat/${conversationId}`);
    },

    async handleDelete(conversationId) {
      const user = authService.getUser();
      if (!user || !user.id) {
        return;
      }

      try {
        await apiService.deleteConversation(conversationId, user.id);
        await this.loadConversations();
      } catch (error) {
        console.error('Erro ao deletar conversa:', error);
      }
    }
  }
};
</script>

