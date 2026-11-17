package com.arryn.satchel.common.ingress;

import com.arryn.satchel.common.identity.SatchelKey;

/**
 * Names an {@link IngressConfig}. Its name owns the subscriptions the ingress installs, so a module
 * names it after itself, as in {@code new IngressKey("frontiermode:boss")}.
 */
public final class IngressKey extends SatchelKey<IngressConfig> {

    public IngressKey(String name) {
        super(name, IngressConfig.class);
    }
}
