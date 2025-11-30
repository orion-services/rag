/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.rpmhub.domain.model.ChatMessage;
import dev.rpmhub.domain.model.Conversation;
import dev.rpmhub.domain.model.ConversationMemory;
import dev.rpmhub.domain.port.ConversationRepository;
import dev.rpmhub.domain.port.ConversationService;
import dev.rpmhub.domain.port.MemoryService;
import dev.rpmhub.domain.port.UserRepository;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementation of MemoryService using MySQL + Redis hybrid approach.
 * MySQL for persistence, Redis for cache/performance.
 */
@ApplicationScoped
public class MemoryServiceImpl implements MemoryService {

    private static final String CONVERSATION_PREFIX = "conversation:";
    private static final String MEMORY_PREFIX = "memory:";

    private final ReactiveRedisDataSource reactiveRedisDataSource;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final UserRepository userRepository;
    private final int defaultMaxMessages;
    private final int ttlHours;

    @Inject
    public MemoryServiceImpl(ReactiveRedisDataSource reactiveRedisDataSource,
            ConversationRepository conversationRepository,
            ConversationService conversationService,
            UserRepository userRepository,
            @ConfigProperty(name = "memory.default.max-messages", defaultValue = "50") int defaultMaxMessages,
            @ConfigProperty(name = "memory.ttl.hours", defaultValue = "24") int ttlHours) {
        this.reactiveRedisDataSource = reactiveRedisDataSource;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.userRepository = userRepository;
        this.defaultMaxMessages = defaultMaxMessages;
        this.ttlHours = ttlHours;
    }

    @Override
    public Uni<Void> saveMessage(ChatMessage message) {
        // Generate ID if not set
        if (message.getId() == null || message.getId().isEmpty()) {
            message.setId(UUID.randomUUID().toString());
        }
        
        // Se tem userId e conversationId, usar novo fluxo híbrido
        if (message.getUserId() != null && message.getConversationId() != null) {
            return saveMessageHybrid(message);
        }
        
        // Fluxo antigo para compatibilidade (apenas Redis)
        String key = CONVERSATION_PREFIX + message.getSessionId();
        return getConversationMemory(message.getSessionId())
                .onItem().ifNull()
                .continueWith(() -> new ConversationMemory(message.getSessionId(), defaultMaxMessages))
                .onItem().transform(memory -> {
                    memory.addMessage(message);
                    return memory;
                })
                .chain(memory -> {
                    ReactiveValueCommands<String, ConversationMemory> valueCommands = reactiveRedisDataSource
                            .value(ConversationMemory.class);
                    return valueCommands.setex(key, ttlHours * 3600L, memory);
                })
                .onFailure().invoke(e -> Log.error("Error saving message to Redis: " + e.getMessage(), e))
                .replaceWithVoid();
    }
    
    private Uni<Void> saveMessageHybrid(ChatMessage message) {
        // 1. Verificar acesso do usuário à conversa
        return conversationService.userHasAccess(message.getUserId(), message.getConversationId())
            .<Void>chain(hasAccess -> {
                if (!hasAccess) {
                    return Uni.createFrom().failure(new SecurityException("Usuário não tem acesso a esta conversa"));
                }
                
                // 2. Salvar no MySQL (persistência permanente)
                return conversationRepository.findById(message.getConversationId())
                    .onItem().ifNull().failWith(() -> new IllegalArgumentException("Conversa não encontrada"))
                    .<Void>chain(conversation -> {
                        // Configurar relacionamentos
                        message.setConversationId(conversation.getId());
                        message.setSessionId(conversation.getId()); // Para compatibilidade
                        
                        // Buscar usuário se for mensagem de usuário
                        if (message.getType() == ChatMessage.MessageType.USER && message.getUserId() != null) {
                            return userRepository.findById(message.getUserId())
                                .chain(user -> {
                                    message.setUser(user);
                                    // Adicionar mensagem à conversa
                                    conversation.getMessages().add(message);
                                    return conversationRepository.persist(conversation)
                                        .chain(() -> conversationRepository.flush());
                                });
                        } else {
                            // Mensagem do assistente ou sistema
                            conversation.getMessages().add(message);
                            return conversationRepository.persist(conversation)
                                .chain(() -> conversationRepository.flush());
                        }
                    })
                    .chain(ignored -> {
                        // 3. Atualizar cache no Redis (performance)
                        String redisKey = MEMORY_PREFIX + message.getConversationId();
                        return getConversationMemoryFromRedis(redisKey)
                            .onItem().ifNull().continueWith(() -> (ConversationMemory) null)
                            .chain(mem -> {
                                // Se não estiver no cache, buscar do MySQL
                                if (mem == null) {
                                    return loadConversationMemoryFromDB(message.getConversationId());
                                }
                                return Uni.createFrom().item(mem);
                            })
                            .chain(memory -> {
                                ConversationMemory mem = memory;
                                if (mem == null) {
                                    mem = new ConversationMemory(message.getUserId(), message.getConversationId(), defaultMaxMessages);
                                }
                                mem.addMessage(message);
                                ReactiveValueCommands<String, ConversationMemory> valueCommands = 
                                    reactiveRedisDataSource.value(ConversationMemory.class);
                                return valueCommands.setex(redisKey, ttlHours * 3600L, mem);
                            });
                    })
                    .replaceWithVoid();
            })
            .onFailure().invoke(e -> Log.error("Error saving message: " + e.getMessage(), e));
    }

