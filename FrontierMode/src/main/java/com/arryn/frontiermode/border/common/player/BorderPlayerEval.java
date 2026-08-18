package com.arryn.frontiermode.border.common.player;

import java.util.UUID;

/**
 * Pure value object representing derived player-border evaluation results.
 *
 * This object:
 * - carries no authority
 * - has no identity beyond its values
 * - is not persisted or synced
 * - is produced only by BorderPlayerLogic
 */
record BorderPlayerEval(
        UUID nearestBorderId,
        int distanceToNearest,
        boolean insideNearest,
        int layerIndex
) {}
