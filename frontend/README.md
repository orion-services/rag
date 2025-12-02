# Frontend RAG Chatbot

Este é o frontend da aplicação RAG Chatbot, gerenciado pelo Vite.

## Desenvolvimento

Para executar o frontend em modo de desenvolvimento:

```bash
cd frontend
npm install
npm run dev
```

O servidor de desenvolvimento estará disponível em `http://localhost:5173`.

## Build

Para fazer o build do frontend:

```bash
cd frontend
npm install
npm run build
```

O build será gerado em `src/main/resources/META-INF/resources/` para ser servido pelo Quarkus.

## Variáveis de Ambiente

Você pode configurar as seguintes variáveis de ambiente (criando um arquivo `.env`):

- `VITE_API_BASE_URL`: URL base da API backend (padrão: `http://localhost:8081`)
- `VITE_ORION_USERS_URL`: URL do serviço Orion Users (padrão: `http://localhost:8080`)

## Estrutura

- `src/main.js`: Ponto de entrada da aplicação
- `src/App.vue`: Componente raiz
- `src/components/`: Componentes Vue
- `src/services/`: Serviços de API
- `src/stores/`: Stores Pinia
- `src/router/`: Configuração de rotas

