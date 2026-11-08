package com.mca.client.core;

import com.mca.MCA;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Persistent client options (config/mca-settings.properties).
 * Read by the HUD every frame, so the fields are plain volatile values.
 */
public final class MCASettings {
    private static final String FILE_NAME = "mca-settings.properties";

    public static volatile boolean showRta = true;
    public static volatile boolean showIgt = true;
    public static volatile boolean showSplits = true;
    public static volatile boolean showMatchCard = true;
    /** True: timers show milliseconds (mm:ss.SSS). False: tenths of a second. */
    public static volatile boolean preciseTimers = true;

    private MCASettings() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static synchronized void load() {
        Path file = file();
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            MCA.LOGGER.warn("MCA could not read {}", file, exception);
            return;
        }
        showRta = bool(properties, "showRta", true);
        showIgt = bool(properties, "showIgt", true);
        showSplits = bool(properties, "showSplits", true);
        showMatchCard = bool(properties, "showMatchCard", true);
        preciseTimers = bool(properties, "preciseTimers", true);
    }

    public static synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty("showRta", String.valueOf(showRta));
        properties.setProperty("showIgt", String.valueOf(showIgt));
        properties.setProperty("showSplits", String.valueOf(showSplits));
        properties.setProperty("showMatchCard", String.valueOf(showMatchCard));
        properties.setProperty("preciseTimers", String.valueOf(preciseTimers));
        Path file = file();
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "MCA client settings");
        } catch (IOException exception) {
            MCA.LOGGER.warn("MCA could not write {}", file, exception);
        }
    }

    public static synchronized void resetToDefaults() {
        showRta = true;
        showIgt = true;
        showSplits = true;
        showMatchCard = true;
        preciseTimers = true;
        save();
    }

    /** Formats a timer value according to the precision option. */
    public static String formatTimer(long ms) {
        return preciseTimers
            ? com.mca.client.gui.MCAUi.formatPrecise(ms)
            : MCATimer.format(ms);
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }
}
