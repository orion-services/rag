/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents a conversation memory for a specific session.
 */
@Getter @Setter @ToString
public class ConversationMemory {

    private String userId;        // NOVO
    private String conversationId; // NOVO
    private String session;        // Mantido para compatibilidade
    private List<ChatMessage> messages;
    private LocalDateTime lastActivity;
    private int maxMessages;

    public ConversationMemory() {
        this.messages = new ArrayList<>();
        this.lastActivity = LocalDateTime.now();
        this.maxMessages = 50; // Default limit
    }

    public ConversationMemory(String session) {
        this();
        this.session = session;
        this.conversationId = session; // Para compatibilidade
    }
    
    public ConversationMemory(String userId, String conversationId) {
        this();
        this.userId = userId;
        this.conversationId = conversationId;
        this.session = conversationId; // Para compatibilidade
    }

    public ConversationMemory(String session, int maxMessages) {
        this(session);
        this.maxMessages = maxMessages;
    }
    
    public ConversationMemory(String userId, String conversationId, int maxMessages) {
        this(userId, conversationId);
        this.maxMessages = maxMessages;
    }

    /**
     * Adds a message to the conversation memory. If the number of messages
     * exceeds the limit, removes the oldest ones.
     *
     * @param message the message to add
     */
    public void addMessage(ChatMessage message) {
        this.messages.add(message);
        this.lastActivity = LocalDateTime.now();

        // Keep only the last maxMessages messages
        if (messages.size() > maxMessages) {
            messages.remove(0);
        }
    }

    /**
     * Gets the conversation history as a formatted string.
     */
    public String getHistory() {
        StringBuilder history = new StringBuilder();
        for (ChatMessage message : messages) {
            history.append(message.getType().name())
                    .append(": ")
                    .append(message.getContent())
                    .append("\n");
        }
        return history.toString();
    }

    /**
     * Gets the last N messages from the conversation.
     *
     * @param count the number of messages to retrieve
     * @return list of the last N messages
     */
    public List<ChatMessage> getLastMessages(int count) {
        int startIndex = Math.max(0, messages.size() - count);
        return new ArrayList<>(messages.subList(startIndex, messages.size()));
    }

    /**
     * Clears the conversation memory.
     */
    public void clear() {
        this.messages.clear();
        this.lastActivity = LocalDateTime.now();
    }

    /**
     * Gets the current number of messages in the conversation.
     *
     * @return the number of messages
     */
    public int getMessageCount() {
        return this.messages.size();
    }

}
