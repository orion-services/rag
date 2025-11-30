/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a conversation that can be shared among multiple users.
 */
@Entity
@Table(name = "conversations")
@Getter @Setter @ToString(exclude = {"participants", "messages"})
public class Conversation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String title;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_activity", nullable = false)
    private LocalDateTime lastActivity;
    
    @Column(name = "is_shared", nullable = false)
    private boolean shared = false;
    
    // Relacionamento many-to-many: usuários participantes
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "conversation_participants",
        joinColumns = @JoinColumn(name = "conversation_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> participants = new HashSet<>();
    
    // Relacionamento one-to-many: mensagens da conversa
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC")
    private Set<ChatMessage> messages = new HashSet<>();
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastActivity == null) {
            lastActivity = LocalDateTime.now();
        }
        // Adiciona o owner como participante
        if (participants == null) {
            participants = new HashSet<>();
        }
        if (owner != null && !participants.contains(owner)) {
            participants.add(owner);
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastActivity = LocalDateTime.now();
    }
    
    public void addParticipant(User user) {
        if (participants == null) {
            participants = new HashSet<>();
        }
        participants.add(user);
        shared = participants.size() > 1;
    }
    
    public void removeParticipant(User user) {
        if (participants != null) {
            participants.remove(user);
            shared = participants.size() > 1;
        }
    }
    
    public boolean hasParticipant(String userId) {
        return participants != null && 
               participants.stream().anyMatch(u -> u.getId().equals(userId));
    }
}

