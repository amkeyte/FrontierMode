package com.arryn.frontiermode;

import com.arryn.frontiermode.border.server.commands.BorderSelectorArgumentTypeInfo;
import com.arryn.frontiermode.boss.server.commands.BossSelectorArgumentTypeInfo;
import com.arryn.frontiermode.core.FrontierRegistrar;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Supplier;

/**
 * The mod's own registrations: the two command argument types and the sounds. Each is registered through
 * {@link FrontierRegistrar}, and each line's mod entry installs that first.
 */
@PlatformBound(reason = "registration")
public final class FrontierRegisters {

    private FrontierRegisters() {
    }

    /** Loads this class, which registers on load. {@code FrontierBoot} calls it once the registrar is installed. */
    static void load() {
    }

    public static final Supplier<ArgumentTypeInfo<?, ?>> BORDER_SELECTOR =
            FrontierRegistrar.argumentType("border_selector", BorderSelectorArgumentTypeInfo::new);

    // /boss's own selector argument type, same registration shape as BORDER_SELECTOR above.
    public static final Supplier<ArgumentTypeInfo<?, ?>> BOSS_SELECTOR =
            FrontierRegistrar.argumentType("boss_selector", BossSelectorArgumentTypeInfo::new);

    // Entry Cue's real sound.
    public static final Supplier<SoundEvent> ENTRY_CUE = FrontierRegistrar.sound("entry_cue");

    // Round-robin clips for BossTellFixture's ambient tell sound (see that class's own
    // TELL_SOUNDS doc), BOSS_TELL_1 through BOSS_TELL_5.
    public static final Supplier<SoundEvent> BOSS_TELL_1 = FrontierRegistrar.sound("boss_tell_1");
    public static final Supplier<SoundEvent> BOSS_TELL_2 = FrontierRegistrar.sound("boss_tell_2");
    public static final Supplier<SoundEvent> BOSS_TELL_3 = FrontierRegistrar.sound("boss_tell_3");
    public static final Supplier<SoundEvent> BOSS_TELL_4 = FrontierRegistrar.sound("boss_tell_4");
    public static final Supplier<SoundEvent> BOSS_TELL_5 = FrontierRegistrar.sound("boss_tell_5");

    // The Cairn pulse's ping.
    public static final Supplier<SoundEvent> CAIRN_PING = FrontierRegistrar.sound("cairn_ping");

    // The Edge Stone's poof where it crosses the Frontier line.
    public static final Supplier<SoundEvent> EDGE_STONE = FrontierRegistrar.sound("edge_stone");
}
