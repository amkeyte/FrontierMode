package com.arryn.frontiermode;

import com.arryn.frontiermode.admin.AdminToolsModule;
import com.arryn.frontiermode.border.BorderModule;
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
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

//TODO AmethystCompassClient::onClientSetup has the correct ResourceLocation pattern. Update to this pattern.
//'ResourceLocation(java.lang.String, java.lang.String)' is deprecated since version 1.20.6 and marked for removal


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
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "entry_cue")));

    // RM_FRO_037 boss-audio-tell follow-up (2026-09-08): first of up to 5 real round-robin clips
    // for BossTellFixture's ambient tell sound (see that class's own TELL_SOUNDS doc) -- project
    // owner is supplying these incrementally to confirm the pipeline before converting the rest,
    // so only slot 1 is a real registered SoundEvent for now; the remaining slots stay the
    // AMBIENT_CAVE placeholder until supplied. Same registration shape as ENTRY_CUE above.
    public static final RegistryObject<SoundEvent> BOSS_TELL_1 =
            SOUND_EVENTS.register(
                    "boss_tell_1",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "boss_tell_1")));

    // 2026-09-08: remaining 4 slots -- project owner supplied these directly as ready-to-use mono
    // OGG Vorbis (no re-encoding done or needed; see this ticket's own log for why bitrate/sample
    // rate don't need to be standardized across these). Same registration shape as BOSS_TELL_1/
    // ENTRY_CUE above.
    public static final RegistryObject<SoundEvent> BOSS_TELL_2 =
            SOUND_EVENTS.register(
                    "boss_tell_2",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "boss_tell_2")));

    public static final RegistryObject<SoundEvent> BOSS_TELL_3 =
            SOUND_EVENTS.register(
                    "boss_tell_3",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "boss_tell_3")));

    public static final RegistryObject<SoundEvent> BOSS_TELL_4 =
            SOUND_EVENTS.register(
                    "boss_tell_4",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "boss_tell_4")));

    public static final RegistryObject<SoundEvent> BOSS_TELL_5 =
            SOUND_EVENTS.register(
                    "boss_tell_5",
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, "boss_tell_5")));

    // ------------------------------------------------------------
    // Constructor – module bootstrap
    // ------------------------------------------------------------
    public FrontierMode(FMLJavaModLoadingContext context) {

        IEventBus modBus = context.getModEventBus();

        // Register custom argument types with Forge
        ARGUMENT_TYPES.register(modBus);

        // Register custom sound events with Forge
        SOUND_EVENTS.register(modBus);

        // FRO_118: BossTellModule.ITEMS lives on that module, not here (project owner's call --
        // see BossTellModule's own class doc), but a DeferredRegister still needs the mod bus
        // instance to register onto, which only exists in this constructor.
        BossTellModule.ITEMS.register(modBus);
        // The sun ward's register lives on BossModule for the same reason.
        BossModule.ITEMS.register(modBus);
        // FRO_121: same reasoning as ITEMS above.
        BossTellModule.ENCHANTMENTS.register(modBus);

        // FRO_120: same reasoning as BossTellModule.ITEMS above -- StructureModule.BLOCKS lives
        // on that module, only registered here because the mod bus instance is only available
        // in this constructor.
        StructureModule.BLOCKS.register(modBus);

        // Admin tools: same reasoning as BossTellModule.ENCHANTMENTS above.
        AdminToolsModule.ENCHANTMENTS.register(modBus);

        // Register configuration
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Hook lifecycle events
        modBus.addListener(this::commonSetup);

        // Register server-side events (@SubscribeEvent methods)
        MinecraftForge.EVENT_BUS.register(this);

        OUT.info("FrontierMode: Module initialization started.");

        // FRO_155: silence vanilla's per-death log line for our own named mobs (Feral, Boss).
        LogFilters.install();

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

        // Admin tools -- no fixture/init-order dependency on anything else.
        AdminToolsModule.init();

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
    @SuppressWarnings("unchecked")
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
        // FRO_120: merges /boss structure debug goto into the same /boss root, same Brigadier
        // merge behavior.
        StructureModule.onRegisterCommands(event);
        // RM_FRO_038 ("Martha"): registers /feral alongside /border and /boss.
        FeralModule.onRegisterCommands(event);
        // Admin tools: registers /admin as its own, separate root -- not merged into /boss.
        AdminToolsModule.onRegisterCommands(event);
    }

    // ------------------------------------------------------------
    // FRO_133: Frontier Sickness's own lingering effects should clear on respawn, not persist
    // through death -- see BorderModule.onPlayerRespawn/clearSicknessEffectsOnRespawn's own docs.
    // ------------------------------------------------------------
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        BorderModule.onPlayerRespawn(event);
    }

}
