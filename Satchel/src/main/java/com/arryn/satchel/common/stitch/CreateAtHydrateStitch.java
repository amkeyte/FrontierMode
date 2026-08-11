package com.arryn.satchel.common.stitch;

public interface CreateAtHydrateStitch {

    /**
     * @return true if persistence may create missing facets during hydrate
     */
    boolean allowCreateAtHydrate();
}
