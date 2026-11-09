package test.arryn.satchel.event;

import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.ingress.ModuleSignal.Verdict;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The result a module event exposes for a {@code @HasResult} native event (SAT_134): {@code allow()}
 * and {@code deny()}, a sticky deny, the default kept when nothing touches it, a refusal when the
 * native event carries no result, and a frozen verdict once dispatch has ended. Plain JUnit, with the
 * native result stood in by a field the registration layer would otherwise bind.
 */
class ModuleSignalResultTest {

    /** A module event with no context. */
    static final class Result extends ModuleSignal<String, Void> {
        final AtomicReference<Verdict> nativeResult = new AtomicReference<>(Verdict.DEFAULT);

        Result(boolean carriesResult) {
            super("native", null);
            if (carriesResult) bindResult(nativeResult::get, nativeResult::set);
        }
    }

    @Test
    void an_untouched_event_keeps_the_default() {
        Result e = new Result(true);

        assertEquals(Verdict.DEFAULT, e.verdict());
        assertEquals(Verdict.DEFAULT, e.nativeResult.get());
    }

    @Test
    void allow_sets_allow() {
        Result e = new Result(true);

        e.allow();

        assertEquals(Verdict.ALLOW, e.verdict());
        assertEquals(Verdict.ALLOW, e.nativeResult.get());
    }

    @Test
    void deny_overrides_an_allow() {
        Result e = new Result(true);

        e.allow();
        e.deny();

        assertEquals(Verdict.DENY, e.nativeResult.get());
    }

    @Test
    void an_allow_does_not_override_a_deny() {
        Result e = new Result(true);

        e.deny();
        e.allow();

        assertEquals(Verdict.DENY, e.verdict());
        assertEquals(Verdict.DENY, e.nativeResult.get());
    }

    @Test
    void an_allow_does_not_override_a_deny_set_by_an_earlier_listener_of_the_native_event() {
        Result e = new Result(true);
        e.nativeResult.set(Verdict.DENY);   // a raw listener, before the module event existed

        e.allow();

        assertEquals(Verdict.DENY, e.nativeResult.get());
    }

    @Test
    void an_event_whose_native_event_has_no_result_refuses_allow_deny_and_verdict() {
        Result e = new Result(false);

        assertThrows(IllegalStateException.class, e::allow);
        assertThrows(IllegalStateException.class, e::deny);
        assertThrows(IllegalStateException.class, e::verdict);
    }

    @Test
    void after_dispatch_the_verdict_is_frozen_and_changing_it_throws() {
        Result e = new Result(true);
        e.deny();

        e.close();
        e.nativeResult.set(Verdict.ALLOW);   // the native event moves on; the closed event does not

        assertEquals(Verdict.DENY, e.verdict());
        assertThrows(IllegalStateException.class, e::allow);
        assertThrows(IllegalStateException.class, e::deny);
    }

    @Test
    void closing_an_event_with_no_result_is_harmless() {
        Result e = new Result(false);

        assertDoesNotThrow(e::close);
        assertThrows(IllegalStateException.class, e::verdict);
    }
}
