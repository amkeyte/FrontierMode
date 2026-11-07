package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.sentinel.client.SentinelTrailClient;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Ticking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The client Trinket on {@code SENTINEL_JIG} (sentinels.md § The Sentinel module, § The trail): each tick it hands the
 * level, the synced {@link com.arryn.frontiermode.sentinel.common.fixture.SentinelFixture} and the scales it last
 * applied to {@link SentinelTrailClient}, which draws the trail and keeps each Sentinel's client hitbox in step with
 * its synced scale. The factory runs once per side and the Trinket exists on both, so it acts on the client only; its
 * server twin is {@link SentinelPresence}.
 */
final class SentinelClient implements Ticking {

    // The scale each Sentinel's client dimensions were last refreshed at. Weak, so an entry ends with its entity; a
    // field of the Trinket, so it ends with the world (new-module-checklist.md item 14).
    private final Map<Entity, Float> appliedScale = new WeakHashMap<>();

    @Override
    public void onTickAfter(ScopeInfo info) {
        if (!Satchel.isClient()) {
            return;
        }
        LevelScope scope = info.scopeAs();
        Level level = scope.level();
        SentinelMarkers.clientFixture(level).ifPresent(
                fixture -> SentinelTrailClient.tick(level, fixture, appliedScale));
    }
}
