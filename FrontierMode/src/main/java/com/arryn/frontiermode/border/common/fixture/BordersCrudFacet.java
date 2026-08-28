package com.arryn.frontiermode.border.common.fixture;


import com.arryn.frontiermode.border.common.BorderConstants;
import com.arryn.frontiermode.border.server.rules.BorderRules;
import com.arryn.satchel.common.util.out.OUT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

///
/// provides the direct interface to interact with proposals.
public class BordersCrudFacet {
    private final BordersFixture fixture;

    BordersCrudFacet(BordersFixture fixture) {
        this.fixture = fixture;
    }

    //return a new proposal
    public BorderProposal getProposal() {
        return new BorderProposal(fixture);
    }

    /**
     * FRO_047: returns {@link Result} instead of throwing on rejection -- the real reason
     * ({@link #failureReason}) is carried through in the {@code Result}'s message rather than
     * only ever reaching {@code OUT.warn()}, same fix RM_FRO_011 originally made for the
     * player-facing message, now expressed as data instead of an exception. See the Border wiki
     * page's "Mutation surface" section for the full contract this follows.
     */
    public Result applyProposal(BorderProposal proposal) {
        fixture.requireServerSide();
        proposal.requireNotConsumed();

        Optional<String> failure = failureReason(proposal);
        if (failure.isEmpty()) {
            Border border = fixture.accept(proposal);
            return Result.success(border);
        }

        return Result.validationRejected(failure.get());
    }

    /**
     * Real validation logic. <b>Layer-collision rejection was removed, RM_FRO_015, 2026-08-20</b>
     * -- project owner's direct design call: Layer and Path are definitionally unrelated (see
     * Border Vocabulary's own "Layer" section, "only coincidentally tied to Path"), and
     * {@code DefaultBorderRules.getRelevant()} already fully resolves a same-layer overlap by
     * nearest center (see that method, and Border Vocabulary's Relevance section: "lowest Layer
     * wins, tie-broken by nearest center") -- a tie-break that was already shipped and already
     * correct *before* this uniqueness guard ever existed. The guard was solving a problem
     * {@code getRelevant()} didn't have: two borders sharing a layer, even two that geometrically
     * overlap, resolve deterministically without it. Global layer uniqueness was actively harmful
     * in practice: organic growth's hardcoded {@code layer = 0} / {@code previous.layer() + 1}
     * (now inlined in {@link BordersPathFacet#grow()}) would collide with any unrelated off-path
     * border already holding that value, blocking ordinary path growth for a reason that was never
     * a real correctness requirement (found via real playtest, RM_FRO_015).
     */
    private Optional<String> failureReason(BorderProposal proposal) {
        fixture.requireServerSide();

        int radius = proposal.radius();
        if (radius < BorderConstants.MIN_RADIUS || radius > BorderConstants.MAX_RADIUS) {
            String reason = "radius " + radius + " outside allowed range ["
                    + BorderConstants.MIN_RADIUS + ", " + BorderConstants.MAX_RADIUS + "].";
            OUT.warn("[Border] Rejected proposal: " + reason);
            return Optional.of(reason);
        }

        int layerIndex = proposal.layerIndex();
        if (layerIndex < 0) {
            String reason = "negative layer " + layerIndex + ".";
            OUT.warn("[Border] Rejected proposal: " + reason);
            return Optional.of(reason);
        }

        return Optional.empty();
    }

    public boolean remove(UUID uuid){
        fixture.requireServerSide();

        return fixture.remove(uuid);
    }

    public boolean remove(Border border){
        fixture.requireServerSide();

        return fixture.remove(border.id());
    }

    public Optional<Border> get(UUID uuid) {
        return fixture.get(uuid);
    }

    public List<Border> all() {
        return fixture.all();
    }

    /**
     * FRO_047: moved here from the deleted {@code BorderLogic.getDefaultDisplayName()} -- needs
     * {@link #all()} for name dedup, which {@code BorderRules} never touches (rules are stateless,
     * fixture-blind by design -- see {@code BorderRules}'s own interface doc).
     */
    public String getDefaultDisplayName() {
        List<String> names = new ArrayList<>(BorderRules.ACTIVE.borderNames());
        if (names.isEmpty()) {
            return "unknown";
        }

        // Shuffle so defaults don't feel deterministic or boring
        Collections.shuffle(names);

        // Collect already-used names
        Set<String> used = new HashSet<>();
        for (Border b : fixture.all()) {
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
}
