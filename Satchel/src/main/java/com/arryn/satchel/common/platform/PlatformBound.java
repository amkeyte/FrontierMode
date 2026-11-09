package com.arryn.satchel.common.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class that holds Minecraft or Forge references on purpose, so a port to another loader or
 * Minecraft version finds it with one search.
 *
 * <p>
 * Satchel is where the game platform is dealt with. Platform references belong in Satchel's own
 * platform layer (the ingress and egress classes, the side-routing facades, {@code MobDataStore})
 * and in classes extended from Satchel types (a fixture, a module event, a Trinket, a coupler, a
 * bundle). This marker is for the exception: a class that needs platform references and extends
 * nothing from Satchel. A class extended from a Satchel type is platform-bound by that fact and
 * needs no marker, and the marker does not excuse a platform reference in a class that game logic is
 * meant to share.
 *
 * <p>
 * A class with a direct Forge listener ({@code @SubscribeEvent}, {@code @Mod.EventBusSubscriber} or a call to
 * {@code addListener}) must carry it, with a reason: a build-time test fails otherwise. Elsewhere it is a
 * direction, not a gate.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PlatformBound {

    /**
     * Why this class holds platform references, for the reader who finds it: the concern it serves
     * ({@code registration}, {@code commands}, {@code render}, {@code pre-level-listeners},
     * {@code health-probe}). Required, and not empty, on a class with a direct Forge listener; optional
     * elsewhere.
     */
    String reason() default "";
}
