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
 * Service interface for managing users.
 */
public interface UserService {
    Uni<User> createUser(String username, String email);
    Uni<User> getUserById(String userId);
    Uni<User> getUserByUsername(String username);
    Uni<User> getUserByEmail(String email);
    Uni<Void> updateUser(User user);
    Uni<Void> deleteUser(String userId);
    Uni<List<User>> listUsers();
}

