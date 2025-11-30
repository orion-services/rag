<template>
  <div class="chat-wrapper">
    <!-- Área de mensagens -->
    <div class="messages-container" ref="chatContainer">
      <div v-if="error" class="mb-4">
        <v-alert type="error" dismissible @click:close="error = null">
          <div class="d-flex align-center">
            <span>{{ error }}</span>
            <v-spacer></v-spacer>
            <v-btn
              v-if="!conversationId"
              small
              color="error"
              text
              @click="initializeChat"
              class="ml-2"
            >
              Tentar Novamente
            </v-btn>
          </div>
        </v-alert>
      </div>
      <div v-if="initializing" class="text-center mt-4">
        <v-progress-circular indeterminate color="primary"></v-progress-circular>
        <div class="mt-2 text-body-2">Inicializando conversa...</div>
      </div>
      <div v-else>
        <div 
          v-for="(message, index) in messages" 
          :key="index" 
          :class="['message-container', message.type === 'user' ? 'user-message' : 'assistant-message', { 'message-enter': message.isNew }]"
        >
          <v-card
            v-if="message.type === 'user'"
            class="user-message-bubble pa-3"
            style="max-width: 80%;"
          >
            <div class="text-body-1">
              {{ message.content }}
            </div>
          </v-card>
          <div 
            v-else 
            class="assistant-message-content markdown-content" 
            v-html="getRenderedMarkdown(message)"
          ></div>
        </div>
        <div v-if="isLoading" class="text-center mt-4">
          <v-progress-circular indeterminate color="primary" size="32"></v-progress-circular>
          <div class="mt-2 text-body-2 text--secondary">Processando...</div>
        </div>
      </div>
    </div>

    <!-- Input de mensagem - Sempre visível na parte inferior -->
    <div class="input-container">
      <v-text-field
        v-model="prompt"
        label="Digite sua mensagem..."
        outlined
        dense
        hide-details
        @keyup.enter="sendMessage"
        :disabled="isLoading || initializing || !conversationId"
        class="input-field"
      ></v-text-field>
      <v-btn
        color="primary"
        icon
        @click="sendMessage"
        :disabled="!prompt.trim() || isLoading || initializing || !conversationId"
        :loading="isLoading"
        class="send-button"
      >
        <v-icon>mdi-send</v-icon>
      </v-btn>
    </div>
  </div>
</template>

<script>
import { marked } from 'marked';
import hljs from 'highlight.js';
import 'highlight.js/styles/github-dark.css';
import { apiService } from '../services/api';
import { authService } from '../services/auth';

// Configurar marked com opções adequadas
marked.use({
  breaks: true, // Quebras de linha como <br>
  gfm: true, // GitHub Flavored Markdown
  highlight: function(code, lang) {
    const language = hljs.getLanguage(lang) ? lang : 'plaintext';
    try {
      return hljs.highlight(code, { language }).value;
    } catch (err) {
      return hljs.highlight(code, { language: 'plaintext' }).value;
    }
  }
});

