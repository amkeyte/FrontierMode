package com.arryn.satchel.common.tunable;

import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry, loading, and files for {@link TuningTable} (SAT_092).
 *
 * <p>
 * <b>Files.</b> Each table {@code ns:name} has two copies, at the same relative path:
 * <ul>
 *   <li><b>Shipped:</b> {@value #DIR}{@code /ns/name.csv} in the resources of the mod whose id is
 *   {@code ns} -- the consuming mod ships its own tables. Read-only.</li>
 *   <li><b>Live:</b> {@code <config>/}{@value #DIR}{@code /ns/name.csv} -- the one a developer
 *   edits, and the one this class writes.</li>
 * </ul>
 *
 * <p>
 * <b>Loading.</b> A table loads the first time it is read while a server is running, and again
 * after the running server changes (a new world in the same game) or on {@link #reload}. No Forge
 * lifecycle event is hooked. A load:
 * <ol>
 *   <li>Starts from the live file; with none, from the shipped file; with neither, from the
 *   declaration's default rows.</li>
 *   <li>Merges the shipped file into an existing live file: a shipped row the live file lacks is
 *   added; a hand-tuned cell the live file leaves blank takes the shipped one; a filled live cell
 *   is kept.</li>
 *   <li>Adds an empty row for every declared key that's missing.</li>
 *   <li>Rewrites every seed cell from the consumer's formula.</li>
 *   <li>Parses and checks every cell. Any problem throws, naming the file and line.</li>
 *   <li>Writes the live file if its text changed. Cell text is kept as typed; only seeds are
 *   formatted here.</li>
 * </ol>
 *
 * <p>
 * Server thread only, like {@link TunableManager}: plain, unsynchronized maps.
 */
public final class TuningTables {

    static final String DIR = "satchel-tables";

    private static final Map<ResourceLocation, TuningTable> DEFINED = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Loaded> LOADED = new HashMap<>();
    private static MinecraftServer loadedFor;

    private TuningTables() {}

    record Loaded(Map<String, TuningTable.Row> rows) {}

    static void define(TuningTable table) {
        if (DEFINED.putIfAbsent(table.id(), table) != null) {
            throw new IllegalStateException("Tuning table " + table.id() + " declared twice");
        }
    }

    /** The loaded rows, loading the table first if this server hasn't yet. A bad file throws. */
    static Loaded loaded(TuningTable table) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("Tuning table " + table.id() + " read with no server running");
        }
        bindServer(server);
        Loaded loaded = LOADED.get(table.id());
        if (loaded == null) {
            loaded = load(table);
            LOADED.put(table.id(), loaded);
        }
        return loaded;
    }

    private static void bindServer(MinecraftServer server) {
        if (server != loadedFor) {
            LOADED.clear();
            loadedFor = server;
        }
    }

    /* =============================================================
     * Runtime -- the /satchel tables commands
     * ========================================================== */

    /** Every declared table id. */
    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (ResourceLocation id : DEFINED.keySet()) names.add(id.toString());
        return names;
    }

    /** One line per declared table: its live file, and its row count if loaded. */
    public static List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (TuningTable table : DEFINED.values()) {
            Loaded loaded = LOADED.get(table.id());
            lines.add(table.id() + " -- " + (loaded == null ? "not loaded yet" : loaded.rows().size() + " row(s)")
                    + " -- config/" + DIR + "/" + table.id().getNamespace() + "/" + table.id().getPath() + ".csv");
        }
        return lines;
    }

    /**
     * Loads {@code target} (a table id), or every declared table when {@code null}, fresh from its
     * files. All selected tables must load cleanly; otherwise the previously loaded copies stay and
     * nothing changes.
     */
    public static TunableOutcome reload(String target) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return TunableOutcome.notRunning();

        List<TuningTable> selected = new ArrayList<>();
        if (target == null) {
            selected.addAll(DEFINED.values());
        } else {
            ResourceLocation id = ResourceLocation.tryParse(target);
            TuningTable table = id == null ? null : DEFINED.get(id);
            if (table == null) return TunableOutcome.unknownTable(target);
            selected.add(table);
        }
        if (selected.isEmpty()) return TunableOutcome.ok("No tuning tables are declared.");

        bindServer(server);
        Map<ResourceLocation, Loaded> fresh = new LinkedHashMap<>();
        try {
            for (TuningTable table : selected) fresh.put(table.id(), load(table));
        } catch (RuntimeException ex) {
            return TunableOutcome.rejected(ex.getMessage() + ".");
        }

        int changed = 0;
        for (Map.Entry<ResourceLocation, Loaded> entry : fresh.entrySet()) {
            changed += changedRows(LOADED.get(entry.getKey()), entry.getValue());
        }
        LOADED.putAll(fresh);

        String summary = "Reloaded " + fresh.size() + " table(s): " + changed + " row(s) read differently now.";
        OUT.info("[TuningTables] reload(" + (target == null ? "all" : target) + "): " + summary);
        return TunableOutcome.ok(summary);
    }

    private static int changedRows(Loaded before, Loaded after) {
        if (before == null) return after.rows().size();
        int changed = 0;
        for (Map.Entry<String, TuningTable.Row> entry : after.rows().entrySet()) {
            TuningTable.Row old = before.rows().get(entry.getKey());
            if (old == null || !old.cells().equals(entry.getValue().cells())) changed++;
        }
        return changed;
    }

    /* =============================================================
     * Load
     * ========================================================== */

    private static Loaded load(TuningTable table) {
        Path live = livePath(table);
        Grid shipped = readShipped(table);
        Grid liveGrid = Files.exists(live) ? Grid.parse(table, readLines(live), live.toString()) : null;

        Grid grid = merge(table, shipped, liveGrid);
        if (table.hasDeclaredKeys()) {
            for (int k = table.firstKey(); k <= table.lastKey(); k++) {
                grid.rows.putIfAbsent(String.valueOf(k), new GridRow("row " + k + " (declared key)"));
            }
        }
        reseed(table, grid);
        Loaded loaded = validate(table, grid);

        String text = render(table, loaded, grid);
        if (!text.equals(Files.exists(live) ? readString(live) : null)) write(live, text);
        OUT.debug("[TuningTables] load(): " + table.id() + " -- " + loaded.rows().size() + " row(s)");
        return loaded;
    }

    private static Path livePath(TuningTable table) {
        return FMLPaths.CONFIGDIR.get().resolve(DIR).resolve(table.id().getNamespace())
                .resolve(table.id().getPath() + ".csv");
    }

    /** The shipped copy in the resources of the mod named by the table's namespace, or {@code null}. */
    private static Grid readShipped(TuningTable table) {
        IModFileInfo mod = ModList.get().getModFileById(table.id().getNamespace());
        if (mod == null) return null;
        List<String> segments = new ArrayList<>(List.of(DIR, table.id().getNamespace()));
        segments.addAll(List.of((table.id().getPath() + ".csv").split("/")));
        Path path = mod.getFile().findResource(segments.toArray(String[]::new));
        if (!Files.exists(path)) return null;
        return Grid.parse(table, readLines(path), "shipped " + String.join("/", segments));
    }

    private static Grid merge(TuningTable table, Grid shipped, Grid live) {
        if (live == null && shipped != null) return shipped;
        if (live == null) return Grid.fromDefaults(table);
        if (shipped == null) return live;

        for (Map.Entry<String, GridRow> entry : shipped.rows.entrySet()) {
            GridRow liveRow = live.rows.get(entry.getKey());
            if (liveRow == null) {
                live.rows.put(entry.getKey(), entry.getValue());
                continue;
            }
            for (TuningTable.Column column : table.columns()) {
                String hand = column.name();
                if (isBlank(liveRow.cells.get(hand)) && !isBlank(entry.getValue().cells.get(hand))) {
                    liveRow.cells.put(hand, entry.getValue().cells.get(hand));
                }
            }
        }
        return live;
    }

    private static void reseed(TuningTable table, Grid grid) {
        for (Map.Entry<String, GridRow> entry : grid.rows.entrySet()) {
            int key;
            try {
                key = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException ex) {
                continue; // reported by validate()
            }
            for (TuningTable.Column column : table.columns()) {
                if (!column.seeded()) continue;
                Object seed = column.seed().apply(key);
                String text;
                try {
                    text = seed == null ? "" : column.type().format(seed);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalStateException("Tuning table " + table.id() + " column '" + column.name()
                            + "' key " + key + ": the declared seed formula returned a bad value -- " + ex.getMessage(), ex);
                }
                entry.getValue().cells.put(column.seedName(), text);
            }
        }
    }

    private static Loaded validate(TuningTable table, Grid grid) {
        List<Map.Entry<Object, TuningTable.Row>> parsed = new ArrayList<>();
        for (Map.Entry<String, GridRow> entry : grid.rows.entrySet()) {
            GridRow gridRow = entry.getValue();
            Object key = parseCell(table, gridRow, table.keyColumn(), table.keyType(), entry.getKey());
            Map<String, Object> cells = new LinkedHashMap<>();
            for (TuningTable.Column column : table.columns()) {
                String text = gridRow.cells.get(column.name());
                if (column.seeded()) {
                    String seedText = gridRow.cells.get(column.seedName());
                    if (!isBlank(seedText)) parseCell(table, gridRow, column.seedName(), column.type(), seedText);
                    if (isBlank(text)) text = seedText;
                    if (isBlank(text)) throw fail(table, gridRow, "column '" + column.name() + "' has no value and no seed");
                } else if (isBlank(text)) {
                    if (!column.optional()) throw fail(table, gridRow, "column '" + column.name() + "' is blank");
                    cells.put(column.name(), null);
                    continue;
                }
                cells.put(column.name(), parseCell(table, gridRow, column.name(), column.type(), text));
            }
            parsed.add(Map.entry(key, new TuningTable.Row(table.id(), entry.getKey(), key, cells)));
        }

        if (table.keyType() == CellType.INT) parsed.sort(Comparator.comparingInt(e -> (Integer) e.getKey()));
        Map<String, TuningTable.Row> rows = new LinkedHashMap<>();
        for (Map.Entry<Object, TuningTable.Row> e : parsed) rows.put(e.getValue().key(), e.getValue());
        return new Loaded(rows);
    }

    private static Object parseCell(TuningTable table, GridRow row, String column, CellType type, String text) {
        try {
            return type.parse(text.trim());
        } catch (IllegalArgumentException ex) {
            throw fail(table, row, "column '" + column + "': " + ex.getMessage());
        }
    }

    private static IllegalStateException fail(TuningTable table, GridRow row, String message) {
        return new IllegalStateException("Tuning table " + table.id() + ", " + row.origin + ": " + message);
    }

    /* =============================================================
     * CSV
     * ========================================================== */

    /** CSV headers in file order: the key, then each column (a seeded one as seed, then value). */
    private static List<String> headers(TuningTable table) {
        List<String> headers = new ArrayList<>();
        headers.add(table.keyColumn());
        for (TuningTable.Column column : table.columns()) {
            if (column.seeded()) headers.add(column.seedName());
            headers.add(column.name());
        }
        return headers;
    }

    private static String render(TuningTable table, Loaded loaded, Grid grid) {
        List<String> headers = headers(table);
        StringBuilder out = new StringBuilder(String.join(",", headers)).append('\n');
        for (String key : loaded.rows().keySet()) {
            GridRow row = grid.rows.get(key);
            List<String> cells = new ArrayList<>();
            cells.add(key);
            for (String header : headers.subList(1, headers.size())) {
                cells.add(Objects.requireNonNullElse(row.cells.get(header), "").trim());
            }
            out.append(String.join(",", cells)).append('\n');
        }
        return out.toString();
    }

    /** Raw cell text by header, per key, with each row's origin for error messages. */
    private static final class Grid {

        final Map<String, GridRow> rows = new LinkedHashMap<>();

        static Grid parse(TuningTable table, List<String> lines, String file) {
            List<String> expected = headers(table);
            Grid grid = new Grid();
            List<String> header = null;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) continue;
                String[] cells = line.split(",", -1);
                if (header == null) {
                    header = new ArrayList<>();
                    for (String cell : cells) {
                        String name = cell.trim();
                        if (!expected.contains(name)) {
                            throw failAt(table, file, i, "unknown column '" + name + "' (expected " + String.join(",", expected) + ")");
                        }
                        if (header.contains(name)) throw failAt(table, file, i, "column '" + name + "' appears twice");
                        header.add(name);
                    }
                    if (!header.contains(table.keyColumn())) {
                        throw failAt(table, file, i, "no '" + table.keyColumn() + "' column");
                    }
                    continue;
                }
                if (cells.length > header.size()) {
                    throw failAt(table, file, i, cells.length + " cells but only " + header.size() + " columns");
                }
                GridRow row = new GridRow(file + " line " + (i + 1));
                for (int c = 0; c < cells.length; c++) row.cells.put(header.get(c), cells[c].trim());
                String key = Objects.requireNonNullElse(row.cells.get(table.keyColumn()), "");
                if (key.isEmpty()) throw failAt(table, file, i, "blank '" + table.keyColumn() + "'");
                if (grid.rows.containsKey(key)) throw failAt(table, file, i, "key '" + key + "' appears twice");
                grid.rows.put(key, row);
            }
            return grid;
        }

        static Grid fromDefaults(TuningTable table) {
            List<String> headers = headers(table);
            Grid grid = new Grid();
            for (List<String> cells : table.defaultRows()) {
                if (cells.size() != headers.size()) {
                    throw new IllegalStateException("Tuning table " + table.id() + ": default row " + cells
                            + " has " + cells.size() + " cells, expected " + headers.size() + " (" + String.join(",", headers) + ")");
                }
                GridRow row = new GridRow("default row '" + cells.get(0) + "'");
                for (int c = 0; c < cells.size(); c++) row.cells.put(headers.get(c), cells.get(c));
                if (grid.rows.put(cells.get(0), row) != null) {
                    throw new IllegalStateException("Tuning table " + table.id() + ": default key '" + cells.get(0) + "' appears twice");
                }
            }
            return grid;
        }

        private static IllegalStateException failAt(TuningTable table, String file, int index, String message) {
            return new IllegalStateException("Tuning table " + table.id() + ", " + file + " line " + (index + 1) + ": " + message);
        }
    }

    private static final class GridRow {

        final String origin;
        final Map<String, String> cells = new HashMap<>();

        GridRow(String origin) {
            this.origin = origin;
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed reading " + path, ex);
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed reading " + path, ex);
        }
    }

    private static void write(Path path, String text) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed writing " + path, ex);
        }
    }
}
