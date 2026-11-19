package com.nova.constructor.common;

import org.springframework.http.HttpStatus;

/** Erro de negócio convertido em ProblemDetail com o status informado. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