export default {
  name: 'ChatInterface',
  data() {
    return {
      prompt: '',
      messages: [],
      isLoading: false,
      initializing: true,
      error: null,
      conversationId: null,
      userId: null
    };
  },
  async mounted() {
    await this.initializeChat();
  },
  watch: {
    '$route.params.conversationId': {
      handler(newId, oldId) {
        // Sempre reinicializar quando o conversationId mudar
        // Isso inclui quando muda de um ID para undefined (nova conversa)
        if (newId !== oldId) {
          this.initializeChat();
        }
      },
      immediate: false
    },
    '$route.fullPath': {
      handler(newPath, oldPath) {
        // Se navegar para /chat sem conversationId, criar nova conversa
        // Isso garante que mesmo quando já estamos em /chat, uma nova navegação força a criação
        if (newPath === '/chat' && newPath !== oldPath) {
          this.initializeChat();
        }
      },
      immediate: false
    }
  },
  methods: {
    getRenderedMarkdown(message) {
      if (message.type !== 'assistant') return '';
      // Não chamar cleanContent aqui, será chamado em renderMarkdown
      return this.renderMarkdown(message.content);
    },
    
    async initializeChat() {
      try {
        this.initializing = true;
        this.error = null;
        
        const user = authService.getUser();
        if (!user) {
          console.warn('Usuário não autenticado, redirecionando para login');
          this.$router.push('/login');
          return;
        }

        // Usar hash como userId (o backend sincroniza automaticamente via JWT)
        // O hash do Orion Users é usado para mapear com o usuário do sistema RAG
        this.userId = user.id || user.hash || user.email;
        
        if (!this.userId) {
          console.error('Usuário sem identificador válido:', user);
          this.error = 'Erro: usuário sem identificador válido. Faça login novamente.';
          this.initializing = false;
          setTimeout(() => {
            this.$router.push('/login');
          }, 2000);
          return;
        }
        
        console.log('Inicializando chat para usuário:', this.userId);
        const routeConversationId = this.$route.params.conversationId;
        
        // Limpar estado anterior ao criar nova conversa
        if (!routeConversationId || routeConversationId === 'undefined' || routeConversationId === 'null') {
          this.conversationId = null;
          this.messages = [];
        }
        
        // Verificar se conversationId é válido (não undefined, null ou string vazia)
        if (routeConversationId && routeConversationId !== 'undefined' && routeConversationId !== 'null') {
          this.conversationId = routeConversationId;
          // Carregar histórico de mensagens
          await this.loadHistory();
        } else {
          // Se não há conversationId, criar nova conversa
          console.log('Criando nova conversa para usuário:', this.userId);
          try {
            const conversation = await apiService.createConversation(this.userId, 'Nova Conversa');
            console.log('Conversa criada:', conversation);
            
            if (conversation && conversation.id) {
              this.conversationId = conversation.id;
              // Usar replace para não adicionar ao histórico de navegação
              await this.$router.replace(`/chat/${this.conversationId}`);
            } else {
              throw new Error('Resposta inválida ao criar conversa: sem ID');
            }
          } catch (error) {
            console.error('Erro ao criar conversa:', error);
            const errorMessage = error.message || error.response?.data?.message || 'Erro ao criar conversa. Tente novamente.';
            this.error = errorMessage;
            this.initializing = false;
            // Redirecionar após mostrar erro
            setTimeout(() => {
              this.$router.push('/conversations');
            }, 3000);
            return;
          }
        }

      } catch (error) {
        console.error('Erro ao inicializar chat:', error);
        this.error = error.message || 'Erro ao inicializar chat. Tente recarregar a página.';
      } finally {
        this.initializing = false;
        this.$nextTick(() => {
          this.scrollToBottom();
        });
      }
    },

    cleanContent(text) {
      if (!text) return '';
      // Remover apenas prefixos "data:" que possam aparecer no início de linhas
      // Preservar todo o resto do conteúdo, incluindo markdown
      let cleaned = text.replace(/^data:\s*/gm, '');
      // Não remover espaços ou quebras de linha - o marked precisa deles
      return cleaned;
    },

    renderMarkdown(text) {
      try {
        if (!text) return '';
        
        // Limpar conteúdo primeiro
        const cleaned = this.cleanContent(text);
        if (!cleaned) return '';
        
        // Normalizar markdown incompleto durante streaming
        const normalized = this.normalizeIncompleteMarkdown(cleaned);
        
        // Processar quebras de linha antes de renderizar
        // Garantir que quebras de linha duplas criem parágrafos
        const processed = this.processLineBreaks(normalized);
        
        // Renderizar markdown - opções já configuradas globalmente com marked.use()
        const html = marked.parse(processed);
        
        // Aplicar highlight.js após renderização
        this.$nextTick(() => {
          const elements = this.$el?.querySelectorAll('.markdown-content');
          if (elements) {
            elements.forEach(element => {
              element.querySelectorAll('pre code').forEach((block) => {
                if (!block.classList.contains('hljs')) {
                  hljs.highlightElement(block);
                }
              });
            });
          }
        });
        
        return html;
      } catch (error) {
        console.error('Erro ao renderizar markdown:', error);
        // Em caso de erro, retornar texto escapado
        return this.escapeHtml(text);
      }
    },

    processLineBreaks(text) {
      // Normalizar diferentes tipos de quebras de linha
      let processed = text
        .replace(/\r\n/g, '\n') // Normalizar quebras de linha Windows
        .replace(/\r/g, '\n');   // Normalizar quebras de linha Mac
      
      // Se o texto não tem quebras de linha (tudo em uma linha), adicionar quebras inteligentes
      const hasLineBreaks = processed.includes('\n');
      if (!hasLineBreaks || processed.split('\n').length < 3) {
        processed = this.addIntelligentLineBreaks(processed);
      }
      
      return processed;
    },

    addIntelligentLineBreaks(text) {
      let processed = text;
      
      // Padrões para adicionar quebras de linha duplas (novos parágrafos):
      // 1. Antes de palavras-chave importantes (com ou sem dois pontos)
      const keywords = [
        'Histórico:', 'Contexto:', 'Pergunta:', 'Resposta:', 'Respostas:',
        'Problemas', 'Necessidade', 'Arquitetura', 'Características', 'Benefícios',
        'Exemplo:', 'Exemplos:', 'Recursos', 'Documentação', 'Tutorial', 'GitHub',
        'Como funciona:', 'O que é:', 'Aqui estão', 'Vamos lá!', 'Em resumo'
      ];
      keywords.forEach(keyword => {
        const escaped = this.escapeRegex(keyword);
        // Quebrar antes da palavra-chave se não estiver no início
        const regex = new RegExp(`([^\\n\\s])(${escaped})`, 'gi');
        processed = processed.replace(regex, '$1\n\n$2');
      });
      
      // 2. Antes de listas numeradas (1., 2., 3., etc.) - padrão mais específico
      processed = processed.replace(/([^\d])(\d+\.\s+[A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ])/g, '$1\n\n$2');
      
      // 3. Antes de listas com marcadores (*, -, •) quando seguido de maiúscula
      processed = processed.replace(/([^\n])([\*\-•]\s+[A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ])/g, '$1\n$2');
      
      // 4. Antes de seções em negrito/markdown (**texto**)
      processed = processed.replace(/([^\n])(\*\*[A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ])/g, '$1\n\n$2');
      
      // 5. Após pontos finais seguidos de espaço e maiúscula (novas frases importantes)
      // Mas evitar quebrar em abreviações comuns
      processed = processed.replace(/([.!?])\s+([A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ][a-záéíóúàèìòùâêîôûãõç]{3,})/g, '$1\n\n$2');
      
      // 6. Quebrar após dois pontos quando seguido de texto longo (definições)
      processed = processed.replace(/(:[A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ][^.!?]{40,}?)([.!?]|$)/g, '$1$2\n\n');
      
      // 7. Quebrar após exclamações seguidas de maiúscula
      processed = processed.replace(/(!)\s+([A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ][a-záéíóúàèìòùâêîôûãõç]{2,})/g, '$1\n\n$2');
      
      // Limpar múltiplas quebras de linha consecutivas (mais de 2)
      processed = processed.replace(/\n{3,}/g, '\n\n');
      
      // Limpar espaços no início de linhas
      processed = processed.replace(/\n\s+/g, '\n');
      
      // Limpar quebras de linha no início e fim
      processed = processed.trim();
      
      return processed;
    },

    escapeRegex(str) {
      return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    },

    normalizeIncompleteMarkdown(text) {
      // Apenas tentar fechar blocos de código incompletos durante streaming
      // Não modificar outros aspectos do markdown para preservar formatação
      let normalized = text;
      
      // Contar backticks para verificar se há blocos de código incompletos
      const codeBlockMatches = normalized.match(/```/g);
      if (codeBlockMatches && codeBlockMatches.length % 2 !== 0) {
        // Bloco de código incompleto - adicionar fechamento temporário
        normalized += '\n```';
      }
      
      // Retornar texto sem outras modificações para preservar formatação markdown
      return normalized;
    },

    escapeHtml(text) {
      const div = document.createElement('div');
      div.textContent = text;
      return div.innerHTML;
    },

    async loadHistory() {
      try {
        if (!this.conversationId) {
          return;
        }
        const memory = await apiService.getMemory(this.userId, this.conversationId);
        if (memory && memory.messages && Array.isArray(memory.messages)) {
          this.messages = memory.messages.map(msg => {
            // Mapear tipos do backend (ASSISTANT, USER) para lowercase
            let type = 'assistant';
            if (msg.type) {
              const msgType = msg.type.toUpperCase();
              if (msgType === 'USER') {
                type = 'user';
              } else if (msgType === 'ASSISTANT') {
                type = 'assistant';
              }
            }
            return {
              type: type,
              content: msg.content || '',
              isNew: false
            };
          });
        }
      } catch (error) {
        console.error('Erro ao carregar histórico:', error);
        // Não mostrar erro fatal, apenas logar
        // O usuário pode continuar a conversar mesmo sem histórico
      }
    },

    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.chatContainer;
        if (container) {
          container.scrollTop = container.scrollHeight;
        }
      });
    },

    async sendMessage() {
      if (!this.prompt.trim() || this.isLoading || !this.conversationId) {
        if (!this.conversationId) {
          this.error = 'Conversa não inicializada. Por favor, recarregue a página.';
        }
        return;
      }

      const userMessage = this.prompt.trim();
      this.prompt = '';
      this.error = null;
      
      // Adicionar mensagem do usuário
      const userMsgIndex = this.messages.length;
      this.messages.push({
        type: 'user',
        content: userMessage,
        isNew: true
      });

      // Remover flag isNew após animação
      this.$nextTick(() => {
        setTimeout(() => {
          if (this.messages[userMsgIndex] && this.messages[userMsgIndex].type === 'user') {
            this.$set(this.messages[userMsgIndex], 'isNew', false);
          }
        }, 300);
      });

      this.scrollToBottom();
      this.isLoading = true;

      // Adicionar mensagem do assistente (vazia inicialmente)
      const botMessageIndex = this.messages.length;
      this.messages.push({
        type: 'assistant',
        content: '',
        isNew: true
      });

      try {
        await apiService.createChatbotStream(
          this.conversationId,
          userMessage,
          (data) => {
            // Atualizar mensagem do bot incrementalmente
            if (this.messages[botMessageIndex]) {
              // Limpar qualquer "data:" que possa aparecer
              const cleanedData = data.replace(/^data:\s*/gm, '').trim();
              if (cleanedData) {
                this.messages[botMessageIndex].content += cleanedData;
                // Scroll automático enquanto recebe dados
                this.scrollToBottom();
              }
            }
          },
          (error) => {
            console.error('Erro no stream:', error);
            if (this.messages[botMessageIndex]) {
              this.messages[botMessageIndex].content = 'Erro ao processar mensagem. Por favor, tente novamente.';
            }
            this.error = error.message || 'Erro ao processar mensagem. Verifique sua conexão e tente novamente.';
            this.isLoading = false;
          },
          () => {
            this.isLoading = false;
            // Remover flag isNew da mensagem do assistente após animação
            if (this.messages[botMessageIndex]) {
              this.$nextTick(() => {
                setTimeout(() => {
                  this.$set(this.messages[botMessageIndex], 'isNew', false);
                }, 300);
              });
            }
            this.scrollToBottom();
          }
        );
      } catch (error) {
        console.error('Erro ao enviar mensagem:', error);
        if (this.messages[botMessageIndex]) {
          this.messages[botMessageIndex].content = 'Erro ao enviar mensagem. Por favor, tente novamente.';
        }
        this.error = error.response?.data?.message || error.message || 'Erro ao enviar mensagem. Verifique sua conexão e tente novamente.';
        this.isLoading = false;
      }
    }
  }
};
</script>

