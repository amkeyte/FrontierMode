package com.arryn.frontiermode.border.common.player;

import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.server.rules.BorderRules;
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

    public BorderPlayerEval evaluate(
            List<Border> borders,
            BlockPos pos
    ) {
        if (pos == null || borders == null || borders.isEmpty()) {
            return new BorderPlayerEval(
                    null,
                    Integer.MAX_VALUE,
                    false,
                    OptionalInt.empty(),
                    -1
            );
        }

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
                .filter(b -> BorderMath.isInside(b, pos))
                .toList();

        Border relevant = BorderRules.ACTIVE.getRelevant(containing, pos);

        if (relevant != null) {
            // The Relevant border is also the nearest border in this branch (it's a member of
            // `containing`, and Relevance is resolved from the same set `nearest` would search),
            // so nearestLayer can just mirror relevantLayer here rather than re-deriving it.
            return new BorderPlayerEval(
                    relevant.id(),
                    BorderMath.distanceToSurface(relevant, pos),
                    true,
                    OptionalInt.of(relevant.layer()),
                    relevant.layer()
            );
        }

        // Not inside anything -- fall back to whichever border's surface is physically closest.
        // BorderRules.getRelevant only ranks borders already known to contain the position, so it
        // has nothing to say about this case; "closest surface" is the sensible fallback for a
        // player outside every border (e.g. for a compass/UI hint pointing them toward one).
        Border nearest = borders.stream()
                .min(Comparator.comparingInt(
                        b -> BorderMath.distanceToSurface(b, pos)
                ))
                .orElseThrow();

        // Outside every border -- Border Vocabulary doesn't define Relevance for this case at
        // all, so relevantLayer stays empty; nearestLayer is the fallback's layer, explicitly not
        // a Relevance result.
        return new BorderPlayerEval(
                nearest.id(),
                BorderMath.distanceToSurface(nearest, pos),
                false,
                OptionalInt.empty(),
                nearest.layer()
        );
    }
}
