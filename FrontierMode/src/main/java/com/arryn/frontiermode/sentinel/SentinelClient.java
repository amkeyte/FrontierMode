package com.arryn.frontiermode.sentinel;

import com.arryn.frontiermode.sentinel.client.SentinelTrailClient;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Ticking;
import net.minecraft.world.level.Level;

/**
 * The client Trinket on {@code SENTINEL_JIG} (sentinels.md § The Sentinel module, § The trail): each tick it hands the
 * level and the synced {@link com.arryn.frontiermode.sentinel.common.fixture.SentinelFixture} to
 * {@link SentinelTrailClient}, which draws the trail and keeps each Sentinel's client hitbox in step with its synced
 * scale. The factory runs once per side and the Trinket exists on both, so it acts on the client only; its server
 * twin is {@link SentinelPresence}.
 */
final class SentinelClient implements Ticking {

    @Override
    public void onTickAfter(ScopeInfo info) {
        if (!Satchel.isClient()) {
            return;
        }
        LevelScope scope = info.scopeAs();
        Level level = scope.level();
        SentinelMarkers.clientFixture(level).ifPresent(fixture -> SentinelTrailClient.tick(level, fixture));
    }
}
