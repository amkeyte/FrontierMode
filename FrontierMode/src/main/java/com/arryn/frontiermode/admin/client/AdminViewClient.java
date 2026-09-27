package com.arryn.frontiermode.admin.client;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.border.client.render.level.WorldBordersRenderer;
import com.arryn.frontiermode.boss.BossMarkers;
import com.arryn.frontiermode.boss.GuardianMarkers;
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
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The debug view: one toggle, like F3, that shows everything a playtester wants to see -- a glow
 * outline on bosses, guardians and Ferals, the name tag on bosses and Ferals, and the border rings.
 * Entirely client-side and per player: nothing is sent, the server never sets the flags, and normal
 * play always shows none of it. {@code /admin debug view <true|false>}, default off, not persisted.
 *
 * <p>The client already knows which mobs to mark: bosses and guardians through the synced
 * {@link BossMarkers}/{@link GuardianMarkers}, Ferals through their {@code feral} team (scoreboard
 * teams are synced, and the team colours the outline: purple guardians, green Ferals). While on, this
 * sets the glow flag and name-tag visibility on each matching entity's local copy every tick -- the
 * server never resends those values unless they change, and re-applying each tick covers the times it
 * resends the whole flags byte. Turning it off puts back what it touched.
 *
 * <p>Registered on the client command dispatcher, the same shape {@code /border debug rings} used:
 * Forge's client and server command graphs are separate, so this runs locally regardless of the
 * server's own {@code /admin} tree. Client-only ({@code Dist.CLIENT}).
 */
@Mod.EventBusSubscriber(modid = FrontierMode.MODID, value = Dist.CLIENT)
public final class AdminViewClient {

    private static boolean enabled;

    // Entities this view has marked, weakly held, so turning it off can put them back.
    private static final Set<Entity> TOUCHED = Collections.newSetFromMap(new WeakHashMap<>());

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
                entity.setGlowingTag(false);
                entity.setCustomNameVisible(false);
            }
            TOUCHED.clear();
        }
    }

    /** Called every client tick from {@code AdminViewHandlers}; a no-op while the view is off. */
    public static void tick(Level level) {
        if (!enabled || !(level instanceof ClientLevel clientLevel)) {
            return;
        }
        for (Entity entity : clientLevel.entitiesForRendering()) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            boolean guardian = GuardianMarkers.isGuardian(mob);
            // A guardian carries no name tag; a boss or a Feral does.
            boolean tagged = BossMarkers.isLiveBoss(mob) || isFeral(mob);
            if (!guardian && !tagged) {
                continue;
            }
            mob.setGlowingTag(true);
            if (tagged) {
                mob.setCustomNameVisible(true);
            }
            TOUCHED.add(mob);
        }
    }

    private static boolean isFeral(Mob mob) {
        Team team = mob.getTeam();
        return team != null && "feral".equals(team.getName());
    }
}
