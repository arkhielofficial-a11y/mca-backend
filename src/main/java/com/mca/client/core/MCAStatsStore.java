package com.mca.client.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mca.MCA;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Local personal-best and run history, saved to config/mca-stats.json.
 * Only ranked match runs are recorded, so practice runs never pollute the bests.
 */
public final class MCAStatsStore {
    private static final String FILE_NAME = "mca-stats.json";
    private static final int MAX_RUNS = 25;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    private static Data data;

    private MCAStatsStore() {}

    /** Per-split aggregate. best is -1 until the split has been recorded. */
    public static final class SplitStat {
        public long best = -1L;
        public long last = -1L;
        public long total;
        public int count;

        public long average() {
            return count == 0 ? -1L : total / count;
        }
    }

    /** One finished match as seen from this client. */
    public static final class Run {
        public long at;
        public boolean won;
        public String reason;
        public long timeMs = -1L;
        public String opponent;
        public Map<String, Long> splits = new LinkedHashMap<String, Long>();
        /** Names of splits that set a new personal best in this run. */
        public List<String> newBests = new ArrayList<String>();
    }

    private static final class Data {
        Map<String, SplitStat> splits = new LinkedHashMap<String, SplitStat>();
        List<Run> runs = new ArrayList<Run>();
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static Data data() {
        if (data == null) data = read();
        return data;
    }

    private static Data read() {
        Path file = file();
        if (!Files.isRegularFile(file)) return new Data();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded == null) return new Data();
            if (loaded.splits == null) loaded.splits = new LinkedHashMap<String, SplitStat>();
            if (loaded.runs == null) loaded.runs = new ArrayList<Run>();
            return loaded;
        } catch (IOException | RuntimeException exception) {
            MCA.LOGGER.warn("MCA could not read {}; starting with empty stats", file, exception);
            return new Data();
        }
    }

    private static void write() {
        Path file = file();
        Path temp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(data(), writer);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            MCA.LOGGER.warn("MCA could not write {}", file, exception);
        }
    }

    /**
     * Records a finished match. timeMs is the player's finish time or -1 when unknown.
     * Safe to call from any thread.
     */
    public static void recordRun(boolean won, String reason, long timeMs,
                                 String opponent, Map<String, Long> splits) {
        synchronized (LOCK) {
            Data d = data();
            Run run = new Run();
            run.at = System.currentTimeMillis();
            run.won = won;
            run.reason = reason;
            run.timeMs = timeMs;
            run.opponent = opponent;
            for (Map.Entry<String, Long> entry : splits.entrySet()) {
                String name = entry.getKey();
                long value = entry.getValue();
                if (value < 0L) continue;
                run.splits.put(name, value);
                SplitStat stat = d.splits.get(name);
                if (stat == null) {
                    stat = new SplitStat();
                    d.splits.put(name, stat);
                }
                if (stat.best < 0L || value < stat.best) {
                    stat.best = value;
                    run.newBests.add(name);
                }
                stat.last = value;
                stat.total += value;
                stat.count++;
            }
            d.runs.add(0, run);
            while (d.runs.size() > MAX_RUNS) d.runs.remove(d.runs.size() - 1);
            write();
        }
    }

    /** Copy of one split's aggregate, or an empty stat when never recorded. */
    public static SplitStat getSplit(String name) {
        synchronized (LOCK) {
            SplitStat source = data().splits.get(name);
            SplitStat copy = new SplitStat();
            if (source != null) {
                copy.best = source.best;
                copy.last = source.last;
                copy.total = source.total;
                copy.count = source.count;
            }
            return copy;
        }
    }

    /** Recorded runs, newest first. */
    public static List<Run> getRuns() {
        synchronized (LOCK) {
            return new ArrayList<Run>(data().runs);
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            data = new Data();
            write();
        }
    }
}
