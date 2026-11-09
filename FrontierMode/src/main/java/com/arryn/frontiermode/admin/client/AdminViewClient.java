package com.arryn.frontiermode.admin.client;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.admin.common.DebugViewTick;
import com.arryn.frontiermode.admin.common.Role;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.boss.BossMarkers;
import com.arryn.frontiermode.sentinel.SentinelMarkers;
import com.arryn.satchel.common.platform.PlatformBound;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The debug view: one toggle, like F3, that shows everything a playtester wants to see -- a coloured
 * marker above bosses, sentinels and Ferals ({@link AdminViewMarkers}), the name tag on bosses, and
 * the border rings.
 * Entirely client-side and per player: nothing is sent, the server never sets the flags, and normal
 * play always shows none of it. {@code /admin debug view <true|false>}, default off, not persisted.
 *
 * <p>The client already knows which mobs to mark: bosses and sentinels through the synced
 * {@link BossMarkers}/{@link SentinelMarkers}, Ferals by position ({@link #isFeral}). While
 * on, each tick {@link #mark} rebuilds the set of marked mobs and their roles (which
 * {@link AdminViewMarkers} draws) and sets name-tag visibility on each boss's local copy -- re-applied
 * each tick in case the server resends it. The tick and all of that state belong to {@code DebugViewTick}, a
 * Trinket on the client player host, which puts back what it touched when the view goes off. That includes the
 * on/off flag, so the view ends with the player's scope: a quit, a new world and a join to a server each start
 * with it off, and a change of dimension keeps it. This class keeps no state.
 *
 * <p>No glow: vanilla's outline draws only from a server-synced entity flag that client code can't
 * set ({@code Entity#setGlowingTag} on the client writes the flag's own current value back), so the
 * marker replaces it. See FRO_202.
 *
 * <p>Registered on the client command dispatcher:
 * Forge's client and server command graphs are separate, so this runs locally regardless of the
 * server's own {@code /admin} tree. Client-only ({@code Dist.CLIENT}).
 */
@PlatformBound(reason = "commands")
@Mod.EventBusSubscriber(modid = FrontierMode.MODID, value = Dist.CLIENT)
public final class AdminViewClient {

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
                                                    Player player = Minecraft.getInstance().player;
                                                    Optional<DebugViewTick> view = player == null
                                                            ? Optional.empty() : DebugViewTick.of(player);
                                                    if (view.isEmpty()) {
                                                        ctx.getSource().sendFailure(Component.literal(
                                                                "[Admin][Debug] The view is not ready yet: the"
                                                                        + " player's scope is not up. Try again in a moment."));
                                                        return 0;
                                                    }
                                                    view.get().set(value);
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

    /**
     * Whether the view is on for the local player: what the border rings follow, through the supplier
     * {@code AdminViewStartup} registers once at startup. False while Satchel or the player's scope isn't ready.
     */
    static boolean viewOn() {
        Player player = Minecraft.getInstance().player;
        return player != null && DebugViewTick.of(player).map(DebugViewTick::isOn).orElse(false);
    }

    /** The mobs to mark this frame and their roles; empty while the view is off or the player's scope isn't ready. */
    static Map<Mob, Role> marked() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return Map.of();
        }
        return DebugViewTick.of(player).map(DebugViewTick::marked).orElse(Map.of());
    }

    /**
     * Rebuilds {@code marked} for this tick and records in {@code touched} each entity it set a name tag
     * visible on. Called every client tick by {@code DebugViewTick} while the view is on.
     */
    public static void mark(Level level, Map<Mob, Role> marked, Set<Entity> touched) {
        if (!(level instanceof ClientLevel clientLevel)) {
            return;
        }
        marked.clear();
        for (Entity entity : clientLevel.entitiesForRendering()) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            Role role = roleOf(mob);
            if (role == null) {
                continue;
            }
            marked.put(mob, role);
            if (role.tagged()) {
                mob.setCustomNameVisible(true);
                touched.add(mob);
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

    /**
     * Feral by position: the client holds no Feral record (MobData is server-only). Every real hostile past the
     * Frontier is Feral, so a {@code MONSTER}-category mob other than a Slime, standing where
     * {@code BorderAPI.frontierDistanceAt} is positive (the client computes it from the Borders fixture it
     * already holds for the rings), is marked. Exact except at the edges; the Inspect Stick shows the record.
     */
    private static boolean isFeral(Mob mob) {
        return mob.getType().getCategory() == MobCategory.MONSTER
                && !(mob instanceof Slime)
                && BorderAPI.frontierDistanceAt(mob.level(), mob.blockPosition()) > 0;
    }
}
