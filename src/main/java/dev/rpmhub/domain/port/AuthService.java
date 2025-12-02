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

/**
 * Service interface for authentication and JWT handling.
 */
public interface AuthService {
    /**
     * Extracts user hash from JWT token and synchronizes/creates local user.
     * @param jwtToken The JWT token from Orion Users
     * @return The synchronized local user
     */
    Uni<User> syncUserFromJwt(String jwtToken);
    
    /**
     * Extracts user hash from JWT token.
     * @param jwtToken The JWT token from Orion Users
     * @return The user hash (orionUserHash)
     */
    String extractUserHashFromJwt(String jwtToken);
    
    /**
     * Extracts email from JWT token.
     * @param jwtToken The JWT token from Orion Users
     * @return The user email
     */
    String extractEmailFromJwt(String jwtToken);
}

