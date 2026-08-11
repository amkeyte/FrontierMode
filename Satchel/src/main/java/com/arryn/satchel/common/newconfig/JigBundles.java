package com.arryn.satchel.common.newconfig;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.newconfig.newnew.JigPolicies;

import java.util.List;
import java.util.Objects;

public final class JigBundles {

    private JigBundles() {}

    public record Schema<S extends SatchelScope>(
            List<BundleDecl<S, ?>> bundles
    ) {
        public Schema {
            Objects.requireNonNull(bundles);
        }
    }

    public record BundleDecl<S extends SatchelScope, B extends SatchelBundle>(
            BundleKey<B> key,
            Factory<S, B> factory,
            List<FixtureDecl< ?>> fixtures
    ) {
        public BundleDecl {
            Objects.requireNonNull(key);
            Objects.requireNonNull(factory);
            Objects.requireNonNull(fixtures);
        }
    }

    public record FixtureDecl< F extends SatchelFixture>(
            FixtureKey<F> key,
            FixtureFactory< F> factory,
            JigPolicies.CreatePolicy createPolicy
    ) {
        public FixtureDecl {
            Objects.requireNonNull(key);
            Objects.requireNonNull(factory);
            if (createPolicy == null) {
                createPolicy = JigPolicies.CreatePolicy.ALWAYS;
            }
        }
    }

    @FunctionalInterface
    public interface Factory<S extends SatchelScope, B extends SatchelBundle> {
        B create(S scope);
    }

    @FunctionalInterface
    public interface FixtureFactory<F extends SatchelFixture> {
        F create();
    }
}
