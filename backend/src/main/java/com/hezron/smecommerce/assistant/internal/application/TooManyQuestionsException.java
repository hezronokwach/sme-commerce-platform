package com.hezron.smecommerce.assistant.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** The caller asked too many questions this minute. Returns 429. */
public class TooManyQuestionsException extends BaseException {

    public TooManyQuestionsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_QUESTIONS", "Too many questions",
                "Please wait a minute before asking again.");
    }

}
