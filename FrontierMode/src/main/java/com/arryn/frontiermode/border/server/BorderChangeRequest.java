package com.arryn.frontiermode.border.server;

import com.arryn.frontiermode.border.common.fixture.Border;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

/**
 * A request to change or remove an existing Border, put to every module on Satchel's bus before the change happens
 * (wiki/frontiermode/architecture/border.md § Change requests). Posted by {@code BorderAPI.transformBorder} and
 * {@code BorderAPI.removeBorder} and by nothing else, so every caller is held and not only the commands. A module that
 * must hold a Border still registers its own consumer on its own ingress and calls {@link #refuse}; Border knows none
 * and imports neither Boss nor Keeper. The poster reads {@link #refusal} when {@code post} returns: a reason means the
 * operation changes nothing and answers {@code Result.validationRejected(reason)}.
 *
 * <p>Not a module event: it has no native Forge event, so it does not extend {@code ModuleSignal}. A request event of
 * the same family as {@link com.arryn.frontiermode.core.InfoRequest}: synchronous, server thread only. A consumer can
 * only refuse; nothing alters the request. {@code addBorder} and {@code grow} post none, since they touch no existing
 * Border, and the path edits ({@code moveup}, {@code movedown}, {@code insert}, {@code remove}, {@code fixlayers}) are
 * not requests.
 */
public final class BorderChangeRequest {

    /** What the poster is about to do to the Border. */
    public enum Kind {
        /** A change of the center, the radius or both. */
        TRANSFORM,
        /** The Border's removal. */
        REMOVE
    }

    private final Level level;
    private final Border border;
    private final Kind kind;
    private String refusal;

    public BorderChangeRequest(Level level, Border border, Kind kind) {
        this.level = Objects.requireNonNull(level, "level");
        this.border = Objects.requireNonNull(border, "border");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    /** The level the Border is in. */
    public Level level() {
        return level;
    }

    /** The Border the poster is about to change or remove. */
    public Border border() {
        return border;
    }

    /** Whether the poster is about to transform the Border or remove it. */
    public Kind kind() {
        return kind;
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
