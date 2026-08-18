package com.arryn.frontiermode.border;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.Rendering;
import com.arryn.frontiermode.border.common.bundle.BordersBundle;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.frontiermode.border.common.player.BorderPlayerBundle;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatusFixture;
import com.arryn.frontiermode.border.common.player.BorderPlayerStatusProposal;
import com.arryn.frontiermode.border.server.commands.BorderCommands;
import com.arryn.frontiermode.border.server.rules.BordersTriggers;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.player.PlayerJig;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.newconfig.EventHandlers;
import com.arryn.satchel.common.newconfig.JigBundles;
import com.arryn.satchel.common.newconfig.newnew.JigPolicies;
import com.arryn.satchel.common.newconfig.newnew.LevelJigConfig;
import com.arryn.satchel.common.newconfig.newnew.PlayerJigConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.util.List;
import java.util.Objects;

/**
 * Entry point for initializing and hooking the Border subsystem.
 */
public final class BorderModule {
    private BorderModule() {
    }


    public static void init() {
        OUT.info("FrontierMode - Border Module Initiating.");

        // ─────────────────────────────────────────────
        // Bundle schema -- FRO_021: RM_SAT_012 consolidated ScopeEngine.create()/.get() onto
        // this schema (bundleDecls) directly; the separate BundleFactories.registerFactory(...)
        // call this module used to also make was dead weight (confirmed via grep -- nothing
        // anywhere calls BundleFactories.entryFor(...) anymore) and has been removed, mirroring
        // TrackingModule.init()'s already-cleaned state. See RM_SAT_012's roadmap node for the
        // full history of that duality.
        // ─────────────────────────────────────────────
        var bordersFixture =
                new JigBundles.FixtureDecl<BordersFixture>(
                        FrontierKeys.BORDERS,
                        BordersFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var bordersBundle =
                new JigBundles.BundleDecl<LevelScope, BordersBundle>(
                        FrontierKeys.BORDERS_BUNDLE,
                        (LevelScope scope) -> new BordersBundle(scope, FrontierKeys.BORDERS_BUNDLE),
                        List.of(bordersFixture)
                );

        JigBundles.Schema<LevelScope> bundles =
                new JigBundles.Schema<>(List.of(bordersBundle));

        // ─────────────────────────────────────────────
        // Event handlers (replaces BorderStrap)
        // ─────────────────────────────────────────────
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, BordersTriggers::updateFinderItems)
                        .on(ScopeEvent.Tick.class, Rendering::onClientTick)
                        // RM_FRO_012: RenderContext.CACHE eviction -- see Rendering.onClientUnload
                        // and RenderContext.evict for the full reasoning.
                        .on(ScopeEvent.Unloaded.class, Rendering::onClientUnload)
                        .build();

        // ─────────────────────────────────────────────
        // JigConfig targeting LevelJig -- one BOTH-applicability config. compileForSide()
        // runs once per side's own foundation boot, so this yields one independent
        // LevelJig instance per side automatically, same as the two separate
        // SatchelJigRegistrar.register(...) calls this replaces.
        // ─────────────────────────────────────────────
        LevelJigConfig config = new LevelJigConfig(FrontierKeys.BORDERS_JIG);

        config.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);

        config.bundles().schema(bundles);

        // Borders persist across restarts -- ScopeEngine_Server's hydrate/flush path
        // requires this capability declared, or it throws AccessFailed the first time
        // a border loads.
        config.policies().capabilities(
                new JigPolicies.Capabilities(true, false, false));

