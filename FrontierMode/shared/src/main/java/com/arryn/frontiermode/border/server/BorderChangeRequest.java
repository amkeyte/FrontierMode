package com.arryn.frontiermode.border.server;

import com.arryn.frontiermode.border.common.fixture.Border;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

/**
 * A request to remove an existing Border, put to every module on Satchel's bus before the removal happens
 * (wiki/frontiermode/architecture/border.md § Change requests). Posted by {@code BorderAPI.removeBorder} and by
 * nothing else, so every caller is held and not only the command. A module that
 * must hold a Border still registers its own consumer on its own ingress and calls {@link #refuse}; Border knows none
 * and imports neither Boss nor Keeper. The poster reads {@link #refusal} when {@code post} returns: a reason means the
 * operation changes nothing and answers {@code Result.validationRejected(reason)}.
 *
 * <p>Not a module event: it has no native Forge event, so it does not extend {@code ModuleSignal}. A request event of
 * the same family as {@link com.arryn.frontiermode.core.InfoRequest}: synchronous, server thread only. A consumer can
 * only refuse; nothing alters the request. {@code addBorder} and {@code grow} post none, since they touch no existing
 * Border.
 */
public final class BorderChangeRequest {

    private final Level level;
    private final Border border;
    private String refusal;

    public BorderChangeRequest(Level level, Border border) {
        this.level = Objects.requireNonNull(level, "level");
        this.border = Objects.requireNonNull(border, "border");
    }

    /** The level the Border is in. */
    public Level level() {
        return level;
    }

    /** The Border the poster is about to remove. */
    public Border border() {
        return border;
    }

    /**
     * Says no. {@code reason} reaches the admin, so it names what holds the Border and what to do about it. Records the
     * answer and returns nothing, as {@code InfoRequest.add} does: it cannot fail. A second call keeps the first reason.
     */
    public void refuse(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (refusal == null) {
            refusal = reason;
        }
    }

    /** The first reason a consumer gave to refuse, empty when nobody refused. */
    public Optional<String> refusal() {
        return Optional.ofNullable(refusal);
    }
}