<style scoped>
.chat-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  overflow: hidden;
}

.messages-container {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 1rem;
  min-height: 0;
}

.input-container {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  border-top: 1px solid rgba(0, 0, 0, 0.12);
  background-color: white;
}

.input-field {
  flex: 1;
}

.send-button {
  flex-shrink: 0;
}

.message-container {
  margin-bottom: 1.5rem;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  margin-left: auto;
}

.assistant-message {
  display: flex;
  justify-content: center;
  width: 100%;
}

.user-message-bubble {
  background-color: #e0e0e0 !important;
  border-radius: 18px !important;
  color: #333 !important;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.assistant-message-content {
  text-align: justify;
  max-width: 80%;
  padding: 0.75rem 1rem;
  word-wrap: break-word;
  font-size: 0.9rem;
  line-height: 1.5;
  margin: 0 auto;
}

.markdown-content {
  word-wrap: break-word;
  color: #333;
}

/* Parágrafos */
.markdown-content :deep(p) {
  margin-bottom: 1rem;
  line-height: 1.6;
  min-height: 1.6em; /* Garantir altura mínima para parágrafos */
}

.markdown-content :deep(p:last-child) {
  margin-bottom: 0;
}

/* Espaçamento entre parágrafos consecutivos */
.markdown-content :deep(p + p) {
  margin-top: 0.5rem;
}

