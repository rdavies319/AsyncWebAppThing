package com.rdavies.api.web;

import com.rdavies.api.GameRuleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns game rule failures into ProblemDetails for the JSON API only: a bad request (stake out of
 * range) is 400; a request that conflicts with the game's state (top-up already claimed, not
 * enough points) is 409. Malformed JSON gets Spring's standard 400, and any
 * other exception is a bug and gets a 500 without its message.
 */
@RestControllerAdvice(assignableTypes = GameApiController.class)
public class GameApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GameRuleException.class)
    public ProblemDetail ruleRefused(GameRuleException e) {
        HttpStatus status = switch (e.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }
}
