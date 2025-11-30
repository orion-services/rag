// Authentication service
const TOKEN_KEY = 'jwt_token';
const USER_KEY = 'user_data';

export const authService = {
  // Salvar token JWT
  setToken(token) {
    localStorage.setItem(TOKEN_KEY, token);
  },

  // Obter token JWT
  getToken() {
    return localStorage.getItem(TOKEN_KEY);
  },

  // Remover token
  removeToken() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },

  // Verificar se está autenticado
  isAuthenticated() {
    return !!this.getToken();
  },

  // Salvar dados do usuário
  setUser(user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },

  // Obter dados do usuário
  getUser() {
    const userStr = localStorage.getItem(USER_KEY);
    return userStr ? JSON.parse(userStr) : null;
  },

  // Logout
  logout() {
    this.removeToken();
  }
};

