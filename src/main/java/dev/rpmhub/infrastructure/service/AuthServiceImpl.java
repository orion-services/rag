/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.service;

import dev.rpmhub.domain.model.User;
import dev.rpmhub.domain.port.AuthService;
import dev.rpmhub.domain.port.UserRepository;
import dev.rpmhub.domain.port.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

/**
 * Implementation of AuthService for JWT handling and user synchronization.
 */
@ApplicationScoped
public class AuthServiceImpl implements AuthService {
    
    private final UserRepository userRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    
    @Inject
    public AuthServiceImpl(UserRepository userRepository, UserService userService, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String extractUserHashFromJwt(String jwtToken) {
        try {
            // JWT format: header.payload.signature
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT token format");
            }
            
            // Decode payload (base64url)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            
            // Parse JSON payload to extract hash
            JsonNode jsonNode = objectMapper.readTree(payload);
            
            if (!jsonNode.has("c_hash")) {
                throw new IllegalArgumentException("Hash not found in JWT token. Available claims: " + jsonNode.fieldNames());
            }
            
            return jsonNode.get("c_hash").asText();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to extract hash from JWT token", e);
        }
    }
    
    @Override
    public String extractEmailFromJwt(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT token format");
            }
            
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            
            // Parse JSON payload
            JsonNode jsonNode = objectMapper.readTree(payload);
            
            if (jsonNode.has("email")) {
                return jsonNode.get("email").asText();
            }
            
            throw new IllegalArgumentException("Email not found in JWT token. Available claims: " + jsonNode.fieldNames());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to extract email from JWT token", e);
        }
    }
    
    @Override
    @WithTransaction
    public Uni<User> syncUserFromJwt(String jwtToken) {
        String orionUserHash = extractUserHashFromJwt(jwtToken);
        String email = extractEmailFromJwt(jwtToken);
        
        // Try to find existing user by hash
        return userRepository.findByOrionUserHash(orionUserHash)
            .onItem().transformToUni(user -> {
                if (user != null) {
                    // User found by hash, return it
                    return Uni.createFrom().item(user);
                }
                
                // User doesn't exist by hash, try to find by email
                return userRepository.findByEmail(email)
                    .onItem().transformToUni(userByEmail -> {
                        if (userByEmail != null) {
                            // User exists but doesn't have hash, update it
                            userByEmail.setOrionUserHash(orionUserHash);
                            return userRepository.persist(userByEmail)
                                .onItem().transformToUni(u -> userRepository.flush().replaceWith(u));
                        }
                        
                        // User doesn't exist at all, create new user
                        // Use email prefix as username
                        String username = email.split("@")[0];
                        
                        return userService.createUser(username, email)
                            .onItem().transformToUni(newUser -> {
                                // Set hash for new user
                                newUser.setOrionUserHash(orionUserHash);
                                return userRepository.persist(newUser)
                                    .onItem().transformToUni(u -> userRepository.flush().replaceWith(u));
                            });
                    });
            });
    }
}

