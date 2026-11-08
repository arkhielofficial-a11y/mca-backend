package com.mca.client.seed;

import java.util.Map;
import com.mojang.serialization.Lifecycle;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.GeneratorOptions;

/**
 * Holds the server-provided signed 64-bit seed and applies it to new generators.
 */
public final class MCASeedOverride {
    private static volatile Long seed;

    private MCASeedOverride() {}

    public static void initialize() {}

    public static void setSeed(long signedSeed) {
        seed = signedSeed;
    }

    /**
     * Parses a seed sent as a signed decimal string by the arena WebSocket server.
     */
    public static boolean setSeed(String signedSeed) {
        try {
            seed = Long.parseLong(signedSeed.trim());
            return true;
        } catch (NumberFormatException exception) {
            seed = null;
            return false;
        }
    }

    public static void clearSeed() {
        seed = null;
    }

    public static Long getSeed() {
        return seed;
    }

    /**
     * Returns a new GeneratorOptions using the server seed for every dimension.
     */
    public static GeneratorOptions apply(GeneratorOptions original) {
        Long override = seed;
        if (override == null) return original;

        SimpleRegistry<DimensionOptions> originalDimensions = original.getDimensions();
        SimpleRegistry<DimensionOptions> dimensions =
            new SimpleRegistry<DimensionOptions>(originalDimensions.getKey(), Lifecycle.stable());

        for (Map.Entry<RegistryKey<DimensionOptions>, DimensionOptions> entry
            : originalDimensions.getEntries()) {
            DimensionOptions dimension = entry.getValue();
            DimensionOptions updated = new DimensionOptions(
                dimension.getDimensionTypeSupplier(),
                dimension.getChunkGenerator().withSeed(override)
            );
            dimensions.add(entry.getKey(), updated, originalDimensions.getEntryLifecycle(dimension));
        }

        return new GeneratorOptions(
            override,
            original.shouldGenerateStructures(),
            original.hasBonusChest(),
            dimensions
        );
    }
}
