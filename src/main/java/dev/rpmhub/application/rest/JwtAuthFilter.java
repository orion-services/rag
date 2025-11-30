/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.application.rest;

import dev.rpmhub.domain.port.AuthService;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

/**
 * Filter to extract JWT token and synchronize user from Orion Users.
 * Only processes requests that have Authorization header.
 */
@Provider
public class JwtAuthFilter implements ContainerRequestFilter {
    
    @Inject
    AuthService authService;
    
    @Override
    public void filter(ContainerRequestContext requestContext) {
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        
        // Only process if Authorization header is present
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwtToken = authHeader.substring(7);
            
            // Store JWT token in request context for later use by endpoints
            // The synchronization will be done lazily in the endpoints that need it
            // This avoids reactive context conflicts
            requestContext.setProperty("jwt.token", jwtToken);
            
            Log.debug("JWT token stored in request context");
        }
    }
}

