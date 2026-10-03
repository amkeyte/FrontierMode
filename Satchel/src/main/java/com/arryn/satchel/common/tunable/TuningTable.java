package com.arryn.satchel.common.tunable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * A hand-edited table of balance values, one row per key (SAT_092) -- the sibling of {@link
 * Tunable} for values that are a list per level rather than one number. One CSV file per table.
 *
 * <p>
 * A table is declared once, in code, by the module that reads it (typically a {@code static
 * final} on its Rules class), and read wherever a value is needed:
 *
 * <pre>{@code
 * static final TuningTable KEEPER_HEALTH = TuningTable.builder(new ResourceLocation(MODID, "keeper-health"))
 *         .intKeys("pathPosition", 1, 20)
 *         .seededColumn("value", "seed", CellType.FLOAT, p -> formula(p))
 *         .build();
 *
 * float health = KEEPER_HEALTH.row(5).getFloat("value");
 * }</pre>
 *
 * <p>
 * Two kinds of table:
 * <ul>
 *   <li><b>Keyed by a declared integer range</b> ({@link Builder#intKeys}) -- every key in the
 *   range always has a row. These may have <b>seeded columns</b>: two CSV columns, a seed the
 *   consumer's formula writes and a value the developer tunes by hand. A read returns the value,
 *   or the seed when the value is blank. Seeds are recomputed from the formula every time the table
 *   loads; hand-tuned values are never touched.</li>
 *   <li><b>Free rows</b> ({@link Builder#freeKeys}) -- whatever rows the file holds, keyed by any
 *   text (an entity id, a name). No seeded columns; {@link Builder#defaultRow} supplies the rows
 *   written when no file exists yet.</li>
 * </ul>
 *
 * <p>
 * Loading, files, and validation are {@link TuningTables}'; reads are only valid while a server is
 * running. A bad row is a crash at first load, naming the file and line.
 */
public final class TuningTable {

    /** One declared column. A seeded column has two CSV headers, {@code seedName} and {@code name}. */
    record Column(String name, CellType type, boolean optional, String seedName, IntFunction<Object> seed) {

        boolean seeded() {
            return seed != null;
        }
    }

    private final ResourceLocation id;
    private final String keyColumn;
    private final CellType keyType;
    private final boolean declaredKeys;
    private final int firstKey;
    private final int lastKey;
    private final List<Column> columns;
    private final List<List<String>> defaultRows;

    private TuningTable(Builder b) {
        this.id = b.id;
        this.keyColumn = b.keyColumn;
        this.keyType = b.keyType;
        this.declaredKeys = b.declaredKeys;
        this.firstKey = b.firstKey;
        this.lastKey = b.lastKey;
        this.columns = List.copyOf(b.columns);
        this.defaultRows = List.copyOf(b.defaultRows);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public ResourceLocation id() {
        return id;
    }

    /* =============================================================
     * Reads
     * ========================================================== */

    /** The row for an integer key. Every key in a declared range has one. */
    public Row row(int key) {
        return row(String.valueOf(key));
    }

    /** The row for a key. */
    public Row row(String key) {
        return find(key).orElseThrow(() -> new IllegalStateException(
                "Tuning table " + id + " has no row '" + key + "'"));
    }

    public Optional<Row> find(int key) {
        return find(String.valueOf(key));
    }

    public Optional<Row> find(String key) {
        return Optional.ofNullable(TuningTables.loaded(this).rows().get(key));
    }

    /** Every row, in file order (integer keys ascending). */
    public List<Row> rows() {
        return List.copyOf(TuningTables.loaded(this).rows().values());
    }

    /* =============================================================
     * Declaration, for TuningTables
     * ========================================================== */

    String keyColumn() {
        return keyColumn;
    }

    CellType keyType() {
        return keyType;
    }

    boolean hasDeclaredKeys() {
        return declaredKeys;
    }

    int firstKey() {
        return firstKey;
    }

    int lastKey() {
        return lastKey;
    }

    List<Column> columns() {
        return columns;
    }

    List<List<String>> defaultRows() {
        return defaultRows;
    }

    /* =============================================================
     * Row
     * ========================================================== */

    /** One loaded row: typed reads by column name. A seeded column reads as its value, else its seed. */
    public static final class Row {

        private final ResourceLocation table;
        private final String key;
        private final Object parsedKey;
        private final Map<String, Object> cells;

        Row(ResourceLocation table, String key, Object parsedKey, Map<String, Object> cells) {
            this.table = table;
            this.key = key;
            this.parsedKey = parsedKey;
            this.cells = Collections.unmodifiableMap(cells);
        }

        /** The key as written in the file. */
        public String key() {
            return key;
        }

        /** The key of an integer-keyed table. */
        public int intKey() {
            if (!(parsedKey instanceof Integer i)) throw new IllegalStateException(table + " is not integer-keyed");
            return i;
        }

        /** Parsed cells by column name; {@code null} for a blank optional cell. */
        Map<String, Object> cells() {
            return cells;
        }

        /** Whether an optional column has a value in this row. */
        public boolean has(String column) {
            return cells.get(column) != null;
        }

        public int getInt(String column) {
            return get(column, Integer.class);
        }

        public float getFloat(String column) {
            return get(column, Float.class);
        }

        public String getString(String column) {
            return get(column, String.class);
        }

        public EntityType<?> getEntityType(String column) {
            return get(column, EntityType.class);
        }

        private <T> T get(String column, Class<T> type) {
            if (!cells.containsKey(column)) {
                throw new IllegalStateException("Tuning table " + table + " has no column '" + column + "'");
            }
            Object v = cells.get(column);
            if (v == null) {
                throw new IllegalStateException("Tuning table " + table + " row '" + key + "': optional column '"
                        + column + "' is blank -- check has() first");
            }
            if (!type.isInstance(v)) {
                throw new IllegalStateException("Tuning table " + table + " column '" + column + "' is not "
                        + type.getSimpleName());
            }
            return type.cast(v);
        }
    }

    /* =============================================================
     * Builder
     * ========================================================== */

    public static final class Builder {

        private final ResourceLocation id;
        private String keyColumn;
        private CellType keyType;
        private boolean declaredKeys;
        private int firstKey;
        private int lastKey;
        private final List<Column> columns = new ArrayList<>();
        private final Set<String> headers = new HashSet<>();
        private final List<List<String>> defaultRows = new ArrayList<>();

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        /** Keys {@code first..last}, every one always a row. Allows seeded columns. */
        public Builder intKeys(String keyColumn, int first, int last) {
            if (first > last) throw new IllegalArgumentException(id + ": key range " + first + ".." + last + " is empty");
            setKey(keyColumn, CellType.INT);
            this.declaredKeys = true;
            this.firstKey = first;
            this.lastKey = last;
            return this;
        }

        /** Rows are whatever the file holds, keyed by a cell of {@code keyType}. No seeded columns. */
        public Builder freeKeys(String keyColumn, CellType keyType) {
            setKey(keyColumn, Objects.requireNonNull(keyType, "keyType"));
            return this;
        }

        /** A column every row must fill. */
        public Builder column(String name, CellType type) {
            return add(new Column(name, type, false, null, null));
        }

        /** A column a row may leave blank; read it after {@link Row#has}. */
        public Builder optionalColumn(String name, CellType type) {
            return add(new Column(name, type, true, null, null));
        }

        /**
         * A seeded column: CSV columns {@code seedName} (written from {@code seed}, recomputed at
         * every load) and {@code name} (hand-tuned; blank means use the seed). {@code seed} may
         * return {@code null} for a key the formula has nothing for; that row's value must then be
         * filled by hand. Integer-keyed tables only.
         */
        public Builder seededColumn(String name, String seedName, CellType type, IntFunction<Object> seed) {
            Objects.requireNonNull(seed, "seed");
            if (!declaredKeys) {
                throw new IllegalStateException(id + ": seeded columns need intKeys(...) declared first");
            }
            claim(seedName);
            return add(new Column(name, type, false, seedName, seed));
        }

        /** A row (key, then one cell per CSV column after it) written when no file exists yet. */
        public Builder defaultRow(String key, String... cells) {
            if (keyType == null || declaredKeys) {
                throw new IllegalStateException(id + ": default rows are for freeKeys(...) tables");
            }
            List<String> row = new ArrayList<>();
            row.add(key);
            row.addAll(List.of(cells));
            defaultRows.add(row);
            return this;
        }

        public TuningTable build() {
            if (keyType == null) throw new IllegalStateException(id + ": no key declared");
            if (columns.isEmpty()) throw new IllegalStateException(id + ": no columns declared");
            TuningTable table = new TuningTable(this);
            TuningTables.define(table);
            return table;
        }

        private void setKey(String keyColumn, CellType keyType) {
            if (this.keyType != null) throw new IllegalStateException(id + ": key declared twice");
            claim(keyColumn);
            this.keyColumn = keyColumn;
            this.keyType = keyType;
        }

        private Builder add(Column column) {
            if (keyType == null) throw new IllegalStateException(id + ": declare the key before columns");
            Objects.requireNonNull(column.type(), "type");
            claim(column.name());
            columns.add(column);
            return this;
        }

        private void claim(String header) {
            Objects.requireNonNull(header, "header");
            if (header.isBlank() || header.contains(",")) {
                throw new IllegalArgumentException(id + ": bad column name '" + header + "'");
            }
            if (!headers.add(header)) throw new IllegalArgumentException(id + ": column '" + header + "' declared twice");
        }
    }
}
