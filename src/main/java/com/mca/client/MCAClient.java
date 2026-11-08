package com.mca.client;

import com.mca.MCA;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mca.client.core.MCATimer;
import com.mca.client.core.MCAFastReset;
import com.mca.client.core.MCASettings;
import com.mca.client.core.MCASplitsTracker;
import com.mca.client.core.MCAHUD;
import com.mca.client.seed.MCASeedOverride;
import com.mca.client.network.MCAWebSocketClient;
import com.mca.client.gui.MCALeaderboardScreen;
import com.mca.client.gui.MCAUiEvents;

/**
 * Client-only entrypoint for MCA.
 */
@Environment(EnvType.CLIENT)
public final class MCAClient implements ClientModInitializer {
    /**
     * Initializes MCA services that are only valid inside Minecraft's client process.
     */
    @Override
    public void onInitializeClient() {
        MCA.LOGGER.info("MCA client initialized");
        MCASettings.load();
        MCASeedOverride.initialize();
        MCATimer.initialize();
        MCAFastReset.initialize();
        MCASplitsTracker.initialize();
        MCAHUD.initialize();
        MCALeaderboardScreen.initialize();
        MCAUiEvents.initialize();
        try {
            initializeNetworking();
        } catch (Throwable t) {
            MCA.LOGGER.error("MCA networking failed to start", t);
        }
    }

    /**
     * Connects arena networking from launch properties, with environment fallbacks.
     */
    private static void initializeNetworking() {
        Properties file = loadConfigFile();
        String serverUrl = firstValue(
            System.getProperty("mca.serverUrl"),
            System.getenv("MCA_SERVER_URL"),
            file.getProperty("serverUrl"),
            null
        );
        String authToken = firstValue(
            System.getProperty("mca.authToken"),
            System.getenv("MCA_AUTH_TOKEN"),
            file.getProperty("authToken")
        );
        MCAWebSocketClient.connect(serverUrl, authToken);
    }

    /**
     * Reads config/mca.properties (keys: serverUrl, authToken) when it exists.
     */
    private static Properties loadConfigFile() {
        Properties properties = new Properties();
        Path file = FabricLoader.getInstance().getConfigDir().resolve("mca.properties");
        if (!Files.isRegularFile(file)) return properties;
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            MCA.LOGGER.warn("MCA could not read {}", file, exception);
        }
        return properties;
    }

    private static String firstValue(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }
}
