package com.arryn.frontiermode.border.server.rules;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderAuthority;
import com.arryn.frontiermode.border.common.fixture.BordersFixture;
import com.arryn.frontiermode.border.common.BorderMath;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.level.LevelScope;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.LogicalSide;

import java.util.*;

/**
 * Stateless logic engine for level-level border queries and proposal generation.
 * <p>
 * Responsibilities:
 * - Enforce border authority ownership
 * - Provide border topology queries
 * - Generate level mutation proposals (initial / growth)
 *
 */
public final class BorderLogic {

    private final BordersFixture setting;
    private final BorderRules rules;


    public BorderLogic(BordersFixture setting, BorderRules rules) {
        this.setting = Objects.requireNonNull(setting, "setting");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    // ============================================================
    // Ownership enforcement
    // ============================================================

    private void requireAuthority(BorderAuthority authority) {
        Objects.requireNonNull(authority, "authority");
    }

    private void ensureOwned(Border border) {
        if (border == null) return;
        if (border.authority() != setting) {
            throw new IllegalArgumentException("Foreign border: " + border.id());
        }
    }

    private void ensureOwned(Collection<Border> borders) {
        if (borders == null) return;
        for (Border b : borders) ensureOwned(b);
    }

    // ============================================================
    // Queries
    // ============================================================


    public List<Border> containing(

            List<Border> borders, BlockPos pos) {
        ensureOwned(borders);

        if (pos == null || borders == null || borders.isEmpty()) {
            return List.of();
        }

        List<Border> result = new ArrayList<>();
        for (Border b : borders) {
            if (BorderMath.isInside(b, pos)) {
                result.add(b);
            }
        }

        return result;
    }

    Optional<Border> nearest(List<Border> borders, BlockPos pos) {
        ensureOwned(borders);

        if (pos == null || borders == null || borders.isEmpty()) {
            return Optional.empty();
        }

        Border best = null;
        int bestDist = Integer.MAX_VALUE;

        for (Border b : borders) {
            int dist = BorderMath.distanceToSurface(b, pos);
            if (dist < bestDist) {
                bestDist = dist;
                best = b;
            }
        }

        return Optional.ofNullable(best);
    }


    // ============================================================
    // Rules-driven proposals (level mutation)
    // ============================================================

    public Border getInitial() {

        ServerLevel level = resolveLevel();
        var prop = setting.CRUD.getProposal();

        prop.center(rules.chooseInitialCenter(level)).radius(rules.chooseInitialRadius(level)).layerIndex(0);
        setting.CRUD.validateProposal(prop);
        return setting.CRUD.applyProposal(prop);
    }

    public Border grow(Border previous) {
        Objects.requireNonNull(previous, "previous");

        ServerLevel level = resolveLevel();
        var prop = setting.CRUD.getProposal();

        prop.center(rules.chooseNextCenter(level, previous)).radius(rules.chooseNextRadius(level, previous)).layerIndex(previous.layerIndex() + 1);

        setting.CRUD.validateProposal(prop);
        return setting.CRUD.applyProposal(prop);
    }

    // ============================================================
    // Defaults (for new proposal systems)
    // ============================================================

    /**
     * Default radius for a newly proposed border.
     * Used by mutable proposal facets.
     */
    public int defaultRadius() {
        return rules.chooseInitialRadius(resolveLevel());
    }

    private ServerLevel resolveLevel() {
        if(!(setting.scope() instanceof LevelScope levelScope)){
            throw new IllegalStateException("Non level scope.");
        }

        if(!(levelScope.level() instanceof ServerLevel serverLevel)){
            throw new IllegalStateException("Non server level");
        }

        return serverLevel;
    }


    public String getDefaultDisplayName() {
        List<String> names = new ArrayList<>(rules.borderNames());
        if (names.isEmpty()) {
            return "unknown";
        }

        // Shuffle so defaults don't feel deterministic or boring
        Collections.shuffle(names);

        // Collect already-used names
        Set<String> used = new HashSet<>();
        for (Border b : setting.CRUD.all()) {
            used.add(b.displayName());
        }

        // Pick the first unused name
        for (String name : names) {
            if (!used.contains(name)) {
                return name;
            }
        }

        // Exhausted the pool
        return "unknown";
    }

    public BlockPos defaultCenter() {
        return rules.chooseInitialCenter(resolveLevel());
    }
    private void requireServerSide() {
        if(Satchel.require().side() == LogicalSide.CLIENT)
            throw new IllegalStateException();
    }
}

