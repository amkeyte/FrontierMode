package com.arryn.satchel.common.stitch;

public interface LogicalSideStitch {

    static LogicalSideStitch from(Object logicalSideStitchCandidate) {
        if (!(logicalSideStitchCandidate instanceof LogicalSideStitch logicalSideProvider)) {
            throw new IllegalStateException("Candidate does not provide logical sides");
        }
        return logicalSideProvider;
    }

    boolean isServerSide();

    boolean isClientSide();
}
