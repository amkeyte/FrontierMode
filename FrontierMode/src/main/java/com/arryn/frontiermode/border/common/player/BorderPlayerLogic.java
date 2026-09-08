package com.arryn.frontiermode.border.common.player;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.FrontierSicknessLogic;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.server.rules.BorderRules;
import com.arryn.frontiermode.border.server.rules.PlayerRules;
import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

/**
 * Stateless evaluator producing derived player-border information.
 *
 * This class:
 * - performs no mutation
 * - enforces no authority
 * - produces no authoritative objects
 * - returns pure evaluation results only
 */
final class BorderPlayerLogic {

    /**
     * RM_FRO_037 ("Brenda," Frontier Sickness epoch 1): {@code previous} is this player's last
     * accepted {@link BorderPlayerStatus} (or {@code null} on the very first evaluation, e.g.
     * just after login) -- needed only for {@code sicknessSeverity}'s target-and-catch-up climb,
     * which is stateful across ticks unlike every other field this method derives. This keeps
     * the method itself a pure function of its inputs (no hidden fields on this class), with
     * {@link BorderPlayerStatusFixture} remaining the one place that actually holds continuity
     * between ticks, per that fixture's own "not persisted, live-recomputed" contract.
     */
    public BorderPlayerEval evaluate(
            List<Border> borders,
            BlockPos pos,
            BorderPlayerStatus previous
    ) {
        double previousSeverity = previous != null ? previous.sicknessSeverity() : 0.0;

        if (pos == null || borders == null || borders.isEmpty()) {
            // No border data to reduce over -- Frontier-distance defensively reads as "not in the
            // Exterior" rather than undefined, same "standby, don't crash" discipline as every
            // other early-return branch in this class. Severity still decays toward 0 from
            // whatever it previously was, exactly as it would if frontierDistance had genuinely
            // dropped to 0 -- this isn't a special case for severity, just this branch also being
            // a (degenerate) frontierDistance=0 tick.
            double decayed = FrontierSicknessLogic.climbSeverity(previousSeverity, 0.0, PlayerRules.SICKNESS_CLIMB_RATE);
            return new BorderPlayerEval(
                    null,
                    Integer.MAX_VALUE,
                    false,
                    OptionalInt.empty(),
                    -1,
                    0,
                    decayed
            );
        }

        // RM_FRO_037: Frontier-distance -- a second reduction over this same `borders` list,
        // independent of the containing/relevant resolution below. Every established Border
        // counts here, not just ones the point is inside or on-path -- see
        // wiki/frontiermode/architecture/exterior.md#frontier-becomes-a-named-computed-aggregate.
        // A point inside (or on) at least one Border always reduces to 0 here, since
        // BorderAPI.MATH.distanceOutside floors at 0 for that Border -- no separate inside-check
        // needed before running this.
        int frontierDistance = borders.stream()
                .mapToInt(b -> BorderAPI.MATH.distanceOutside(pos, b.center(), b.radius()))
                .min()
                .orElse(0);

        double target = FrontierSicknessLogic.severityTarget(frontierDistance, PlayerRules.SICKNESS_TARGET_SCALE);
        double sicknessSeverity = FrontierSicknessLogic.climbSeverity(previousSeverity, target, PlayerRules.SICKNESS_CLIMB_RATE);

        // Borders are concentric per layer (DefaultBorderRules' own class doc), so a player deep
        // inside a fully-grown border stack is inside every layer at once. The original version
        // of this method picked whichever border's raw distanceToSurface was smallest (most
        // negative) -- since a bigger radius makes that number MORE negative, that always picked
        // the OUTERMOST containing border, backwards from what "relevant" means here (confirmed
        // wrong by real playtest: returned the wrong border, not the lowest layer).
        //
        // BorderRules.getRelevant(containing, pos) already defines the correct, established
        // semantics for this exact question -- lowest layer wins among borders the position
        // is actually inside, ties broken by nearest center (see DefaultBorderRules.getRelevant,
        // already the single source of truth other facets reference for this ranking, e.g.
        // BordersCrudFacet/BordersPathFacet's own comments). Delegating to it here instead of
        // duplicating the ranking keeps this in sync with that one authoritative implementation
        // rather than drifting from it again.
        List<Border> containing = borders.stream()
                .filter(b -> BorderAPI.MATH.isInside(b, pos))
                .toList();

        Border relevant = BorderRules.ACTIVE.getRelevant(containing, pos);

        if (relevant != null) {
            // The Relevant border is also the nearest border in this branch (it's a member of
            // `containing`, and Relevance is resolved from the same set `nearest` would search),
            // so nearestLayer can just mirror relevantLayer here rather than re-deriving it.
            return new BorderPlayerEval(
                    relevant.id(),
                    BorderAPI.MATH.distanceToSurface(relevant, pos),
                    true,
                    OptionalInt.of(relevant.layer()),
                    relevant.layer(),
                    frontierDistance,
                    sicknessSeverity
            );
        }

        // Not inside anything -- fall back to whichever border's surface is physically closest.
        // BorderRules.getRelevant only ranks borders already known to contain the position, so it
        // has nothing to say about this case; "closest surface" is the sensible fallback for a
        // player outside every border (e.g. for a compass/UI hint pointing them toward one).
        Border nearest = borders.stream()
                .min(Comparator.comparingInt(
                        b -> BorderAPI.MATH.distanceToSurface(b, pos)
                ))
                .orElseThrow();

        // Outside every border -- Border Vocabulary doesn't define Relevance for this case at
        // all, so relevantLayer stays empty; nearestLayer is the fallback's layer, explicitly not
        // a Relevance result.
        return new BorderPlayerEval(
                nearest.id(),
                BorderAPI.MATH.distanceToSurface(nearest, pos),
                false,
                OptionalInt.empty(),
                nearest.layer(),
                frontierDistance,
                sicknessSeverity
        );
    }
}