/* Quebras de linha - garantir que sejam visíveis */
.markdown-content :deep(br) {
  line-height: 1.6;
}

/* Garantir espaçamento entre parágrafos */
.markdown-content :deep(p + p) {
  margin-top: 0.75rem;
}

/* Headers */
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin-top: 1.5rem;
  margin-bottom: 0.75rem;
  font-weight: 600;
  line-height: 1.25;
  color: #1a1a1a;
}

.markdown-content :deep(h1) {
  font-size: 1.75rem;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 0.3rem;
}

.markdown-content :deep(h2) {
  font-size: 1.5rem;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 0.3rem;
}

.markdown-content :deep(h3) {
  font-size: 1.25rem;
}

.markdown-content :deep(h4) {
  font-size: 1.1rem;
}

.markdown-content :deep(h5) {
  font-size: 1rem;
}

.markdown-content :deep(h6) {
  font-size: 0.9rem;
  color: #666;
}

.markdown-content :deep(h1:first-child),
.markdown-content :deep(h2:first-child),
.markdown-content :deep(h3:first-child) {
  margin-top: 0;
}

/* Código inline */
.markdown-content :deep(code) {
  background-color: rgba(0, 0, 0, 0.05);
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.9em;
  color: #e83e8c;
}

/* Blocos de código */
.markdown-content :deep(pre) {
  background-color: #1e1e1e;
  padding: 1rem;
  border-radius: 6px;
  overflow-x: auto;
  margin: 1rem 0;
  line-height: 1.45;
  border: 1px solid rgba(0, 0, 0, 0.1);
}