        // capabilities().requiresPersistence=true is cross-checked by JigConfigValidator
        // against this policy's own persistent flag -- declaring the capability alone is
        // not enough; without this call JigConfigValidator.validateCapabilities rejects the
        // config at foundation boot (on both sides, since this config is BOTH-applicability)
        // with "requires persistence but persistence policy is not persistent" before any
        // level ever loads. flushOn/missingPolicy aren't consumed by anything yet (grepped:
        // no reader exists), but set to real values rather than defaults so the policy
        // reads as intentional, not a leftover default.
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.UNLOAD,
                        JigPolicies.MissingDataPolicy.WARN));

        // JigPolicies.Lifecycle.defaults() is (load=true, unload=true, tick=false,
        // executionPulse=false) -- withTick(true) alone leaves executionPulse false. But
        // ScopeEngine_Server.flushIfDirty()/scheduleSync() (persistence flush + client sync) and
        // ScopeEngine_Client.applyIncomingParcels() (parcel drain) are ONLY ever called from
        // onExecutionPulse -- AScopeCoupler.onExecutionPulse() gates on
        // execution().lifecycle().participatesInExecutionPulse() before even calling into the
        // engine. With it false, Border's own flush-then-sync path never ran a single time on
        // either side, regardless of SAT_026's network registration fix -- the send call was
        // simply never reached. withExecutionPulse(true) is required for persistence and
        // client rendering to receive real data at all. See FRO_018.
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);

        // ─────────────────────────────────────────────
        // RM_FRO_006 (Sandra): per-player border evaluation, PlayerJig-scoped. A second,
        // independent JigConfig alongside the LevelJig one above -- PlayerTrackingModule (Satchel)
        // is the template this follows for wiring a bundle onto PlayerJig/PlayerScope, same
        // division of labor TrackingModule's LevelJigConfig usage already establishes for this
        // module's own BordersBundle registration above.
        //
        // Deliberately real PlayerJig/PlayerScope, not a LevelScope workaround hosted on
        // BordersBundle -- per-player state (nearest border, distance, inside flag) is genuinely
        // identity-tied and must follow the player across dimensions without a manual handoff.
        // See RM_FRO_006's own roadmap node for the full design ruling (2026-08-14) and RM_SAT_020
        // for why this had to wait on PlayerJig/PlayerScope landing first.
        // ─────────────────────────────────────────────
        var borderPlayerFixture =
                new JigBundles.FixtureDecl<BorderPlayerStatusFixture>(
                        FrontierKeys.BORDER_PLAYER_STATUS,
                        BorderPlayerStatusFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var borderPlayerBundle =
                new JigBundles.BundleDecl<PlayerScope, BorderPlayerBundle>(
                        FrontierKeys.BORDER_PLAYER_BUNDLE,
                        (PlayerScope scope) -> new BorderPlayerBundle(scope, FrontierKeys.BORDER_PLAYER_BUNDLE),
                        List.of(borderPlayerFixture)
                );

        JigBundles.Schema<PlayerScope> playerBundles =
                new JigBundles.Schema<>(List.of(borderPlayerBundle));

        EventHandlers playerEventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, BorderModule::onPlayerScopeTick)
                        .build();

        // PlayerJigConfig already pins jigType/couplerType/scopeType/sourceType/
        // sideApplicability(SERVER)/scopeResolver/uuidDeterminer to the PlayerJig defaults this
        // module needs, and its lifecycle preset already has withTick(true) -- only bundles and
        // eventHandlers are per-module, same as PlayerTrackingModule's own usage. Not persisted,
        // not networked: BorderPlayerStatus is a live-recomputed snapshot (see
        // BorderPlayerStatusFixture's own doc), so no capabilities()/persistence() override is
        // needed here, unlike the LevelJigConfig above (Border's own state genuinely must survive
        // a restart).
        PlayerJigConfig playerConfig = new PlayerJigConfig(FrontierKeys.BORDER_PLAYER_JIG);

        playerConfig.bundles().schema(playerBundles);

        playerConfig.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(playerEventHandlers);

        Satchel.registerJigConfig(playerConfig);

        // onBlockPlaced is a plain static method, not an @SubscribeEvent instance method on a
        // registered listener object -- FrontierMode's constructor only does
        // MinecraftForge.EVENT_BUS.register(this), which picks up @SubscribeEvent methods
        // declared on FrontierMode itself (just onRegisterCommands). Nothing was ever wiring
        // BlockEvent.EntityPlaceEvent to BorderModule::onBlockPlaced, so gold-block path growth
        // was fully dead code: growPathCriteria/BorderAPI.grow() were unreachable from any real
        // gameplay action, not just untested. See FRO_017.
        MinecraftForge.EVENT_BUS.addListener(BorderModule::onBlockPlaced);
    }


    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BorderCommands.register(event.getDispatcher());
    }

    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        BordersTriggers.growPath(event);
    }

    // ─────────────────────────────────────────────
    // RM_FRO_006 (Sandra) tick handler
    // ─────────────────────────────────────────────

    /**
     * Recomputes the ticking player's {@link BorderPlayerStatusFixture} against their current
     * level's live border list. Shared-bus caveat, same as every other {@code ScopeEvent} handler
     * in this codebase (e.g. {@code PlayerTrackingModule}'s handlers): {@code ScopeEvent.Tick}
     * fires for every jig scoped to whatever just ticked, not just this one -- the
     * {@code BORDER_PLAYER_JIG} key check below is what keeps this from running against the
     * wrong jig's scopes.
     */
    private static void onPlayerScopeTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.BORDER_PLAYER_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = (PlayerJig) info.jigInfo().jig;
        PlayerScope scope = (PlayerScope) info.scope();
        ServerPlayer player = scope.player();

        BorderPlayerBundle bundle = jig.getOrCreate(scope, FrontierKeys.BORDER_PLAYER_BUNDLE);
        BorderPlayerStatusFixture fixture =
                bundle.getOrCreateFixture(FrontierKeys.BORDER_PLAYER_STATUS, BorderPlayerStatusFixture::new);

        List<Border> borders =
                BorderAPI.borders(player.serverLevel())
                        .map(b -> b.CRUD.all())
                        .orElseGet(List::of);

        fixture.accept(
                new BorderPlayerStatusProposal(player.getUUID()),
                borders,
                player.blockPosition()
        );
    }
}
