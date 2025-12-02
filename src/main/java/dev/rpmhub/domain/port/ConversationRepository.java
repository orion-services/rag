/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.domain.port;

import dev.rpmhub.domain.model.Conversation;
import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * Repository interface for Conversation entity.
 */
public interface ConversationRepository {
    Uni<Conversation> findById(String id);
    Uni<Conversation> findByIdWithMessages(String id);
    Uni<List<Conversation>> findByIds(List<String> ids);
    Uni<List<Conversation>> findByUserId(String userId);
    Uni<List<Conversation>> findOwnedByUserId(String userId);
    Uni<Boolean> userHasAccess(String userId, String conversationId);
    Uni<Conversation> persist(Conversation conversation);
    Uni<Void> flush();
    Uni<Boolean> deleteById(String id);
}

