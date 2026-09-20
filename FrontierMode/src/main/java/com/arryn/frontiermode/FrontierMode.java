package com.arryn.frontiermode;

import com.arryn.frontiermode.border.BorderModule;
import net.minecraft.server.level.ServerLevel;
import com.arryn.frontiermode.border.server.commands.BorderSelectorArgumentType;
import com.arryn.frontiermode.border.server.commands.BorderSelectorArgumentTypeInfo;
import com.arryn.frontiermode.boss.BossModule;
import com.arryn.frontiermode.boss.server.commands.BossSelectorArgumentType;
import com.arryn.frontiermode.boss.server.commands.BossSelectorArgumentTypeInfo;
import com.arryn.frontiermode.bosstell.BossTellModule;
import com.arryn.frontiermode.effects.EffectsMod;
import com.arryn.frontiermode.feral.FeralModule;
import com.arryn.frontiermode.structure.StructureModule;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(FrontierMode.MODID)
public final class FrontierMode {

    public static final String MODID = "frontiermode";

    // ------------------------------------------------------------
    // Command Argument Type Registration (Forge Registry)
    // ------------------------------------------------------------
    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES =
            DeferredRegister.create(ForgeRegistries.COMMAND_ARGUMENT_TYPES, MODID);

    public static final RegistryObject<ArgumentTypeInfo<?, ?>> BORDER_SELECTOR =
            ARGUMENT_TYPES.register("border_selector", BorderSelectorArgumentTypeInfo::new);

    // FRO_057 (RM_FRO_022, "Joyce"): /boss's own selector argument type, same registration shape
    // as BORDER_SELECTOR above.
    public static final RegistryObject<ArgumentTypeInfo<?, ?>> BOSS_SELECTOR =
            ARGUMENT_TYPES.register("boss_selector", BossSelectorArgumentTypeInfo::new);

