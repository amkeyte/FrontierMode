package com.arryn.frontiermode.border.common.player;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Pure value object representing derived player-border evaluation results.
 *
 * This object:
 * - carries no authority
 * - has no identity beyond its values
 * - is not persisted or synced
 * - is produced only by BorderPlayerLogic
 *
 * {@code relevantLayer} and {@code nearestLayer} were split from a single overloaded
 * {@code layer} field (Border Vocabulary Conformance Checklist item 2.4): {@code
 * relevantLayer} is populated only when {@code insideNearest} is true (a real Relevance
 * resolution, per Border Vocabulary); {@code nearestLayer} is always populated and is the
 * nearest-surface fallback border's layer, which is *not* a Relevance result and must not be
 * treated as one.
 *
 * <p>RM_FRO_037 ("Brenda," Frontier Sickness epoch 1): {@code frontierDistance} and
 * {@code sicknessSeverity} are a second, independent reduction over the same {@code borders}
 * list this eval already walks -- see
 * wiki/frontiermode/architecture/exterior.md#where-this-lives for why this is a second
 * reduction, not a reuse of {@code nearestBorderId}/{@code distanceToNearest} above (those are
 * resolved only among Borders the point is relevant to; Frontier-distance considers every
 * established Border regardless). {@code frontierDistance} is {@code 0} when the point isn't in
 * the Exterior at all (inside or on at least one Border). {@code sicknessSeverity} is a
 * target-and-catch-up climb toward a {@code frontierDistance}-derived target -- see
 * {@code FrontierSicknessLogic} -- carried forward from the previous tick's snapshot, not
 * re-derived from {@code frontierDistance} alone each time.
 */
record BorderPlayerEval(
        UUID nearestBorderId,
        int distanceToNearest,
        boolean insideNearest,
        OptionalInt relevantLayer,
        int nearestLayer,
        int frontierDistance,
        double sicknessSeverity
) {}
