/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.service;

import dev.rpmhub.domain.model.Conversation;
import dev.rpmhub.domain.port.ConversationRepository;
import dev.rpmhub.domain.port.ConversationService;
import dev.rpmhub.domain.port.UserRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementation of ConversationService.
 */
@ApplicationScoped
public class ConversationServiceImpl implements ConversationService {
    
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    
    @Inject
    public ConversationServiceImpl(ConversationRepository conversationRepository, 
                                   UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
    }
    
    @Override
    public Uni<Conversation> createConversation(String userId, String title) {
        return userRepository.findById(userId)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Usuário não encontrado"))
            .chain(user -> {
                Conversation conversation = new Conversation();
                conversation.setTitle(title);
                conversation.setOwner(user);
                conversation.addParticipant(user);
                return conversationRepository.persist(conversation)
                    .chain(() -> conversationRepository.flush())
                    .replaceWith(conversation);
            });
    }
    
    @Override
    public Uni<Conversation> getConversation(String conversationId) {
        return conversationRepository.findByIdWithParticipants(conversationId)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Conversa não encontrada"));
    }
    
    @Override
    public Uni<List<Conversation>> getUserConversations(String userId) {
        return conversationRepository.findByUserId(userId);
    }
    
    @Override
    public Uni<Void> shareConversation(String conversationId, String ownerId, String targetUserId) {
        return conversationRepository.findById(conversationId)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Conversa não encontrada"))
            .chain(conversation -> {
                // Verificar se o usuário é o owner
                if (!conversation.getOwner().getId().equals(ownerId)) {
                    return Uni.createFrom().failure(new SecurityException("Apenas o dono pode compartilhar a conversa"));
                }
                
                return userRepository.findById(targetUserId)
                    .onItem().ifNull().failWith(() -> new IllegalArgumentException("Usuário alvo não encontrado"))
                    .chain(targetUser -> {
                        conversation.addParticipant(targetUser);
                        return conversationRepository.persist(conversation)
                            .chain(() -> conversationRepository.flush())
                            .replaceWithVoid();
                    });
            });
    }
    
    @Override
    public Uni<Void> removeParticipant(String conversationId, String ownerId, String targetUserId) {
        return conversationRepository.findById(conversationId)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Conversa não encontrada"))
            .chain(conversation -> {
                if (!conversation.getOwner().getId().equals(ownerId)) {
                    return Uni.createFrom().failure(new SecurityException("Apenas o dono pode remover participantes"));
                }
                
                if (conversation.getOwner().getId().equals(targetUserId)) {
                    return Uni.createFrom().failure(new IllegalArgumentException("Não é possível remover o dono da conversa"));
                }
                
                return userRepository.findById(targetUserId)
                    .chain(targetUser -> {
                        conversation.removeParticipant(targetUser);
                        return conversationRepository.persist(conversation)
                            .chain(() -> conversationRepository.flush())
                            .replaceWithVoid();
                    });
            });
    }
    
    @Override
    public Uni<Boolean> userHasAccess(String userId, String conversationId) {
        return conversationRepository.userHasAccess(userId, conversationId);
    }
    
    @Override
    public Uni<Void> deleteConversation(String conversationId, String userId) {
        return conversationRepository.findById(conversationId)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Conversa não encontrada"))
            .chain(conversation -> {
                if (!conversation.getOwner().getId().equals(userId)) {
                    return Uni.createFrom().failure(new SecurityException("Apenas o dono pode deletar a conversa"));
                }
                return conversationRepository.deleteById(conversationId)
                    .chain(deleted -> {
                        if (!deleted) {
                            return Uni.createFrom().failure(new IllegalArgumentException("Conversa não encontrada"));
                        }
                        return conversationRepository.flush();
                    });
            });
    }
}