.markdown-content :deep(pre code) {
  background-color: transparent;
  padding: 0;
  color: #d4d4d4;
  font-size: 0.9em;
  display: block;
  overflow-x: auto;
}

/* Listas */
.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0.75rem 0;
  padding-left: 2rem;
  line-height: 1.6;
}

.markdown-content :deep(li) {
  margin: 0.25rem 0;
}

.markdown-content :deep(ul ul),
.markdown-content :deep(ol ol),
.markdown-content :deep(ul ol),
.markdown-content :deep(ol ul) {
  margin-top: 0.25rem;
  margin-bottom: 0.25rem;
}

/* Listas de tarefas */
.markdown-content :deep(input[type="checkbox"]) {
  margin-right: 0.5rem;
}

/* Blockquotes */
.markdown-content :deep(blockquote) {
  margin: 1rem 0;
  padding: 0.5rem 1rem;
  border-left: 4px solid #dfe2e5;
  background-color: rgba(0, 0, 0, 0.02);
  color: #6a737d;
  font-style: italic;
}

.markdown-content :deep(blockquote p:last-child) {
  margin-bottom: 0;
}

/* Tabelas */
.markdown-content :deep(table) {
  border-collapse: collapse;
  margin: 1rem 0;
  width: 100%;
  display: block;
  overflow-x: auto;
}

.markdown-content :deep(thead) {
  background-color: rgba(0, 0, 0, 0.05);
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid #dfe2e5;
  padding: 0.5rem 0.75rem;
  text-align: left;
}

.markdown-content :deep(th) {
  font-weight: 600;
  background-color: rgba(0, 0, 0, 0.05);
}

.markdown-content :deep(tr:nth-child(even)) {
  background-color: rgba(0, 0, 0, 0.02);
}

/* Links */
.markdown-content :deep(a) {
  color: #0366d6;
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(a:visited) {
  color: #6f42c1;
}

/* Imagens */
.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 1rem 0;
  display: block;
}

/* Regra horizontal */
.markdown-content :deep(hr) {
  border: none;
  border-top: 1px solid #eaecef;
  margin: 1.5rem 0;
}

/* Texto forte e itálico - usar maior especificidade */
.assistant-message-content.markdown-content :deep(strong),
.assistant-message-content.markdown-content :deep(b),
.markdown-content :deep(strong),
.markdown-content :deep(b) {
  font-weight: 700 !important;
  font-weight: bold !important;
  color: #1a1a1a !important;
  display: inline;
}

.markdown-content :deep(em),
.markdown-content :deep(i) {
  font-style: italic;
}

/* Texto riscado */
.markdown-content :deep(del),
.markdown-content :deep(s) {
  text-decoration: line-through;
  opacity: 0.7;
}

/* Garantir que o conteúdo preserve formatação */
.markdown-content {
  word-wrap: break-word;
  overflow-wrap: break-word;
}

@keyframes messageEnter {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-enter {
  animation: messageEnter 0.3s ease-out;
}
</style>

