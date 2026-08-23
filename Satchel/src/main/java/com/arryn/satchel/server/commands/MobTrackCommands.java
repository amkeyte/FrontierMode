package com.arryn.satchel.server.commands;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RM_SAT_021 ("Frank") verification aid -- the in-game surface for
 * {@link SatchelHealth#watch}/{@link SatchelHealth#unwatch} and
 * {@link MobScope#getFor}, so the done bar's checklist can be exercised from chat without code
 * access or a debugger attached to the running server. Mirrors
 * {@code BorderCommands}/{@code BorderCommandHandler}'s Brigadier registration idiom (own class
 * doc, same package shape) -- the only pattern for touching
 * {@code MinecraftForge.EVENT_BUS}'s {@code RegisterCommandsEvent} already proven out in this
 * codebase.
 *
 * <p>
 * As of SAT_039, {@code watch}/{@code unwatch}/{@code currentInterests} live on
 * {@link SatchelHealth} (relocated from the old {@code MobTrackingModule}, same IDs and
 * behavior) -- this class's own commands are unchanged, only the class they call into moved.
 *
 * <p>
 * {@code watch}/{@code getfor} target "the nearest {@link Mob} to the command source" rather than
 * taking a UUID argument -- deliberately: it keeps targeting simple (stand next to the mob, run
 * the command) and avoids introducing a new Brigadier argument type this sandbox has no way to
 * compile-check against the real Forge/Mojang mappings jar. {@code unwatch} does NOT re-run that
 * same nearest-mob search -- see {@link #unwatch} for why a first build of this class did, and
 * why that was wrong.
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

    private record LastWatched(ServerLevel level, UUID mobUuid, String label) {
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

        SatchelHealth.watch(level, mob.getUUID());

        Entity caller = source.getEntity();
        if (caller != null) {
            LAST_WATCHED.put(
                    caller.getUUID(),
                    new LastWatched(level, mob.getUUID(), mob.getType().toString())
            );
        }

        source.sendSuccess(
                () -> msg("Watching " + mob.getType() + " " + mob.getUUID()
                        + " in " + level.dimension().location()
                        + " -- expect a [SatchelHealth] LOADED line within one poll cycle (up to 1s)."),
                false
        );
        return 1;
    }

    /**
     * Releases the mob the caller last {@code watch}ed -- NOT a fresh nearest-mob search.
     *
     * <p>
     * A first build of this command re-ran the same nearest-mob search {@code watch} uses. On
     * real-server testing (SAT_035 log, 2026-08-21/22) that produced a silent failure: the
     * player had walked around between calls, so the nearest mob at {@code unwatch} time was a
     * wolf that was never watched at all -- {@code unwatch} happily reported success ("Unwatched
     * ... wolf ...") while the actually-watched skeleton stayed registered, with no error and no
     * indication anything was wrong. Nearest-mob targeting is only stable at the single instant
     * {@code watch} runs; it is not a reliable way to refer back to that same mob later. Tracking
     * the caller's last-watched target in {@link #LAST_WATCHED} and releasing that instead makes
     * {@code unwatch} always release the mob {@code watch} actually registered, regardless of
     * what else is nearby now.
     */
    private static int unwatch(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Entity caller = source.getEntity();
        LastWatched last = caller == null ? null : LAST_WATCHED.get(caller.getUUID());

        if (last != null) {
            SatchelHealth.unwatch(last.level(), last.mobUuid());
            LAST_WATCHED.remove(caller.getUUID());
            source.sendSuccess(
                    () -> msg("Unwatched " + last.label() + " " + last.mobUuid()
                            + " -- the existing scope (if any) tears down on the next poll cycle,"
                            + " not instantly; expect a [SatchelHealth] UNLOADED line within ~1s."),
                    false
            );
            return 1;
        }

        // No recorded watch for this caller (e.g. `unwatch` before any `watch`, or after the
        // JVM/server restarted and LAST_WATCHED reset) -- fall back to the nearest-mob search,
        // clearly labelled as a fallback rather than silently guessing.
        ServerLevel level = source.getLevel();
        Mob mob = findNearestMob(source, level);
        SatchelHealth.unwatch(level, mob.getUUID());
        source.sendSuccess(
                () -> msg("No recorded watch target for you -- falling back to nearest mob."
                        + " Unwatched " + mob.getType() + " " + mob.getUUID()
                        + " -- the existing scope (if any) tears down on the next poll cycle,"
                        + " not instantly; expect a [SatchelHealth] UNLOADED line within ~1s."),
                false
        );
        return 1;
    }

    private static int getFor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Mob mob = findNearestMob(source, level);

        // Bypasses the poll entirely -- exercises MobScope.getFor()'s immediate-attachment
        // guarantee directly, per mobscope-getfor.md.
        Optional<MobScope> scope = MobScope.getFor(mob);

        if (scope.isPresent()) {
            source.sendSuccess(
                    () -> msg("getFor() attached immediately: " + scope.get().debugName()),
                    false
            );
        } else {
            source.sendFailure(
                    msg("getFor() returned empty -- either Satchel isn't ready on this side yet,"
                            + " or the targeted mob is already removed.")
            );
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        Map<ServerLevel, Set<UUID>> interests = SatchelHealth.currentInterests();

        if (interests.isEmpty() || interests.values().stream().allMatch(Set::isEmpty)) {
            source.sendSuccess(() -> msg("No mobs currently watched."), false);
            return 0;
        }

        // This lists registered INTEREST, not live scope state: unwatching is the only thing
        // that removes an entry here. A mob whose scope already tore down via chunk unload (see
        // MobJig's reason-agnostic teardown) still shows up in this list until it's explicitly
        // unwatched -- interest and "currently has a live MobScope" are two different things.
        // Check [SatchelHealth] LOADED/UNLOADED lines in the server log for the latter.
        int total = 0;
        for (Map.Entry<ServerLevel, Set<UUID>> entry : interests.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            total += entry.getValue().size();
            String dim = entry.getKey().dimension().location().toString();
            for (UUID uuid : entry.getValue()) {
                source.sendSuccess(() -> msg(dim + " -- " + uuid), false);
            }
        }
        int finalTotal = total;
        source.sendSuccess(() -> msg(finalTotal + " mob(s) watched (interest registered;"
                + " does not mean each currently has a live scope -- see server log)."), false);
        return total;
    }

    // ─────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────

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