    /**
     * Retrieves the conversation memory for a specific session (backward compatibility).
     *
     * @param sessionId the session identifier
     * @return a Uni containing the conversation memory, or null if not found
     */
    @Override
    public Uni<ConversationMemory> getConversationMemory(String sessionId) {
        String key = CONVERSATION_PREFIX + sessionId;
        ReactiveValueCommands<String, ConversationMemory> valueCommands = reactiveRedisDataSource
                .value(ConversationMemory.class);

        return valueCommands.get(key)
                .onItem().invoke(memory -> {
                    if (memory != null) {
                        Log.debug("Retrieved conversation for session: " + sessionId +
                                " with " + memory.getMessageCount() + " messages");
                    } else {
                        Log.debug("No conversation found for session: " + sessionId);
                    }
                })
                .onFailure().invoke(e -> Log.error("Error retrieving conversation from Redis: " + e.getMessage(), e))
                .onFailure().recoverWithNull();
    }
    
    /**
     * Retrieves the conversation memory for a specific conversation.
     * Tries Redis cache first, falls back to MySQL if not found.
     *
     * @param userId the user identifier
     * @param conversationId the conversation identifier
     * @return a Uni containing the conversation memory, or null if not found
     */
    @Override
    public Uni<ConversationMemory> getConversationMemory(String userId, String conversationId) {
        String redisKey = MEMORY_PREFIX + conversationId;
        
        // Tentar buscar do cache primeiro
        return getConversationMemoryFromRedis(redisKey)
            .onItem().ifNull().continueWith(() -> (ConversationMemory) null)
            .chain(mem -> {
                // Se não estiver no cache, buscar do MySQL
                if (mem == null) {
                    return loadConversationMemoryFromDB(conversationId)
                        .chain(memory -> {
                            if (memory != null) {
                                // Salvar no cache para próximas consultas
                                ReactiveValueCommands<String, ConversationMemory> valueCommands = 
                                    reactiveRedisDataSource.value(ConversationMemory.class);
                                return valueCommands.setex(redisKey, ttlHours * 3600L, memory)
                                    .replaceWith(memory);
                            }
                            return Uni.createFrom().nullItem();
                        });
                }
                return Uni.createFrom().item(mem);
            })
            .onFailure().recoverWithNull();
    }
    
    private Uni<ConversationMemory> getConversationMemoryFromRedis(String key) {
        ReactiveValueCommands<String, ConversationMemory> valueCommands = 
            reactiveRedisDataSource.value(ConversationMemory.class);
        return valueCommands.get(key);
    }
    
    private Uni<ConversationMemory> loadConversationMemoryFromDB(String conversationId) {
        return conversationRepository.findById(conversationId)
            .onItem().ifNull().continueWith(() -> (Conversation) null)
            .map(conversation -> {
                if (conversation == null) {
                    return null;
                }
                ConversationMemory memory = new ConversationMemory();
                memory.setConversationId(conversationId);
                memory.setSession(conversationId); // Para compatibilidade
                if (conversation.getOwner() != null) {
                    memory.setUserId(conversation.getOwner().getId());
                }
                // Converter Set<ChatMessage> para List<ChatMessage>
                memory.setMessages(new ArrayList<>(conversation.getMessages()));
                memory.setLastActivity(conversation.getLastActivity());
                memory.setMaxMessages(defaultMaxMessages);
                return memory;
            });
    }

