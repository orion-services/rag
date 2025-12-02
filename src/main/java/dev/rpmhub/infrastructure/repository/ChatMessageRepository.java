/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.repository;

import dev.rpmhub.domain.model.ChatMessage;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for ChatMessage entity using Hibernate Reactive Panache.
 */
@ApplicationScoped
public class ChatMessageRepository implements PanacheRepositoryBase<ChatMessage, String> {
    
    public Uni<ChatMessage> persist(ChatMessage message) {
        return PanacheRepositoryBase.super.persist(message);
    }
}

