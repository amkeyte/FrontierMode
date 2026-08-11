package com.arryn.frontiermode.border.common.fixture;


import java.util.List;
import java.util.Optional;
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
        return new BorderProposal(fixture.logic);
    }

    public Border applyProposal(BorderProposal proposal) {
        fixture.requireServerSide();
        proposal.requireNotConsumed();

        if (validateProposal(proposal)) {
            return fixture.accept(proposal);
        }
        throw new IllegalStateException("Something went wrong");
    }

    //pretty much future use. maybe some sanity/rules checks, etc.
    public boolean validateProposal(BorderProposal proposal) {
        fixture.requireServerSide();

        return true;
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
}