package com.hezron.smecommerce.assistant.internal.web;

import com.hezron.smecommerce.assistant.internal.application.AssistantAnswerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The storefront's assistant: no login, one shop per URL. */
@RestController
class StorefrontAssistantController {

    private final AssistantAnswerService answers;

    StorefrontAssistantController(AssistantAnswerService answers) {
        this.answers = answers;
    }

    /** {@code POST /api/store/{shopSlug}/assistant/ask}. */
    @PostMapping("/api/store/{shopSlug}/assistant/ask")
    AnswerResponse ask(@PathVariable String shopSlug, @Valid @RequestBody AskRequest request,
            HttpServletRequest http) {
        return AnswerResponse.fromResult(answers.ask(shopSlug, request.question(), http.getRemoteAddr()));
    }

}
