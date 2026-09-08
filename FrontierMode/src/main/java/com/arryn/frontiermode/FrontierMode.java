package com.arryn.frontiermode;

import com.arryn.frontiermode.border.BorderModule;
import net.minecraft.server.level.ServerLevel;
import com.arryn.frontiermode.border.server.commands.BorderSelectorArgumentType;
import com.arryn.frontiermode.border.server.commands.BorderSelectorArgumentTypeInfo;
import com.arryn.frontiermode.boss.BossModule;
import com.arryn.frontiermode.boss.server.commands.BossSelectorArgumentType;
import com.arryn.frontiermode.boss.server.commands.BossSelectorArgumentTypeInfo;
import com.arryn.frontiermode.effects.EffectsMod;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
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

        // FRO_089: cross-cutting particle/sound dispatch module. No fixture/init-order
        // dependency on Border or Boss -- both now route their effects through it.
        EffectsMod.init();
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
    }

    // ------------------------------------------------------------
    // RM_FRO_029 ("Gloria") / RM_FRO_037 ("Brenda"): the mod's one raw
    // MobSpawnEvent.FinalizeSpawn touch point. Same shape as onRegisterCommands above -- this
    // stays the single real Forge-subscription class, delegating immediately, by name, to each
    // module rather than holding any decision logic itself. Guardian Mobs (Boss-side, guardian
    // conversion on hostiles near a boss) and Sick Wildlife's density boost (Border-side, extra
    // spawns on passive mobs in the Exterior) are independent concerns on the same event --
    // both get a look, neither cancels the spawn either way, so calling both in sequence is
    // safe and needs no coordination between them.
    // ------------------------------------------------------------
    @SubscribeEvent
    public void onMobSpawnFinalize(MobSpawnEvent.FinalizeSpawn event) {
        BossModule.onMobSpawnFinalize(event);

        if (event.getLevel() instanceof ServerLevel level) {
            BorderModule.onMobSpawnFinalize(event.getEntity(), level);
        }
    }

}
