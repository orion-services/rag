/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.util;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Utility class to wrap blocking operations and make them non-blocking.
 * 
 * Executes blocking operations on executor thread and ensures the result
 * is emitted back on the original Vert.x EventLoop thread, allowing
 * subsequent reactive operations to work correctly.
 */
public class BlockingToReactive {
    
    private static final Executor EXECUTOR = io.quarkus.runtime.ExecutorRecorder.getCurrent();
    
    /**
     * Wraps a blocking operation and returns a Uni that:
     * 1. Executes the operation on executor thread (non-blocking for EventLoop)
     * 2. Emits the result back on the original EventLoop thread
     * 
     * @param blockingOperation the blocking operation to execute
     * @return a Uni that emits the result on the EventLoop thread
     */
    public static <T> Uni<T> wrap(Supplier<T> blockingOperation) {
        // Capture the current Vert.x context BEFORE executing blocking operations
        // This allows us to return to the EventLoop thread after blocking ops complete
        Context vertxContext = Vertx.currentContext();
        
        // Execute blocking operation on executor thread
        return Uni.createFrom().item(() -> {
                    // Execute the blocking operation
                    return blockingOperation.get();
                })
                // Execute blocking operation on executor thread
                .runSubscriptionOn(EXECUTOR)
                // CRITICAL: Use transformToUni to switch back to EventLoop context
                // This ensures subsequent reactive operations run on EventLoop thread
                .onItem().transformToUni(result -> {
                    if (vertxContext != null) {
                        // Create a new Uni that will be executed on the EventLoop
                        // by using runOnContext to schedule the emission
                        return Uni.createFrom().emitter(emitter -> {
                            vertxContext.runOnContext(v -> {
                                emitter.complete(result);
                            });
                        });
                    }
                    // No context, return as is (will stay on executor thread)
                    return Uni.createFrom().item(result);
                });
    }
    
    /**
     * Wraps a blocking operation that may throw checked exceptions.
     * 
     * @param blockingOperation the blocking operation that may throw exceptions
     * @return a Uni that emits the result on the EventLoop thread
     */
    public static <T> Uni<T> wrapThrowing(java.util.concurrent.Callable<T> blockingOperation) {
        Context vertxContext = Vertx.currentContext();
        
        return Uni.createFrom().item(() -> {
                    try {
                        return blockingOperation.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .runSubscriptionOn(EXECUTOR)
                .onItem().transformToUni(result -> {
                    if (vertxContext != null) {
                        return Uni.createFrom().emitter(emitter -> {
                            vertxContext.runOnContext(v -> {
                                emitter.complete(result);
                            });
                        });
                    }
                    return Uni.createFrom().item(result);
                });
    }
}