    /**
     * Gets the last N messages from a conversation (backward compatibility).
     *
     * @param sessionId the session identifier
     * @param count     the number of messages to retrieve
     * @return a Uni containing list of the last N messages
     */
    @Override
    public Uni<List<ChatMessage>> getLastMessages(String sessionId, int count) {
        return getConversationMemory(sessionId)
                .onItem().transform(memory -> {
                    if (memory == null) {
                        return List.<ChatMessage>of();
                    }
                    return memory.getLastMessages(count);
                });
    }
    
    /**
     * Gets the last N messages from a conversation.
     *
     * @param userId the user identifier
     * @param conversationId the conversation identifier
     * @param count     the number of messages to retrieve
     * @return a Uni containing list of the last N messages
     */
    @Override
    public Uni<List<ChatMessage>> getLastMessages(String userId, String conversationId, int count) {
        return getConversationMemory(userId, conversationId)
                .onItem().transform(memory -> {
                    if (memory == null) {
                        return List.<ChatMessage>of();
                    }
                    return memory.getLastMessages(count);
                });
    }

    /**
     * Gets the full conversation history as a single string (backward compatibility).
     *
     * @param session the session identifier
     * @return a Uni containing the conversation history as a string
     */
    @Override
    public Uni<String> getHistory(String session) {
        return getConversationMemory(session)
                .onItem().transform(memory -> {
                    if (memory == null) {
                        return "";
                    }
                    return memory.getHistory();
                });
    }
    
    /**
     * Gets the full conversation history as a single string.
     *
     * @param userId the user identifier
     * @param conversationId the conversation identifier
     * @return a Uni containing the conversation history as a string
     */
    @Override
    public Uni<String> getHistory(String userId, String conversationId) {
        return getConversationMemory(userId, conversationId)
                .onItem().transform(memory -> {
                    if (memory == null) {
                        return "";
                    }
                    return memory.getHistory();
                });
    }

    @Override
    public Uni<Void> clearConversation(String sessionId) {
        String key = CONVERSATION_PREFIX + sessionId;
        ReactiveKeyCommands<String> keyCommands = reactiveRedisDataSource.key();

        return keyCommands.del(key)
                .onItem().invoke(() -> Log.info("Cleared conversation for session: " + sessionId))
                .onFailure().invoke(e -> Log.error("Error clearing conversation from Redis: " + e.getMessage(), e))
                .replaceWithVoid();
    }
    
    @Override
    public Uni<Void> clearConversation(String userId, String conversationId) {
        String redisKey = MEMORY_PREFIX + conversationId;
        ReactiveKeyCommands<String> keyCommands = reactiveRedisDataSource.key();
        
        // Limpar apenas do cache Redis (mensagens permanecem no MySQL)
        return keyCommands.del(redisKey)
                .onItem().invoke(() -> Log.info("Cleared conversation cache for: " + conversationId))
                .onFailure().invoke(e -> Log.error("Error clearing conversation cache: " + e.getMessage(), e))
                .replaceWithVoid();
    }

    @Override
    public Uni<Boolean> hasConversation(String sessionId) {
        return getConversationMemory(sessionId)
                .onItem().transform(memory -> memory != null && !memory.getMessages().isEmpty());
    }
    
    @Override
    public Uni<Boolean> hasConversation(String userId, String conversationId) {
        return getConversationMemory(userId, conversationId)
                .onItem().transform(memory -> memory != null && !memory.getMessages().isEmpty());
    }

    @Override
    public Uni<Void> setMaxMessages(String sessionId, int maxMessages) {
        return getConversationMemory(sessionId)
                .onItem().ifNotNull().transformToUni(memory -> {
                    memory.setMaxMessages(maxMessages);

                    // Save updated memory back to Redis
                    String key = CONVERSATION_PREFIX + sessionId;
                    ReactiveValueCommands<String, ConversationMemory> valueCommands = reactiveRedisDataSource
                            .value(ConversationMemory.class);
                    return valueCommands.setex(key, ttlHours * 3600L, memory);
                })
                .onItem()
                .invoke(() -> Log.debug("Updated max messages for session: " + sessionId + " to " + maxMessages))
                .replaceWithVoid();
    }

    @Override
    public Uni<Integer> getMessageCount(String sessionId) {
        return getConversationMemory(sessionId)
                .onItem().transform(memory -> memory != null ? memory.getMessageCount() : 0);
    }
}
