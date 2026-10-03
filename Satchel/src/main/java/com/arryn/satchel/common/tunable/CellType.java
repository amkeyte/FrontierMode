package com.arryn.satchel.common.tunable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The value shape of one {@link TuningTable} column -- drives parsing a cell's text, and formatting
 * a seed the consumer's formula produced. Cells are plain unquoted CSV text, so no cell may contain
 * a comma.
 */
public enum CellType {
    INT,
    FLOAT,
    STRING,
    /** An entity type id ({@code minecraft:zombie}), checked against the entity registry. */
    ENTITY_TYPE;

    /** Seeds are rounded to this many decimals, so a formula's float noise doesn't reach the file. */
    private static final int SEED_DECIMALS = 4;

    /**
     * The parsed value of a non-blank cell: an {@code Integer}, {@code Float}, {@code String}, or
     * {@code EntityType}.
     *
     * @throws IllegalArgumentException naming what's wrong with {@code text}
     */
    Object parse(String text) {
        switch (this) {
            case INT -> {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("'" + text + "' is not an integer");
                }
            }
            case FLOAT -> {
                float f;
                try {
                    f = Float.parseFloat(text);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("'" + text + "' is not a number");
                }
                if (!Float.isFinite(f)) throw new IllegalArgumentException("'" + text + "' is not a finite number");
                return f;
            }
            case STRING -> {
                return text;
            }
            default -> {
                ResourceLocation id = ResourceLocation.tryParse(text);
                if (id == null) throw new IllegalArgumentException("'" + text + "' is not a valid id");
                return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                        .orElseThrow(() -> new IllegalArgumentException("'" + text + "' is not a registered entity type"));
            }
        }
    }

    /**
     * The cell text for a seed value from the consumer's formula. Accepts the parsed types, plus any
     * {@code Number} for {@link #INT}/{@link #FLOAT} and a {@code ResourceLocation} or id string for
     * {@link #ENTITY_TYPE}.
     *
     * @throws IllegalArgumentException if {@code value} doesn't fit this type -- a consumer bug
     */
    String format(Object value) {
        String text = switch (this) {
            case INT -> {
                if (!(value instanceof Integer || value instanceof Short || value instanceof Byte)) throw mismatch(value);
                yield String.valueOf(((Number) value).intValue());
            }
            case FLOAT -> {
                if (!(value instanceof Number n)) throw mismatch(value);
                double d = n.doubleValue();
                if (!Double.isFinite(d)) throw new IllegalArgumentException("seed " + value + " is not finite");
                yield BigDecimal.valueOf(d).setScale(SEED_DECIMALS, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString();
            }
            case STRING -> {
                if (!(value instanceof String s)) throw mismatch(value);
                yield s;
            }
            default -> {
                if (value instanceof EntityType<?> type) yield EntityType.getKey(type).toString();
                if (value instanceof ResourceLocation || value instanceof String) yield value.toString();
                throw mismatch(value);
            }
        };
        if (text.contains(",") || text.contains("\n") || text.contains("\r")) {
            throw new IllegalArgumentException("seed '" + text + "' contains a comma or line break");
        }
        return text;
    }

    private IllegalArgumentException mismatch(Object value) {
        return new IllegalArgumentException("seed " + value + " ("
                + (value == null ? "null" : value.getClass().getSimpleName()) + ") is not a " + this);
    }
}
