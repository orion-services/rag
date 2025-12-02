/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.domain.usecase;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.rpmhub.domain.model.AIRequest;
import dev.rpmhub.domain.model.ChatMessage;
import dev.rpmhub.domain.model.RagQuery;
import dev.rpmhub.domain.port.AIService;
import dev.rpmhub.domain.port.EmbeddingRepository;
import dev.rpmhub.domain.port.MemoryService;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Use case for interacting with a chatbot using RAG
 * (Retrieval-Augmented Generation).
 */
@ApplicationScoped
public class ChatbotUseCase {

    /**
     * Repository for managing embeddings.
     */
    private final EmbeddingRepository embeddingRepository;

    /**
     * Service for AI interactions.
     */
    private final AIService aiService;

    /**
     * Service for managing conversation memory.
     */
    private final MemoryService memoryService;

    /**
     * Default context to use when no context is found.
     */
    @ConfigProperty(name = "rag.context", defaultValue = "")
    private static final String DEFAULT_CONTEXT = "";

    @Inject
    public ChatbotUseCase(EmbeddingRepository embeddingRepository, AIService aiService, MemoryService memoryService) {
        this.embeddingRepository = embeddingRepository;
        this.aiService = aiService;
        this.memoryService = memoryService;
    }

    /**
     * Executes the use case to interact with the chatbot (backward compatibility).
     *
     * @param session the session ID
     * @param prompt  the user prompt
     * @return a Multi emitting the chatbot response
     */
    public Multi<String> execute(String session, String prompt) {
        Log.info("Executing ChatbotUseCase for session: " + session + " with prompt: " + prompt);
        // Save user message to memory
        ChatMessage userMessage = new ChatMessage(session, prompt, ChatMessage.MessageType.USER);

        return memoryService.saveMessage(userMessage)
                .onItem().invoke(() -> Log.info("Saved user message for session: " + session))
                .onItem().transformToMulti(ignored -> {
                    RagQuery query = new RagQuery(prompt, 1, 0.7);

                    return embeddingRepository.searchChunks(query)
                            .flatMap(ragResponse -> {
                                String context = ragResponse.getContexts().isEmpty()
                                        ? DEFAULT_CONTEXT
                                        : ragResponse.getFirstContext();

                                Log.info("Context: " + context);

                                // Get conversation history for context
                                return memoryService.getHistory(session)
                                        .onItem().transformToMulti(history -> {
                                            AIRequest aiRequest = new AIRequest(session, prompt, context, history);
                                            return aiService.generateContextualResponse(aiRequest)
                                                    .group().intoLists().of(20)
                                                    .onItem().transform(list -> String.join("", list))
                                                    .onItem().call(response -> {
                                                        // Save assistant response to memory
                                                        ChatMessage assistantMessage = new ChatMessage(session,
                                                                response,
                                                                ChatMessage.MessageType.ASSISTANT);
                                                        return memoryService.saveMessage(assistantMessage);
                                                    });
                                        });
                            });
                });
    }
    
    /**
     * Executes the use case to interact with the chatbot with user and conversation.
     *
     * @param userId the user ID
     * @param conversationId the conversation ID
     * @param prompt  the user prompt
     * @return a Multi emitting the chatbot response
     */
    public Multi<String> execute(String userId, String conversationId, String prompt) {
        Log.info("Executing ChatbotUseCase for user: " + userId + ", conversation: " + conversationId + " with prompt: " + prompt);
        // Save user message to memory
        ChatMessage userMessage = new ChatMessage(userId, conversationId, prompt, ChatMessage.MessageType.USER);

        return memoryService.saveMessage(userMessage)
                .onItem().invoke(() -> Log.info("Saved user message for conversation: " + conversationId))
                .onItem().transformToMulti(ignored -> {
                    RagQuery query = new RagQuery(prompt, 1, 0.7);

                    return embeddingRepository.searchChunks(query)
                            .flatMap(ragResponse -> {
                                String context = ragResponse.getContexts().isEmpty()
                                        ? DEFAULT_CONTEXT
                                        : ragResponse.getFirstContext();

                                Log.info("Context: " + context);

                                // Get conversation history for context
                                return memoryService.getHistory(userId, conversationId)
                                        .onItem().transformToMulti(history -> {
                                            AIRequest aiRequest = new AIRequest(conversationId, prompt, context, history);
                                            return aiService.generateContextualResponse(aiRequest)
                                                    .group().intoLists().of(20)
                                                    .onItem().transform(list -> String.join("", list))
                                                    .onItem().call(response -> {
                                                        // Save assistant response to memory
                                                        // Mensagens ASSISTANT não devem ter userId
                                                        ChatMessage assistantMessage = new ChatMessage();
                                                        assistantMessage.setConversationId(conversationId);
                                                        assistantMessage.setSessionId(conversationId); // Para compatibilidade
                                                        assistantMessage.setContent(response);
                                                        assistantMessage.setType(ChatMessage.MessageType.ASSISTANT);
                                                        assistantMessage.setUserId(null); // Mensagens do assistente não têm userId
                                                        return memoryService.saveMessage(assistantMessage);
                                                    });
                                        });
                            });
                });
    }
}