    // ------------------------------------------------------------
    // Sound Event Registration (Forge Registry)
    // RM_FRO_037 ("Brenda," Frontier Sickness epoch 1): Entry Cue's real sound -- replaces the
    // SoundEvents.AMBIENT_CAVE placeholder BorderModule.onFrontierSicknessTick used until the
    // project owner supplied an actual asset. Same DeferredRegister/RegistryObject shape as
    // ARGUMENT_TYPES above -- the first real SoundEvent this mod has registered, so there was no
    // existing facility to reuse here.
    // ------------------------------------------------------------
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<SoundEvent> ENTRY_CUE =
            SOUND_EVENTS.register(
                    "entry_cue",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "entry_cue")));

    // RM_FRO_037 boss-audio-tell follow-up (2026-09-08): first of up to 5 real round-robin clips
    // for BossTellFixture's ambient tell sound (see that class's own TELL_SOUNDS doc) -- project
    // owner is supplying these incrementally to confirm the pipeline before converting the rest,
    // so only slot 1 is a real registered SoundEvent for now; the remaining slots stay the
    // AMBIENT_CAVE placeholder until supplied. Same registration shape as ENTRY_CUE above.
    public static final RegistryObject<SoundEvent> BOSS_TELL_1 =
            SOUND_EVENTS.register(
                    "boss_tell_1",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "boss_tell_1")));

    // 2026-09-08: remaining 4 slots -- project owner supplied these directly as ready-to-use mono
    // OGG Vorbis (no re-encoding done or needed; see this ticket's own log for why bitrate/sample
    // rate don't need to be standardized across these). Same registration shape as BOSS_TELL_1/
    // ENTRY_CUE above.
    public static final RegistryObject<SoundEvent> BOSS_TELL_2 =
            SOUND_EVENTS.register(
                    "boss_tell_2",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "boss_tell_2")));

    public static final RegistryObject<SoundEvent> BOSS_TELL_3 =
            SOUND_EVENTS.register(
                    "boss_tell_3",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "boss_tell_3")));

    public static final RegistryObject<SoundEvent> BOSS_TELL_4 =
            SOUND_EVENTS.register(
                    "boss_tell_4",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "boss_tell_4")));

    public static final RegistryObject<SoundEvent> BOSS_TELL_5 =
            SOUND_EVENTS.register(
                    "boss_tell_5",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "boss_tell_5")));

    // ------------------------------------------------------------
    // Constructor – module bootstrap
    // ------------------------------------------------------------
    public FrontierMode() {

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register custom argument types with Forge
        ARGUMENT_TYPES.register(modBus);

        // Register custom sound events with Forge
        SOUND_EVENTS.register(modBus);

        // FRO_118: BossTellModule.ITEMS lives on that module, not here (project owner's call --
        // see BossTellModule's own class doc), but a DeferredRegister still needs the mod bus
        // instance to register onto, which only exists in this constructor.
        BossTellModule.ITEMS.register(modBus);

        // FRO_120: same reasoning as BossTellModule.ITEMS above -- StructureModule.BLOCKS lives
        // on that module, only registered here because the mod bus instance is only available
        // in this constructor.
        StructureModule.BLOCKS.register(modBus);

        // Register configuration
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Hook lifecycle events
        modBus.addListener(this::commonSetup);

        // Register server-side events (@SubscribeEvent methods)
        MinecraftForge.EVENT_BUS.register(this);

        OUT.info("FrontierMode: Module initialization started.");

        // Initialize the Border module (fixtures, tick handler, commands)
        BorderModule.init();

        // RM_FRO_018 (Shirley): Boss entity/spawn system. Depends on Border -- must init after
        // it, never the reverse. FRO_075: the level-bootstrap pairing (first Border + its paired
        // Boss record) is BossModule's own ScopeEvent.Loaded handler now, registered from inside
        // this same init() call -- see BossModule.onBordersScopeLoaded's own doc.
        BossModule.init();

        // FRO_118 (RM_FRO_033, "Marilyn"): Admin Compass + shared core. No fixture/init-order
        // dependency on Border or Boss's own module init -- it reaches BossAPI/BorderAPI from
        // outside, the same one-directional relationship Feral has with both (see
        // BossTellModule's own class doc). Placed after BossModule.init() only for bootstrap-log
        // ordering, not because anything here requires it.
        BossTellModule.init();

        // FRO_120 (RM_FRO_039, "Helen"): Cairn's own third home. Reads BossAPI/BorderAPI from
        // outside, no fixture/init-order dependency on Border's or Boss's own module init --
        // registers into BossPositionListeners at its own init() instead (see StructureModule's
        // class doc).
        StructureModule.init();

        // FRO_089: cross-cutting particle/sound dispatch module. No fixture/init-order
        // dependency on Border or Boss -- both now route their effects through it.
        EffectsMod.init();

        // RM_FRO_038 ("Martha"): Feral mobs. No fixture/init-order dependency on Border/Boss
        // either -- reads BorderAPI/EffectsAPI at spawn time, same as Guardian Mobs reads them
        // from inside Boss's own module. Kept as its own module deliberately -- see FeralModule's
        // own doc.
        FeralModule.init();
    }

    // ------------------------------------------------------------
    // Common Setup (runs once on both sides)
    // ------------------------------------------------------------
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ArgumentTypeInfos.registerByClass(
                    BorderSelectorArgumentType.class,
                    (ArgumentTypeInfo<BorderSelectorArgumentType, BorderSelectorArgumentTypeInfo.Template>)
                            FrontierMode.BORDER_SELECTOR.get()
            );
            ArgumentTypeInfos.registerByClass(
                    BossSelectorArgumentType.class,
                    (ArgumentTypeInfo<BossSelectorArgumentType, BossSelectorArgumentTypeInfo.Template>)
                            FrontierMode.BOSS_SELECTOR.get()
            );
        });
    }


    // ------------------------------------------------------------
    // Command Registration
    // Delegated fully to BorderModule
    // ------------------------------------------------------------
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BorderModule.onRegisterCommands(event);
        // FRO_057 (RM_FRO_022, "Joyce"): registers /boss alongside /border.
        BossModule.onRegisterCommands(event);
        // FRO_118: merges /boss debug compass into the /boss -> debug node BossModule just
        // registered above -- order matters for readability only, not correctness (Brigadier's
        // own addChild merge doesn't care which registration runs first).
        BossTellModule.onRegisterCommands(event);
        // RM_FRO_038 ("Martha"): registers /feral alongside /border and /boss.
        FeralModule.onRegisterCommands(event);
    }

    // ------------------------------------------------------------
    // RM_FRO_029 ("Gloria") / RM_FRO_037 ("Brenda") / RM_FRO_038 ("Martha"): the mod's one raw
    // MobSpawnEvent.FinalizeSpawn touch point. Same shape as onRegisterCommands above -- this
    // stays the single real Forge-subscription class, delegating immediately, by name, to each
    // module rather than holding any decision logic itself. Guardian Mobs (Boss-side, guardian
    // conversion on hostiles near a boss), Sick Wildlife's density boost (Border-side, extra
    // spawns on passive mobs in the Exterior), and Feral (its own module, hostile conversion in
    // the Exterior) are independent concerns on the same event -- all three get a look, none
    // cancels the spawn either way, so calling them in sequence is safe and needs no
    // coordination between them.
    // ------------------------------------------------------------
    @SubscribeEvent
    public void onMobSpawnFinalize(MobSpawnEvent.FinalizeSpawn event) {
        BossModule.onMobSpawnFinalize(event);

        if (event.getLevel() instanceof ServerLevel level) {
            BorderModule.onMobSpawnFinalize(event.getEntity(), level);
            FeralModule.onMobSpawnFinalize(event.getEntity(), level);
        }
    }

    // ------------------------------------------------------------
    // RM_FRO_038 ("Martha"): fires earlier than FinalizeSpawn above -- before a mob attempts to
    // spawn at all, where vanilla's own light-level gate lives. Same single-subscription-class
    // shape as every other @SubscribeEvent on this file.
    // ------------------------------------------------------------
    @SubscribeEvent
    public void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        FeralModule.onSpawnPlacementCheck(event);
    }

    // ------------------------------------------------------------
    // RM_FRO_038 ("Martha"): Feral's own cosmetic tell on already-spawned mobs. Feral's own tick
    // hook, not Border's -- see FeralModule's own class doc for why it can't ride
    // BorderModule.onPlayerScopeTick instead.
    // ------------------------------------------------------------
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        FeralModule.onPlayerTick(event);
    }

    // ------------------------------------------------------------
    // FRO_111 ("Martha"): kills a Feral mob that wandered back inside the Frontier. Level-wide,
    // not player-radius-bound like onPlayerTick above -- see FeralModule.onLevelTick's own doc.
    // ------------------------------------------------------------
    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        FeralModule.onLevelTick(event);
    }

    // ------------------------------------------------------------
    // RM_FRO_038 ("Martha"): no free loot from a Feral mob, however it dies -- see
    // FeralModule.onLivingDrops's own doc for why both drop paths are closed rather than trying
    // to distinguish a combat kill from the FRO_111 re-entry kill.
    // ------------------------------------------------------------
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        FeralModule.onLivingDrops(event);
    }

    @SubscribeEvent
    public void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        FeralModule.onLivingExperienceDrop(event);
    }

    // ------------------------------------------------------------
    // RM_FRO_038 ("Martha"): Feral's actual tagging authority -- catches anything that joins a
    // level however it got there (natural spawn, Slime/MagmaCube split children, this module's
    // own density companions), not just the ones that pass through FinalizeSpawn. See
    // FeralModule.onEntityJoinLevel's own doc for why tagging moved here.
    // ------------------------------------------------------------
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        FeralModule.onEntityJoinLevel(event);
    }

}
