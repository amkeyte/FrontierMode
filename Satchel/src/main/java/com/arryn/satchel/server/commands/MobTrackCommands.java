package com.arryn.satchel.server.commands;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.tracking.SatchelHealth;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The in-game surface for the health mob jig's interest API (RM_SAT_021, then SAT_125), so the
 * verification checklist can be exercised from chat without code access or a debugger attached to
 * the running server: {@code watch} is {@code interest(uuid)}, {@code unwatch} is {@code
 * lostInterest(uuid)}, {@code getfor} is {@code scopeFor(uuid)}, and {@code list} prints the jig's
 * entries with the count against the cap. Mirrors {@code BorderCommands}/{@code
 * BorderCommandHandler}'s Brigadier registration idiom.
 *
 * <p>
 * {@code watch}/{@code getfor} target "the nearest {@link Mob} to the command source" rather than
 * taking a UUID argument -- deliberately: it keeps targeting simple (stand next to the mob, run
 * the command) and avoids introducing a new Brigadier argument type. {@code unwatch} does NOT
 * re-run that same nearest-mob search -- see {@link #unwatch} for why.
 */
public final class MobTrackCommands {

    private MobTrackCommands() {
    }

    // Small on purpose: this is "the mob you're standing next to," not "some mob somewhere
    // nearby I can't see." First build of this command used 32 blocks and, on real-server
    // testing (SAT_035 log, 2026-08-21/22), that let `watch` silently latch onto a skeleton the
    // player couldn't see or locate -- out of sight is not a usable target for a hands-on
    // verification tool. 6 blocks keeps the target essentially "the thing right in front of you."
    private static final double SEARCH_RADIUS = 6.0;

    /**
     * The mob each player last targeted with {@code watch}, so {@code unwatch} can release
     * exactly that mob instead of re-running the nearest-mob search (see {@link #unwatch}).
     * Keyed by the command source's entity UUID (the player), not the mob -- one outstanding
     * "last watched" per player, matching how a single admin exercises this tool one target at a
     * time. Cleared on a successful {@code unwatch} of that entry; overwritten by the next
     * {@code watch}.
     */
    private static final Map<UUID, LastWatched> LAST_WATCHED = new ConcurrentHashMap<>();

    private record LastWatched(UUID mobUuid, String label) {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("satchel")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("mobtrack")
                                .then(Commands.literal("watch")
                                        .executes(MobTrackCommands::watch))
                                .then(Commands.literal("unwatch")
                                        .executes(MobTrackCommands::unwatch))
                                .then(Commands.literal("getfor")
                                        .executes(MobTrackCommands::getFor))
                                .then(Commands.literal("list")
                                        .executes(MobTrackCommands::list))
                        )
        );
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    // ─────────────────────────────────────────────
    // Subcommands
    // ─────────────────────────────────────────────

    private static int watch(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Mob mob = findNearestMob(source, level);
        MobJig jig = healthJig(source);
        if (jig == null) return 0;

        // interest() builds the scope in this call, because the mob is in the level.
        Optional<MobScope> scope = jig.interest(mob.getUUID());

        Entity caller = source.getEntity();
        if (caller != null) {
            LAST_WATCHED.put(caller.getUUID(), new LastWatched(mob.getUUID(), mob.getType().toString()));
        }

        source.sendSuccess(
                () -> msg("Interested in " + mob.getType() + " " + mob.getUUID()
                        + " in " + level.dimension().location()
                        + (scope.isPresent()
                        ? " -- its scope was built in the call: " + scope.get().debugName()
                        : " -- no scope (the mob is not in the level)")
                        + ". " + jig.interestCount() + " entr(ies) held."),
                false
        );
        return 1;
    }

    /**
     * Ends the interest in the mob the caller last {@code watch}ed -- NOT a fresh nearest-mob search.
     *
     * <p>
     * A first build of this command re-ran the same nearest-mob search {@code watch} uses. On
     * real-server testing (SAT_035 log, 2026-08-21/22) that produced a silent failure: the
     * player had walked around between calls, so the nearest mob at {@code unwatch} time was a
     * wolf that was never watched at all -- {@code unwatch} happily reported success while the
     * actually-watched skeleton stayed registered, with no error and no indication anything was
     * wrong. Nearest-mob targeting is only stable at the single instant {@code watch} runs; it is
     * not a reliable way to refer back to that same mob later. Tracking the caller's last-watched
     * target in {@link #LAST_WATCHED} and releasing that instead makes {@code unwatch} always end
     * the interest {@code watch} actually added, regardless of what else is nearby now.
     *
     * <p>
     * {@code lostInterest} ends the interest only: a live scope stays until its mob leaves.
     */
    private static int unwatch(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        MobJig jig = healthJig(source);
        if (jig == null) return 0;

        Entity caller = source.getEntity();
        LastWatched last = caller == null ? null : LAST_WATCHED.get(caller.getUUID());

        if (last != null) {
            jig.lostInterest(last.mobUuid());
            LAST_WATCHED.remove(caller.getUUID());
            source.sendSuccess(
                    () -> msg("No longer interested in " + last.label() + " " + last.mobUuid()
                            + " -- takes effect at once; a live scope stays until the mob leaves."),
                    false
            );
            return 1;
        }

        // No recorded watch for this caller (e.g. `unwatch` before any `watch`, or after the
        // JVM/server restarted and LAST_WATCHED reset) -- fall back to the nearest-mob search,
        // clearly labelled as a fallback rather than silently guessing.
        ServerLevel level = source.getLevel();
        Mob mob = findNearestMob(source, level);
        jig.lostInterest(mob.getUUID());
        source.sendSuccess(
                () -> msg("No recorded watch target for you -- falling back to nearest mob."
                        + " No longer interested in " + mob.getType() + " " + mob.getUUID() + "."),
                false
        );
        return 1;
    }

    private static int getFor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Mob mob = findNearestMob(source, level);
        MobJig jig = healthJig(source);
        if (jig == null) return 0;

        // Exercises scopeFor()'s contract directly: keyed by UUID, gated by interest, immediate.
        Optional<MobScope> scope = jig.scopeFor(mob.getUUID());

        if (scope.isPresent()) {
            source.sendSuccess(
                    () -> msg("scopeFor() returned the scope: " + scope.get().debugName()),
                    false
            );
        } else {
            source.sendFailure(
                    msg("scopeFor() returned empty -- Satchel isn't ready on this side yet, the mob is not on"
                            + " the list (`watch` it first; scopeFor never registers interest), or it is"
                            + " already removed.")
            );
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MobJig jig = healthJig(source);
        if (jig == null) return 0;

        List<UUID> entries = jig.interestedUuids();
        int cap = jig.policies().maxInterest();

        // This lists INTEREST, not live scope state: only lostInterest/drop/reset removes an entry. A
        // mob whose scope already unloaded because its mob left (a chunk unload, a kill) still shows
        // up here until it is unwatched -- interest and "currently has a live MobScope" are two
        // different things. The [SatchelHealth] and [MobJig] log lines show the latter.
        for (UUID uuid : entries) {
            boolean scoped = Satchel.require().askJigInfo(SatchelHealth.mobJigKey())
                    .map(ji -> ji.hasScope(uuid)).orElse(false);
            source.sendSuccess(() -> msg(uuid + (scoped ? "  (scope live)" : "  (no scope)")), false);
        }
        source.sendSuccess(() -> msg(entries.size() + " of " + cap + " interest entr(ies) on "
                + SatchelHealth.mobJigKey().name + "."), false);
        return entries.size();
    }

    // ─────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────

    /**
     * The server's health mob jig, or null (after telling the caller why) if Satchel isn't ready.
     */
    private static MobJig healthJig(CommandSourceStack source) {
        if (!Satchel.isReady()) {
            source.sendFailure(msg("Satchel is not ready on this side."));
            return null;
        }
        return Satchel.mobJig(SatchelHealth.mobJigKey());
    }

    private static Component msg(String m) {
        return Component.literal("[MobTrack] " + m);
    }

    private static Mob findNearestMob(
            CommandSourceStack source,
            ServerLevel level
    ) throws CommandSyntaxException {
        Vec3 pos = source.getPosition();
        AABB box = new AABB(
                pos.x - SEARCH_RADIUS, pos.y - SEARCH_RADIUS, pos.z - SEARCH_RADIUS,
                pos.x + SEARCH_RADIUS, pos.y + SEARCH_RADIUS, pos.z + SEARCH_RADIUS
        );

        List<Mob> found = level.getEntitiesOfClass(Mob.class, box, Mob::isAlive);

        if (found.isEmpty()) {
            throw new SimpleCommandExceptionType(
                    msg("No living mob within " + (int) SEARCH_RADIUS + " blocks.")
            ).create();
        }

        return found.stream()
                .min(Comparator.comparingDouble(m -> m.position().distanceToSqr(pos)))
                .orElseThrow();
    }
}
