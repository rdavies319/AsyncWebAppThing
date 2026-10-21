package com.rdavies.api;

/**
 * A request the game's rules refuse, with a message for the player: shown inline on the page, and a
 * 400 or 409 ProblemDetail from the JSON API. Anything else a service throws is a bug and becomes a 500.
 */
public class GameRuleException extends RuntimeException {
    public enum Kind {
        /** The request itself is wrong (an invalid signup, a stake out of range): 400. */
        INVALID,
        /** The request clashes with the game's state (top-up already claimed, too few points): 409. */
        CONFLICT
    }

    private final Kind kind;

    private GameRuleException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public static GameRuleException invalid(String message) {
        return new GameRuleException(Kind.INVALID, message, null);
    }

    public static GameRuleException invalid(String message, Throwable cause) {
        return new GameRuleException(Kind.INVALID, message, cause);
    }

    public static GameRuleException conflict(String message) {
        return new GameRuleException(Kind.CONFLICT, message, null);
    }

    public static GameRuleException conflict(String message, Throwable cause) {
        return new GameRuleException(Kind.CONFLICT, message, cause);
    }

    public Kind kind() {
        return kind;
    }
}
