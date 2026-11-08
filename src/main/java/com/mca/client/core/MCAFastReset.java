package com.mca.client.core;

import com.mca.MCA;
import com.mca.client.seed.MCASeedOverride;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.World;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.registry.DynamicRegistryManager;
import org.lwjgl.glfw.GLFW;

/**
 * Handles instant reset and recreates the active singleplayer world.
 */
public final class MCAFastReset {
    private static KeyBinding resetKey;

    private MCAFastReset() {}

    /** The registered reset key, or null before initialize(). */
    public static KeyBinding getResetKey() {
        return resetKey;
    }

    public static void initialize() {
        resetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mca.fast_reset",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.mca"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(MCAFastReset::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        if (!resetKey.wasPressed() || client.world == null) return;
        resetWorld(client);
    }

    /**
     * Deletes the current integrated world and immediately creates a fresh world
     * with the same level properties and the server-provided seed.
     */
    public static synchronized void resetWorld(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) return;

        SaveProperties properties = server.getSaveProperties();
        LevelInfo levelInfo = properties.getLevelInfo();
        GeneratorOptions original = properties.getGeneratorOptions();
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        DynamicRegistryManager.Impl registryManager =
            (DynamicRegistryManager.Impl) server.getRegistryManager();

        MCATimer.stop();
        String nextName = worldRoot.getFileName().toString() + "-mca";
        server.stop(false);

        deleteRecursively(worldRoot);

        GeneratorOptions options = MCASeedOverride.apply(original);
        client.execute(() -> {
            client.disconnect();
            client.createWorld(
                nextName,
                levelInfo,
                registryManager,
                options
            );
            MCATimer.start();
            MCASplitsTracker.reset();
        });
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) return;
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                    if (error != null) throw error;
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            MCA.LOGGER.error("MCA could not delete world {}", root, exception);
        }
    }
}
