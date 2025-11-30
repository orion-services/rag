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
 * Service interface for managing conversations.
 */
public interface ConversationService {
    Uni<Conversation> createConversation(String userId, String title);
    Uni<Conversation> getConversation(String conversationId);
    Uni<List<Conversation>> getUserConversations(String userId);
    Uni<Void> shareConversation(String conversationId, String ownerId, String targetUserId);
    Uni<Void> removeParticipant(String conversationId, String ownerId, String targetUserId);
    Uni<Boolean> userHasAccess(String userId, String conversationId);
    Uni<Void> deleteConversation(String conversationId, String userId);
}

