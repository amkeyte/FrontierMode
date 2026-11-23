package com.arryn.frontiermode.structure;

import com.arryn.satchel.common.ingress.GlobalConsumers;

/**
 * The structure module's global consumers, written once for both lines: the one consumer of {@link CairnCapBroken}. Each
 * line's {@code StructureIngress} installs these.
 */
public final class StructureConsumers {

    private StructureConsumers() {
    }

    /** A fresh set of consumers: build one in the ingress's side-keyed factory. */
    public static GlobalConsumers declare() {
        GlobalConsumers declared = new GlobalConsumers();
        declared.listenForAll(CairnCapBroken.class, CairnHandlers::onCapBroken);
        return declared;
    }
}
