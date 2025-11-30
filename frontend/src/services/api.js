import axios from 'axios';

// Backend API Service
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Interceptor para adicionar token JWT
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('jwt_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Interceptor para tratar erros de autenticação
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      // Token inválido ou expirado
      localStorage.removeItem('jwt_token');
      localStorage.removeItem('user_data');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const apiService = {
  // Conversas
  async createConversation(userId, title) {
    const response = await api.post(`/ai/users/${userId}/conversations`, { title });
    return response.data;
  },

  async getUserConversations(userId) {
    const response = await api.get(`/ai/users/${userId}/conversations`);
    return response.data;
  },

  async getConversation(conversationId) {
    const response = await api.get(`/ai/conversations/${conversationId}`);
    return response.data;
  },

  async deleteConversation(conversationId, userId) {
    const response = await api.delete(`/ai/conversations/${conversationId}?userId=${userId}`);
    return response.data;
  },

  async shareConversation(conversationId, ownerId, targetUserId) {
    const response = await api.post(
      `/ai/conversations/${conversationId}/share?ownerId=${ownerId}&targetUserId=${targetUserId}`
    );
    return response.data;
  },

  // Memória
  async getMemory(userId, conversationId) {
    const response = await api.get(`/ai/memory?userId=${userId}&conversationId=${conversationId}`);
    return response.data;
  },

  // Chatbot SSE (usando fetch com stream)
  async createChatbotStream(userId, conversationId, prompt, onMessage, onError, onComplete) {
    const token = localStorage.getItem('jwt_token');
    const url = `${API_BASE_URL}/ai/chatbot?userId=${userId}&conversationId=${conversationId}&prompt=${encodeURIComponent(prompt)}`;
    
    const response = await fetch(url, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    if (!response.ok) {
      onError(new Error(`HTTP error! status: ${response.status}`));
      return;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();
        
        if (done) {
          onComplete();
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.substring(6);
            if (data === '[DONE]') {
              onComplete();
              return;
            }
            onMessage(data);
          }
        }
      }
    } catch (error) {
      onError(error);
    }
  }
};

