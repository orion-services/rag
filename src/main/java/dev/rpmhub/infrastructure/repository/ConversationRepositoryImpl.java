/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.repository;

import dev.rpmhub.domain.model.Conversation;
import dev.rpmhub.domain.port.ConversationRepository;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Implementation of ConversationRepository using Hibernate Reactive Panache.
 */
@ApplicationScoped
public class ConversationRepositoryImpl implements PanacheRepositoryBase<Conversation, String>, ConversationRepository {
    
    @Override
    public Uni<Conversation> findById(String id) {
        return PanacheRepositoryBase.super.findById(id);
    }
    
    @Override
    public Uni<List<Conversation>> findByUserId(String userId) {
        return find("SELECT DISTINCT c FROM Conversation c LEFT JOIN FETCH c.participants p WHERE p.id = ?1 OR c.owner.id = ?1", userId)
            .list();
    }
    
    @Override
    public Uni<List<Conversation>> findSharedByUserId(String userId) {
        return find("SELECT DISTINCT c FROM Conversation c LEFT JOIN FETCH c.participants p WHERE p.id = ?1 AND c.shared = true", userId)
            .list();
    }
    
    @Override
    public Uni<Conversation> findByIdWithParticipants(String conversationId) {
        return find("SELECT c FROM Conversation c LEFT JOIN FETCH c.participants WHERE c.id = ?1", conversationId)
            .firstResult();
    }
    
    @Override
    public Uni<Boolean> userHasAccess(String userId, String conversationId) {
        return count("SELECT COUNT(c) FROM Conversation c WHERE c.id = ?1 AND (c.owner.id = ?2 OR EXISTS (SELECT 1 FROM c.participants p WHERE p.id = ?2))", 
                     conversationId, userId)
            .map(count -> count > 0);
    }
    
    @Override
    public Uni<Conversation> persist(Conversation conversation) {
        return PanacheRepositoryBase.super.persist(conversation);
    }
    
    @Override
    public Uni<Void> flush() {
        return PanacheRepositoryBase.super.flush();
    }
    
    @Override
    public Uni<Boolean> deleteById(String id) {
        return PanacheRepositoryBase.super.deleteById(id);
    }
}

