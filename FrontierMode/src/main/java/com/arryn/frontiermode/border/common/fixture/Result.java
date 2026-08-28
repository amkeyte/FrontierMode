package com.arryn.frontiermode.border.common.fixture;

import java.util.Objects;

/**
 * Outcome of a Border mutation. {@link BordersCrudFacet#applyProposal(BorderProposal)} and every
 * {@code BorderAPI} operation built on it return this instead of throwing or returning
 * {@code Optional.empty()} -- see the Border wiki page's "Mutation surface" section (FRO_046's
 * ruling, built by FRO_047). Modeled on {@code BorderSelectorResult}'s established shape in this
 * codebase: static factories, final fields, tagged by an enum.
 */
public final class Result {

    public enum Outcome {
        SUCCESS,
        FAILURE
    }

    /**
     * Populated only on failure -- distinguishes a transient not-ready state (the level's Border
     * facet isn't resolvable yet) from a permanent validation rejection (bad radius, negative
     * layer) from a not-found lookup (no border with the given id).
     */
    public enum FailureKind {
        NOT_READY,
        VALIDATION_REJECTED,
        NOT_FOUND
    }

    private final Outcome outcome;
    private final FailureKind failureKind;
    private final String message;
    private final Border border;

    private Result(Outcome outcome, FailureKind failureKind, String message, Border border) {
        this.outcome = outcome;
        this.failureKind = failureKind;
        this.message = message;
        this.border = border;
    }

    public static Result success(Border border) {
        return new Result(Outcome.SUCCESS, null, null, Objects.requireNonNull(border, "border"));
    }

    public static Result notReady(String message) {
        return new Result(Outcome.FAILURE, FailureKind.NOT_READY, message, null);
    }

    public static Result validationRejected(String message) {
        return new Result(Outcome.FAILURE, FailureKind.VALIDATION_REJECTED, message, null);
    }

    public static Result notFound(String message) {
        return new Result(Outcome.FAILURE, FailureKind.NOT_FOUND, message, null);
    }

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    public Outcome outcome() {
        return outcome;
    }

    public FailureKind failureKind() {
        return failureKind;
    }

    public String message() {
        return message;
    }

    /**
     * The resulting {@link Border} on success. {@code null} on failure -- check
     * {@link #isSuccess()} first.
     */
    public Border border() {
        return border;
    }
}
