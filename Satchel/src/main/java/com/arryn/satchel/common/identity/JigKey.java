package com.arryn.satchel.common.identity;



import com.arryn.satchel.common.jig.guts.SatchelJig;
import com.arryn.satchel.common.jig.guts.SatchelScope;

import java.util.UUID;

public final class JigKey<J extends SatchelJig<? extends SatchelScope>>
        extends SatchelKey<J> {

    public JigKey(String name, Class<J> type) {
        super(name, type);
    }

    public JigKey(UUID id, String name, Class<J> type) {
        super(id, name, type);
    }

     public static void validateTypes(Object key, Object jig) {

        if (!(key instanceof JigKey<?> jk)) {
            throw new IllegalStateException("Key is not a JigKey: " + key);
        }

        if (!(jig instanceof SatchelJig<?> sj)) {
            throw new IllegalStateException("Object is not a SatchelJig: " + jig);
        }

        Class<?> keyType = jk.type;
        Class<?> jigType = sj.getClass();

        if (keyType != jigType) {
            throw new IllegalStateException(
                    "JigKey type mismatch: key=" + keyType.getName() +
                            ", requireJig=" + jigType.getName()
            );
        }
    }
}
