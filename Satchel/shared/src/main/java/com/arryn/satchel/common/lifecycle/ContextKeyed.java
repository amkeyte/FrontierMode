package com.arryn.satchel.common.lifecycle;

/**
 * A module event's context that names its own key on the {@link SatchelEventBus}. The bus delivers a
 * module event to the global subscribers plus those registered under the event's context key; a
 * context that is not {@code ContextKeyed} is its own key. {@code ScopeInfo} is {@code ContextKeyed}
 * and answers its scope's id, so a scope-bound subscription is found by a map lookup.
 */
public interface ContextKeyed {

    /**
     * The key subscriptions for this context are filed under. Stable for the context's lifetime.
     */
    Object contextKey();
}
