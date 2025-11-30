/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.service;

import dev.rpmhub.domain.model.User;
import dev.rpmhub.domain.port.UserRepository;
import dev.rpmhub.domain.port.UserService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementation of UserService.
 */
@ApplicationScoped
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    
    @Inject
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Override
    public Uni<User> createUser(String username, String email) {
        // Verificar se username já existe
        return userRepository.findByUsername(username)
            .onItem().ifNotNull().failWith(() -> new IllegalArgumentException("Username já existe"))
            .onItem().ifNull().continueWith(() -> (User) null)
            .chain(ignored -> {
                // Verificar se email já existe
                return userRepository.findByEmail(email);
            })
            .onItem().ifNotNull().failWith(() -> new IllegalArgumentException("Email já existe"))
            .onItem().ifNull().continueWith(() -> (User) null)
            .chain(ignored -> {
                // Criar novo usuário
                User user = new User();
                user.setUsername(username);
                user.setEmail(email);
                return userRepository.persist(user)
                    .chain(() -> userRepository.flush())
                    .replaceWith(user);
            });
    }
    
    @Override
    public Uni<User> getUserById(String userId) {
        return userRepository.findById(userId)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Usuário não encontrado"));
    }
    
    @Override
    public Uni<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Usuário não encontrado"));
    }
    
    @Override
    public Uni<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Usuário não encontrado"));
    }
    
    @Override
    public Uni<Void> updateUser(User user) {
        return userRepository.findById(user.getId())
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Usuário não encontrado"))
            .chain(() -> {
                return userRepository.persist(user)
                    .chain(() -> userRepository.flush())
                    .replaceWithVoid();
            });
    }
    
    @Override
    public Uni<Void> deleteUser(String userId) {
        return userRepository.findById(userId)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Usuário não encontrado"))
            .chain(() -> userRepository.deleteById(userId)
                .chain(deleted -> {
                    if (!deleted) {
                        return Uni.createFrom().failure(new IllegalArgumentException("Usuário não encontrado"));
                    }
                    return userRepository.flush();
                }));
    }
    
    @Override
    public Uni<List<User>> listUsers() {
        return userRepository.listAll();
    }
}

