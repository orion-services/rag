/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */

package dev.rpmhub.infrastructure.adapter;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;

/**
 * Personalized AI service interface for handling AI interactions.
 */
@RegisterAiService
public interface LangChainAIService {

        String DEFAULT_SYSTEM_MESSAGE = "Você é um assistente de programação " +
                        "para estudantes você deve detalhar os exemplos de código e explicar " +
                        "conceitos. Responda em português. " +
                        "IMPORTANTE: Sempre formate suas respostas com quebras de linha adequadas. " +
                        "Use quebras de linha duplas (\\n\\n) para separar parágrafos e seções. " +
                        "Use quebras de linha simples (\\n) dentro de parágrafos quando necessário. " +
                        "Formate listas, títulos e seções com espaçamento adequado para melhor legibilidade.";

        @SystemMessage(DEFAULT_SYSTEM_MESSAGE)
        @UserMessage("{prompt}")
        Multi<String> generateResponse(String prompt);

        @SystemMessage(DEFAULT_SYSTEM_MESSAGE)
        @UserMessage("Histórico: {history}, Contexto: {context}, pergunta: {prompt}")
        Multi<String> generateContextualResponse(String history, String context,
                        String prompt);
}
