package com.arryn.satchel.common.tunable;

import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Registry + persistence for {@link Tunable}. Touches no Forge event itself (SAT_093): {@link
 * #load} runs from Satchel's common setup -- once per game, before any world exists, so a {@code
 * Tunable} read during world creation sees the CSV -- and again from {@code ServerForgeIngress}
 * just before each server starts, so a CSV edited between two worlds of one game isn't overwritten;
 * {@code ServerForgeIngress} calls {@link #save} as each server stops. The file is instance-wide
 * ({@code config/}), not per world.
 *
 * <p>
 * Single global registry, not side-scoped like {@code LogicalFoundation} -- balance numbers have
 * no legitimate client-side value. A plain, unsynchronized {@link LinkedHashMap}, deliberately: the
 * first load runs on the main thread during common setup, before any server thread exists, and
 * everything after it runs on the server thread (the same assumption the rest of Satchel's
 * gameplay-facing code makes).
 *
 * <p>
 * <b>The running registry is the truth (SAT_090).</b> Values load as above and the whole registry
 * is written back as each server stops; in between, the {@code
 * /satchel tunables} commands change it: {@link #tune} sets one value, {@link #reset} returns one
 * row or one class's rows to their defaults, {@link #reload} replaces values (all, one row, or one
 * class's rows) from the live file, and {@link #write} saves it without stopping. A change is seen by the next {@link Tunable} construction -- every consumer
 * constructs fresh at each read -- and never by a value already consumed. Mid-session, a bad value
 * or file row is refused and nothing changes; only {@link #load} treats a bad row as a crash.
 *
 * <p>
 * <b>Seed + live file (SAT_082).</b> Two files share one name, {@value #FILE_NAME}. The
 * <em>live file</em> in the config directory is the one this class reads and writes at runtime.
 * The <em>seed</em> is a read-only copy in Satchel's own resources, shipped in Satchel's jar --
 * tunables are a Satchel facility, and consuming modules contribute rows, not files.
 * If the live file is missing, the seed rows become the live file. If it exists, each key merges
 * by this rule: seed-only rows are added; live-only rows are kept; a row in both whose live value
 * still equals its {@code shipped} column (never hand-edited) adopts the whole seed row; a
 * hand-edited row (or one with no recorded {@code shipped}) keeps the live row. Either way {@code
 * shipped} is then set to the seed's value.
 *
 * <p>
 * Producers: {@code SAT_074}, {@code SAT_082}, {@code SAT_090}, {@code SAT_093}.
 */
public final class TunableManager {

    private static final String FILE_NAME = "satchel-tunables.csv";
    private static final String HEADER = "name,type,min,max,default,value,shipped";

    /** Insertion-ordered: rows loaded at start keep their order automatically; a name first
     * registered this session is appended (see {@link #loadedKeys}, used to sort only the
     * newly-appended subset at write time). */
    private static final Map<String, TunableRecord> REGISTRY = new LinkedHashMap<>();

    /** Snapshot of {@link #REGISTRY}'s keys right after load -- distinguishes "was in the file"
     * from "registered fresh this session" for write-time ordering. */
    private static Set<String> loadedKeys = Set.of();

    private static Path csvPath;

    /** Keys changed by {@link #tune} since the last start, {@link #write}, or reload of that key --
     * what a reload of that key would discard. */
    private static final Set<String> UNSAVED = new HashSet<>();

    /** Keys some {@link Tunable} has been constructed for since the last start -- the rows whose
     * {@code def} is the running code's own default. Any other row's {@code def} is the file's
     * record of it, which may predate the current code. */
    private static final Set<String> CONSTRUCTED = new HashSet<>();

    private TunableManager() {}

    /* =============================================================
     * Registration (called from Tunable's constructors)
     * ========================================================== */

    static Object register(String namespacedName, TunableType type, Object min, Object max, Object def) {
        if (type != TunableType.STRING && !inRange(type, min, max, def)) {
            throw new IllegalStateException(
                    "Tunable '" + namespacedName + "': default " + def + " outside [" + min + "," + max + "]"
            );
        }

        CONSTRUCTED.add(namespacedName);
        TunableRecord existing = REGISTRY.get(namespacedName);
        if (existing == null) {
            REGISTRY.put(namespacedName, new TunableRecord(type, min, max, def, def, null));
            return def;
        }

        if (!existing.sameShape(new TunableRecord(type, min, max, def, def, null))) {
            throw new IllegalStateException(
                    "Tunable '" + namespacedName + "' redeclared with a different shape -- recorded "
                            + existing.type() + " [" + existing.min() + "," + existing.max() + "], code declared "
                            + type + " [" + min + "," + max + "]. Fix the code to match the csv's intent, "
                            + "or fix/delete the csv row."
            );
        }

        // Shape matches -- refresh the reference-only default, keep the resolved value.
        REGISTRY.put(namespacedName,
                new TunableRecord(type, min, max, def, existing.value(), existing.shipped()));
        return existing.value();
    }

    private static boolean inRange(TunableType type, Object min, Object max, Object val) {
        return switch (type) {
            case FLOAT -> (Float) min <= (Float) val && (Float) val <= (Float) max;
            case INT -> (Integer) min <= (Integer) val && (Integer) val <= (Integer) max;
            case STRING -> true;
        };
    }

    /* =============================================================
     * Load -- common setup, and before each server starts
     * ========================================================== */

    /**
     * Reads the seed and the live file into a fresh registry, merging per the rule above, and
     * writes the live file at once if there wasn't one. A bad row throws, naming its line -- at
     * common setup that is a mod-loading error. Nothing is lost by re-reading before a server
     * starts: the previous server's stop already wrote the registry.
     */
    public static void load() {
        REGISTRY.clear();
        UNSAVED.clear();
        CONSTRUCTED.clear();
        csvPath = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);

        Map<String, TunableRecord> seed = readSeeds();
        boolean liveExists = Files.exists(csvPath);
        Map<String, TunableRecord> live = liveExists ? readFile(csvPath) : Map.of();

        // Live rows first, in their own file order.
        for (Map.Entry<String, TunableRecord> entry : live.entrySet()) {
            TunableRecord liveRow = entry.getValue();
            TunableRecord seedRow = seed.get(entry.getKey());
            if (seedRow == null) {
                REGISTRY.put(entry.getKey(), liveRow);
                continue;
            }
            boolean untouched = liveRow.shipped() != null && Objects.equals(liveRow.value(), liveRow.shipped());
            REGISTRY.put(entry.getKey(), (untouched ? seedRow : liveRow).withShipped(seedRow.value()));
        }

        // Then seed-only rows: file order when there's no live file to sort against, alphabetical
        // when appending to an existing one, matching the write-time rule for new rows.
        List<String> seedOnly = new ArrayList<>();
        for (String key : seed.keySet()) if (!live.containsKey(key)) seedOnly.add(key);
        if (liveExists) seedOnly.sort(null);
        for (String key : seedOnly) {
            TunableRecord seedRow = seed.get(key);
            REGISTRY.put(key, seedRow.withShipped(seedRow.value()));
        }

        loadedKeys = Set.copyOf(REGISTRY.keySet());
        OUT.info("[Tunables] " + (liveExists ? "merged " : "seeded live file from ") + seed.size()
                + " shipped row(s); registry now holds " + REGISTRY.size());

        // A fresh live file is written straight away so it exists even if this run never stops
        // cleanly.
        if (!liveExists) writeLive();
    }

    /** The one seed: {@value #FILE_NAME} in Satchel's own resources, shipped in Satchel's jar.
     * Tunables are a Satchel facility -- consuming modules contribute rows, not files -- so there
     * is exactly one seed and no classpath scan. Absent (a fresh repo before the first Gradle
     * copy-back) means an empty seed, not an error. */
    private static Map<String, TunableRecord> readSeeds() {
        try (InputStream in = TunableManager.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in == null) {
                OUT.info("[Tunables] no shipped seed found");
                return Map.of();
            }
            List<String> lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines().toList();
            return parseRows(lines);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed reading tunable seed", ex);
        }
    }

    private static Map<String, TunableRecord> readFile(Path path) {
        try {
            return parseRows(Files.readAllLines(path));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed reading " + path, ex);
        }
    }

    /** Skips the header line; blank lines ignored. A bad row throws, naming its line. */
    private static Map<String, TunableRecord> parseRows(List<String> lines) {
        Map<String, TunableRecord> rows = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            String[] cols = line.split(",", -1);
            try {
                rows.put(cols[0], parseRow(cols));
            } catch (RuntimeException ex) {
                throw new IllegalStateException(
                        "Tunable csv line " + (i + 1) + " ('" + cols[0] + "'): " + ex.getMessage(), ex);
            }
        }
        return rows;
    }

    /** Accepts both the current 7-column layout and the pre-{@code shipped} 6-column one. */
    private static TunableRecord parseRow(String[] cols) {
        String name = cols[0];
        TunableType type = TunableType.valueOf(cols[1]);
        String shippedText = cols.length > 6 ? cols[6] : "";

        Object min, max, def, value, shipped;
        switch (type) {
            case FLOAT -> {
                min = Float.parseFloat(cols[2]);
                max = Float.parseFloat(cols[3]);
                def = Float.parseFloat(cols[4]);
                value = Float.parseFloat(cols[5]);
                shipped = shippedText.isEmpty() ? null : Float.parseFloat(shippedText);
            }
            case INT -> {
                min = Integer.parseInt(cols[2]);
                max = Integer.parseInt(cols[3]);
                def = Integer.parseInt(cols[4]);
                value = Integer.parseInt(cols[5]);
                shipped = shippedText.isEmpty() ? null : Integer.parseInt(shippedText);
            }
            default -> {
                min = null;
                max = null;
                def = cols[4];
                value = cols[5];
                shipped = shippedText.isEmpty() ? null : shippedText;
            }
        }

        if (type != TunableType.STRING && !inRange(type, min, max, value)) {
            throw new IllegalStateException(
                    "Tunable '" + name + "': csv value " + value + " outside its own declared [" + min + "," + max + "]"
            );
        }

        return new TunableRecord(type, min, max, def, value, shipped);
    }

    /* =============================================================
     * Runtime -- the /satchel tunables commands (SAT_090)
     * ========================================================== */

    /** Every registered name: each row loaded at start, plus any code registered since. */
    public static Set<String> names() {
        return Set.copyOf(REGISTRY.keySet());
    }

    /** One row's current state, for chat. */
    public static TunableOutcome describe(String name) {
        if (csvPath == null) return TunableOutcome.notRunning();
        TunableRecord r = REGISTRY.get(name);
        if (r == null) return TunableOutcome.unknownName(name);

        String range = r.type() == TunableType.STRING ? "" : " [" + r.min() + ", " + r.max() + "]";
        String shipped = r.shipped() == null ? "none" : String.valueOf(r.shipped());
        return TunableOutcome.ok(name + " = " + r.value() + " (" + r.type() + range + "; default "
                + r.def() + ", shipped " + shipped + ")" + (UNSAVED.contains(name) ? " -- unsaved" : ""));
    }

    /**
     * Sets one row's running value from its text form, checked against the row's registered type
     * and range exactly as construction checks it. Works on a row no code has constructed yet --
     * its shape is known from the file.
     */
    public static TunableOutcome tune(String name, String rawValue) {
        if (csvPath == null) return TunableOutcome.notRunning();
        TunableRecord r = REGISTRY.get(name);
        if (r == null) return TunableOutcome.unknownName(name);

        Object value;
        try {
            value = switch (r.type()) {
                case FLOAT -> Float.parseFloat(rawValue.trim());
                case INT -> Integer.parseInt(rawValue.trim());
                case STRING -> rawValue;
            };
        } catch (NumberFormatException ex) {
            return TunableOutcome.rejected("'" + rawValue + "' is not a valid " + r.type() + ".");
        }
        if (r.type() == TunableType.STRING) {
            // One unquoted csv cell per value: a comma or line break would split the row.
            if (rawValue.contains(",") || rawValue.contains("\n") || rawValue.contains("\r")) {
                return TunableOutcome.rejected("A string tunable can't contain a comma or line break.");
            }
        } else if (!inRange(r.type(), r.min(), r.max(), value)) {
            return TunableOutcome.rejected(value + " is outside " + name + "'s range [" + r.min() + ", " + r.max() + "].");
        }

        Object previous = r.value();
        REGISTRY.put(name, r.withValue(value));
        UNSAVED.add(name);
        OUT.info("[Tunables] tune(): " + name + " = " + value + " (was " + previous + ")");
        return TunableOutcome.ok(name + " = " + value + " (was " + previous + "). Unsaved: kept until"
                + " /satchel tunables write or server stop, and a saved value ships in the next jar's seed.");
    }

    /**
     * Re-reads the live file and applies the rows {@code target} selects: {@code null} for every
     * row, {@code "Class:*"} for one class's rows, or one exact name. The whole file must parse,
     * and every selected row must keep its registered type and range; otherwise nothing changes.
     * Only values are taken from the file -- a row's shape is fixed for the session. Rows the file
     * doesn't hold (registered by code this session) are left as they are. Unsaved tunes on the
     * selected rows are discarded.
     */
    public static TunableOutcome reload(String target) {
        if (csvPath == null) return TunableOutcome.notRunning();

        Map<String, TunableRecord> file;
        try {
            file = readFile(csvPath);
        } catch (RuntimeException ex) {
            return TunableOutcome.rejected("Couldn't read " + csvPath.getFileName() + ": " + ex.getMessage() + ".");
        }

        Map<String, TunableRecord> selected = new LinkedHashMap<>();
        for (Map.Entry<String, TunableRecord> entry : file.entrySet()) {
            if (selects(target, entry.getKey())) selected.put(entry.getKey(), entry.getValue());
        }
        if (selected.isEmpty()) {
            return TunableOutcome.notInFile(csvPath.getFileName() + " holds no row matching '" + target + "'.");
        }

        List<String> misshapen = new ArrayList<>();
        for (Map.Entry<String, TunableRecord> entry : selected.entrySet()) {
            TunableRecord existing = REGISTRY.get(entry.getKey());
            if (existing != null && !existing.sameShape(entry.getValue())) misshapen.add(entry.getKey());
        }
        if (!misshapen.isEmpty()) {
            return TunableOutcome.rejected(misshapen.size() + " row(s) changed type or range in the file ("
                    + String.join(", ", misshapen.subList(0, Math.min(3, misshapen.size())))
                    + (misshapen.size() > 3 ? ", ..." : "") + "); a shape change needs a restart.");
        }

        int changed = 0;
        int added = 0;
        int discarded = 0;
        for (Map.Entry<String, TunableRecord> entry : selected.entrySet()) {
            String key = entry.getKey();
            TunableRecord fileRow = entry.getValue();
            TunableRecord existing = REGISTRY.get(key);
            if (existing == null) {
                REGISTRY.put(key, fileRow);
                added++;
                continue;
            }
            if (!Objects.equals(existing.value(), fileRow.value())) {
                changed++;
                if (UNSAVED.contains(key)) discarded++;
            }
            REGISTRY.put(key, existing.withValue(fileRow.value()));
            UNSAVED.remove(key);
        }

        String summary = "Reloaded " + selected.size() + " row(s) from " + csvPath.getFileName() + ": "
                + changed + " changed, " + added + " added, " + discarded + " unsaved tune(s) discarded.";
        OUT.info("[Tunables] reload(" + (target == null ? "all" : target) + "): " + summary);
        return TunableOutcome.ok(summary);
    }

    /**
     * Sets the rows {@code target} selects -- one exact name, or {@code "Class:*"} -- back to their
     * default. Deliberately no "every row" form. A row's default is the running code's own once
     * code has constructed it this session; before that it is the file's recorded default, which
     * may be older than the code, and the outcome says how many rows that applies to. A default
     * outside its row's range (a hand-edited file) refuses the whole reset.
     */
    public static TunableOutcome reset(String target) {
        if (csvPath == null) return TunableOutcome.notRunning();
        Objects.requireNonNull(target, "target");

        List<String> keys = new ArrayList<>();
        for (String key : REGISTRY.keySet()) if (selects(target, key)) keys.add(key);
        if (keys.isEmpty()) return TunableOutcome.unknownName(target);

        List<String> outOfRange = new ArrayList<>();
        for (String key : keys) {
            TunableRecord r = REGISTRY.get(key);
            if (r.type() != TunableType.STRING && !inRange(r.type(), r.min(), r.max(), r.def())) outOfRange.add(key);
        }
        if (!outOfRange.isEmpty()) {
            return TunableOutcome.rejected(outOfRange.size() + " row(s) have a default outside their range ("
                    + String.join(", ", outOfRange.subList(0, Math.min(3, outOfRange.size())))
                    + (outOfRange.size() > 3 ? ", ..." : "") + ").");
        }

        int changed = 0;
        int fromFile = 0;
        for (String key : keys) {
            TunableRecord r = REGISTRY.get(key);
            if (!CONSTRUCTED.contains(key)) fromFile++;
            if (Objects.equals(r.value(), r.def())) continue;
            REGISTRY.put(key, r.withValue(r.def()));
            UNSAVED.add(key);
            changed++;
        }

        String summary = "Reset " + keys.size() + " row(s) to default: " + changed + " changed"
                + (fromFile == 0 ? "." : "; " + fromFile + " not read by code yet this session, so their"
                + " default is the file's record and may be older than the code.")
                + (changed == 0 ? "" : " Unsaved until /satchel tunables write or server stop.");
        OUT.info("[Tunables] reset(" + target + "): " + summary);
        return TunableOutcome.ok(summary);
    }

    private static boolean selects(String target, String key) {
        if (target == null) return true;
        if (target.endsWith(":*")) return key.startsWith(target.substring(0, target.length() - 1));
        return key.equals(target);
    }

    /** Writes the whole registry to the live file now, as server stop would. */
    public static TunableOutcome write() {
        if (csvPath == null) return TunableOutcome.notRunning();
        try {
            writeLive();
        } catch (UncheckedIOException ex) {
            return TunableOutcome.rejected("Couldn't write " + csvPath.getFileName() + ": " + ex.getMessage() + ".");
        }
        int saved = UNSAVED.size();
        UNSAVED.clear();
        String summary = "Wrote " + REGISTRY.size() + " row(s) to " + csvPath.getFileName()
                + " (" + saved + " unsaved tune(s) saved).";
        OUT.info("[Tunables] write(): " + summary);
        return TunableOutcome.ok(summary);
    }

    /* =============================================================
     * Write -- as each server stops
     * ========================================================== */

    /** Writes the whole registry back to the live file. */
    public static void save() {
        if (csvPath == null) return;
        writeLive();
    }

    private static void writeLive() {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);

        for (Map.Entry<String, TunableRecord> entry : REGISTRY.entrySet()) {
            if (loadedKeys.contains(entry.getKey())) {
                lines.add(formatRow(entry.getKey(), entry.getValue()));
            }
        }

        for (String key : new TreeSet<>(REGISTRY.keySet())) {
            if (!loadedKeys.contains(key)) lines.add(formatRow(key, REGISTRY.get(key)));
        }

        try {
            Files.write(csvPath, lines);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed writing " + csvPath, ex);
        }
    }

    private static String formatRow(String name, TunableRecord r) {
        String min = r.min() == null ? "" : String.valueOf(r.min());
        String max = r.max() == null ? "" : String.valueOf(r.max());
        String shipped = r.shipped() == null ? "" : String.valueOf(r.shipped());
        return String.join(",", name, r.type().name(), min, max, String.valueOf(r.def()), String.valueOf(r.value()), shipped);
    }
}
