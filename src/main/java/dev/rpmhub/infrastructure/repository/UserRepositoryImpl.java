/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.repository;

import dev.rpmhub.domain.model.User;
import dev.rpmhub.domain.port.UserRepository;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Implementation of UserRepository using Hibernate Reactive Panache.
 */
@ApplicationScoped
public class UserRepositoryImpl implements PanacheRepositoryBase<User, String>, UserRepository {
    
    @Override
    public Uni<User> findById(String id) {
        return PanacheRepositoryBase.super.findById(id);
    }
    
    @Override
    public Uni<User> findByUsername(String username) {
        return find("username", username).firstResult();
    }
    
    @Override
    public Uni<User> findByEmail(String email) {
        return find("email", email).firstResult();
    }
    
    @Override
    public Uni<User> findByOrionUserHash(String orionUserHash) {
        return find("orionUserHash", orionUserHash).firstResult();
    }
    
    @Override
    public Uni<User> persist(User user) {
        return PanacheRepositoryBase.super.persist(user);
    }
    
    @Override
    public Uni<Void> flush() {
        return PanacheRepositoryBase.super.flush();
    }
    
    @Override
    public Uni<Boolean> deleteById(String id) {
        return PanacheRepositoryBase.super.deleteById(id);
    }
    
    @Override
    public Uni<List<User>> listAll() {
        return PanacheRepositoryBase.super.listAll();
    }
}

