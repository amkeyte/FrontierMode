package com.arryn.frontiermode.admin.client;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.border.client.render.level.WorldBordersRenderer;
import com.arryn.frontiermode.boss.BossMarkers;
import com.arryn.frontiermode.boss.SentinelMarkers;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Team;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The debug view: one toggle, like F3, that shows everything a playtester wants to see -- a coloured
 * marker above bosses, sentinels and Ferals ({@link AdminViewMarkers}), the name tag on bosses and
 * Ferals, and the border rings.
 * Entirely client-side and per player: nothing is sent, the server never sets the flags, and normal
 * play always shows none of it. {@code /admin debug view <true|false>}, default off, not persisted.
 *
 * <p>The client already knows which mobs to mark: bosses and sentinels through the synced
 * {@link BossMarkers}/{@link SentinelMarkers}, Ferals through their synced {@code feral} team. While
 * on, each tick this rebuilds the set of marked mobs and their roles (which {@link AdminViewMarkers}
 * draws) and sets name-tag visibility on each boss's and Feral's local copy -- re-applied each tick in
 * case the server resends it. Turning it off puts back what it touched.
 *
 * <p>No glow: vanilla's outline draws only from a server-synced entity flag that client code can't
 * set ({@code Entity#setGlowingTag} on the client writes the flag's own current value back), so the
 * marker replaces it. See FRO_202.
 *
 * <p>Registered on the client command dispatcher, the same shape {@code /border debug rings} used:
 * Forge's client and server command graphs are separate, so this runs locally regardless of the
 * server's own {@code /admin} tree. Client-only ({@code Dist.CLIENT}).
 */
@Mod.EventBusSubscriber(modid = FrontierMode.MODID, value = Dist.CLIENT)
public final class AdminViewClient {

    private static boolean enabled;

    /** What a marked mob is there for, and its marker colour (ARGB, fully opaque). */
    public enum Role {
        BOSS(0xFFFFAA00, true),
        SENTINEL(0xFFFF55FF, false),
        FERAL(0xFF55FF55, true);

        private final int color;
        private final boolean tagged;

        Role(int color, boolean tagged) {
            this.color = color;
            this.tagged = tagged;
        }

        public int color() {
            return color;
        }

        /** Whether this role also shows its name tag in the view (a sentinel carries none). */
        public boolean tagged() {
            return tagged;
        }
    }

    // Entities this view has shown a name tag on, weakly held, so turning it off can put them back.
    private static final Set<Entity> TOUCHED = Collections.newSetFromMap(new WeakHashMap<>());

    // This tick's marked mobs and their roles, weakly held; rebuilt every tick while on.
    private static final Map<Mob, Role> MARKED = new WeakHashMap<>();

    private AdminViewClient() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("admin")
                        .then(Commands.literal("debug")
                                .then(Commands.literal("view")
                                        .then(Commands.argument("visible", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    boolean value = BoolArgumentType.getBool(ctx, "visible");
                                                    setEnabled(value);
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("[Admin][Debug] View: "
                                                                    + (value ? "ON" : "OFF")),
                                                            false);
                                                    return 1;
                                                })
                                        )
                                )
                        )
        );
    }

    private static void setEnabled(boolean value) {
        enabled = value;
        WorldBordersRenderer.setVisible(value);
        if (!value) {
            for (Entity entity : TOUCHED) {
                entity.setCustomNameVisible(false);
            }
            TOUCHED.clear();
            MARKED.clear();
        }
    }

    /** The mobs to mark this frame and their roles; empty while the view is off. */
    static Map<Mob, Role> marked() {
        return Collections.unmodifiableMap(MARKED);
    }

    /** Called every client tick from {@code AdminViewHandlers}; a no-op while the view is off. */
    public static void tick(Level level) {
        if (!enabled || !(level instanceof ClientLevel clientLevel)) {
            return;
        }
        MARKED.clear();
        for (Entity entity : clientLevel.entitiesForRendering()) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            Role role = roleOf(mob);
            if (role == null) {
                continue;
            }
            MARKED.put(mob, role);
            if (role.tagged()) {
                mob.setCustomNameVisible(true);
                TOUCHED.add(mob);
            }
        }
    }

    // A boss first, then a sentinel (a sentinel in the Wilds is Feral-tagged too), then a Feral.
    private static Role roleOf(Mob mob) {
        if (BossMarkers.isLiveBoss(mob)) {
            return Role.BOSS;
        }
        if (SentinelMarkers.isSentinel(mob)) {
            return Role.SENTINEL;
        }
        return isFeral(mob) ? Role.FERAL : null;
    }

    private static boolean isFeral(Mob mob) {
        Team team = mob.getTeam();
        return team != null && "feral".equals(team.getName());
    }
}
