/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.application.rest;

import dev.rpmhub.domain.model.Conversation;
import dev.rpmhub.domain.model.ConversationMemory;
import dev.rpmhub.domain.model.User;
import dev.rpmhub.domain.port.ConversationService;
import dev.rpmhub.domain.port.MemoryService;
import dev.rpmhub.domain.port.UserService;
import dev.rpmhub.domain.usecase.AskQuestionUseCase;
import dev.rpmhub.domain.usecase.ChatbotUseCase;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/ai")
public class RagController {

    private final ChatbotUseCase chatbotUseCase;
    private final AskQuestionUseCase askQuestionUseCase;
    private final MemoryService memoryService;
    private final UserService userService;
    private final ConversationService conversationService;

    @Inject
    public RagController(ChatbotUseCase chatbotUseCase,
            AskQuestionUseCase askQuestionUseCase,
            MemoryService memoryService,
            UserService userService,
            ConversationService conversationService) {

        this.chatbotUseCase = chatbotUseCase;
        this.askQuestionUseCase = askQuestionUseCase;
        this.memoryService = memoryService;
        this.userService = userService;
        this.conversationService = conversationService;
    }

    @GET
    @Path("/chatbot")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> chatbot(
            @QueryParam("session") String session,
            @QueryParam("userId") String userId,
            @QueryParam("conversationId") String conversationId,
            @QueryParam("prompt") @NotBlank String prompt) {
        // Suporte para novo formato (userId + conversationId) e formato antigo (session)
        if (userId != null && conversationId != null) {
            // Requer autenticação JWT para novo formato
            // A validação será feita pelo filtro de segurança quando Authorization header estiver presente
            Log.info("Chatbot User: " + userId + ", Conversation: " + conversationId);
            // Verificar acesso antes de processar
            return conversationService.userHasAccess(userId, conversationId)
                .chain(hasAccess -> {
                    if (!hasAccess) {
                        return Uni.createFrom().failure(new SecurityException("Acesso negado"));
                    }
                    return chatbotUseCase.execute(userId, conversationId, prompt).toUni();
                })
                .toMulti();
        } else if (session != null) {
            // Formato antigo para compatibilidade (sem autenticação JWT)
            Log.info("Chatbot Session: " + session);
            return chatbotUseCase.execute(session, prompt);
        } else {
            return Multi.createFrom().failure(new IllegalArgumentException("Deve fornecer session ou userId+conversationId"));
        }
    }

    @GET
    @Path("/ask")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> askModel(
            @QueryParam("session") @NotBlank String session,
            @QueryParam("prompt") @NotBlank String prompt) {
        Log.info("Ask Model Session: " + session);
        return askQuestionUseCase.execute(session, prompt);
    }

    @GET
    @Path("/memory")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<ConversationMemory> getMemory(
        @QueryParam("session") String session,
        @QueryParam("userId") String userId,
        @QueryParam("conversationId") String conversationId) {
        if (userId != null && conversationId != null) {
            Log.info("Memory User: " + userId + ", Conversation: " + conversationId);
            return memoryService.getConversationMemory(userId, conversationId);
        } else if (session != null) {
            Log.info("Memory Session: " + session);
            return memoryService.getConversationMemory(session);
        } else {
            return Uni.createFrom().failure(new IllegalArgumentException("Deve fornecer session ou userId+conversationId"));
        }
    }
    
    // ========== Endpoints de Usuário ==========
    
    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<User> createUser(@Valid UserRequest request) {
        Log.info("Creating user: " + request.username);
        return userService.createUser(request.username, request.email);
    }
    
    @GET
    @Path("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<User> getUser(@PathParam("userId") String userId) {
        Log.info("Getting user: " + userId);
        return userService.getUserById(userId);
    }
    
    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<List<User>> listUsers() {
        Log.info("Listing users");
        return userService.listUsers();
    }
    
    // ========== Endpoints de Conversa ==========
    
    @POST
    @Path("/users/{userId}/conversations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<Conversation> createConversation(
            @PathParam("userId") String userId,
            @Valid ConversationRequest request) {
        Log.info("Creating conversation for user: " + userId);
        return conversationService.createConversation(userId, request.title);
    }
    
    @GET
    @Path("/users/{userId}/conversations")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<List<Conversation>> getUserConversations(@PathParam("userId") String userId) {
        Log.info("Getting conversations for user: " + userId);
        return conversationService.getUserConversations(userId);
    }
    
    @GET
    @Path("/conversations/{conversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<Conversation> getConversation(@PathParam("conversationId") String conversationId) {
        Log.info("Getting conversation: " + conversationId);
        return conversationService.getConversation(conversationId);
    }
    
    @POST
    @Path("/conversations/{conversationId}/share")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<Response> shareConversation(
            @PathParam("conversationId") String conversationId,
            @QueryParam("ownerId") @NotBlank String ownerId,
            @QueryParam("targetUserId") @NotBlank String targetUserId) {
        Log.info("Sharing conversation " + conversationId + " with user " + targetUserId);
        return conversationService.shareConversation(conversationId, ownerId, targetUserId)
            .replaceWith(Response.ok().build());
    }
    
    @DELETE
    @Path("/conversations/{conversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Uni<Response> deleteConversation(
            @PathParam("conversationId") String conversationId,
            @QueryParam("userId") @NotBlank String userId) {
        Log.info("Deleting conversation " + conversationId + " by user " + userId);
        return conversationService.deleteConversation(conversationId, userId)
            .replaceWith(Response.ok().build());
    }
    
    // ========== DTOs ==========
    
    public static class UserRequest {
        @NotBlank
        public String username;
        
        @NotBlank
        public String email;
    }
    
    public static class ConversationRequest {
        @NotBlank
        public String title;
    }
}
