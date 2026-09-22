package com.arryn.satchel.common.tunable;

import com.arryn.satchel.SatchelMod;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registry + persistence for {@link Tunable} -- a dev-only balance-tuning utility, not part of
 * Satchel's gameplay-facing runtime. Touches Forge's server-lifecycle events directly rather than
 * routing through {@code ServerForgeIngress} -- a deliberate, named exception to Satchel's ingress
 * discipline (see the Forge Integration & Sidedness Contract), same category as Feral's own six
 * direct hooks or {@code Rendering.onRenderLevel}: this is dev tooling, not gameplay.
 *
 * <p>
 * Single global registry, not side-scoped like {@code LogicalFoundation} -- balance numbers have
 * no legitimate client-side value, and this only ever runs on the main server tick thread (same
 * assumption the rest of Satchel's gameplay-facing code already makes), so a plain, unsynchronized
 * {@link LinkedHashMap} is deliberate, not an oversight.
 *
 * <p>
 * No hot-reload, on purpose: values load once at {@link ServerStartingEvent} and write once at
 * {@link ServerStoppingEvent}. Changing a tuned value means stop, edit the CSV, start -- avoiding
 * the unexpected-kneecapper class of bugs a live-reload path would risk.
 *
 * <p>
 * Producer: {@code SAT_074}.
 */
@Mod.EventBusSubscriber(
        modid = SatchelMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class TunableManager {

    private static final String FILE_NAME = "satchel-tunables.csv";
    private static final String HEADER = "name,type,min,max,default,value";

    /** Insertion-ordered: rows loaded from the CSV keep their original file order automatically;
     * a name first registered this session is appended (see {@link #loadedKeys}, used to sort
     * only the newly-appended subset at write time). */
    private static final Map<String, TunableRecord> REGISTRY = new LinkedHashMap<>();

    /** Snapshot of {@link #REGISTRY}'s keys right after load -- distinguishes "was in the file"
     * from "registered fresh this session" for write-time ordering, without needing a second pass
     * over the file. */
    private static Set<String> loadedKeys = Set.of();

    private static Path csvPath;

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

        TunableRecord existing = REGISTRY.get(namespacedName);
        if (existing == null) {
            REGISTRY.put(namespacedName, new TunableRecord(type, min, max, def, def));
            return def;
        }

        if (existing.type() != type || !Objects.equals(existing.min(), min) || !Objects.equals(existing.max(), max)) {
            throw new IllegalStateException(
                    "Tunable '" + namespacedName + "' redeclared with a different shape -- recorded "
                            + existing.type() + " [" + existing.min() + "," + existing.max() + "], code declared "
                            + type + " [" + min + "," + max + "]. Fix the code to match the csv's intent, "
                            + "or fix/delete the csv row."
            );
        }

        // Shape matches -- refresh the reference-only default, keep the resolved value.
        REGISTRY.put(namespacedName, new TunableRecord(type, min, max, def, existing.value()));
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
     * Load — ServerStartingEvent
     * ========================================================== */

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent e) {
        REGISTRY.clear();
        csvPath = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);

        if (!Files.exists(csvPath)) {
            loadedKeys = Set.of();
            return;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(csvPath);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed reading " + csvPath, ex);
        }

        if (lines.size() > 1) {
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) continue;
                loadRow(line);
            }
        }

        loadedKeys = Set.copyOf(REGISTRY.keySet());
    }

    private static void loadRow(String line) {
        String[] cols = line.split(",", -1);
        String name = cols[0];
        TunableType type = TunableType.valueOf(cols[1]);

        Object min, max, def, value;
        switch (type) {
            case FLOAT -> {
                min = Float.parseFloat(cols[2]);
                max = Float.parseFloat(cols[3]);
                def = Float.parseFloat(cols[4]);
                value = Float.parseFloat(cols[5]);
            }
            case INT -> {
                min = Integer.parseInt(cols[2]);
                max = Integer.parseInt(cols[3]);
                def = Integer.parseInt(cols[4]);
                value = Integer.parseInt(cols[5]);
            }
            default -> {
                min = null;
                max = null;
                def = cols[4];
                value = cols[5];
            }
        }

        if (type != TunableType.STRING && !inRange(type, min, max, value)) {
            throw new IllegalStateException(
                    "Tunable '" + name + "': csv value " + value + " outside its own declared [" + min + "," + max + "]"
            );
        }

        REGISTRY.put(name, new TunableRecord(type, min, max, def, value));
    }

    /* =============================================================
     * Write — ServerStoppingEvent
     * ========================================================== */

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);

        for (Map.Entry<String, TunableRecord> entry : REGISTRY.entrySet()) {
            if (loadedKeys.contains(entry.getKey())) {
                lines.add(formatRow(entry.getKey(), entry.getValue()));
            }
        }

        REGISTRY.entrySet().stream()
                .filter(entry -> !loadedKeys.contains(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> lines.add(formatRow(entry.getKey(), entry.getValue())));

        try {
            Files.write(csvPath, lines);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed writing " + csvPath, ex);
        }
    }

    private static String formatRow(String name, TunableRecord r) {
        String min = r.min() == null ? "" : String.valueOf(r.min());
        String max = r.max() == null ? "" : String.valueOf(r.max());
        return String.join(",", name, r.type().name(), min, max, String.valueOf(r.def()), String.valueOf(r.value()));
    }
}
