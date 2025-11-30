/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.domain.port;

import dev.rpmhub.domain.model.User;
import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * Repository interface for User entity.
 */
public interface UserRepository {
    Uni<User> findById(String id);
    Uni<User> findByUsername(String username);
    Uni<User> findByEmail(String email);
    Uni<User> findByOrionUserHash(String orionUserHash);
    Uni<User> persist(User user);
    Uni<Void> flush();
    Uni<Boolean> deleteById(String id);
    Uni<List<User>> listAll();
}

