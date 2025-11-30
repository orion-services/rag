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
 * Represents a user in the system.
 */
@Entity
@Table(name = "users")
@Getter @Setter @ToString(exclude = {"passwordHash", "conversations", "ownedConversations"})
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(name = "password_hash")
    private String passwordHash; // Para autenticação futura
    
    @Column(name = "orion_user_hash", unique = true)
    private String orionUserHash; // Hash do usuário no Orion Users para mapeamento
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    
    // Relacionamento many-to-many com conversas
    @ManyToMany(mappedBy = "participants", fetch = FetchType.LAZY)
    private Set<Conversation> conversations = new HashSet<>();
    
    // Relacionamento one-to-many: conversas criadas pelo usuário
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Conversation> ownedConversations = new HashSet<>();
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

