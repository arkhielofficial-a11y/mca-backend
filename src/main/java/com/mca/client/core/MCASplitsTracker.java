package com.mca.client.core;

import com.mca.client.network.MCAWebSocketClient;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.StructureFeature;
import net.minecraft.world.World;

/**
 * Detects standard speedrun milestones and records relative RTA timestamps.
 */
public final class MCASplitsTracker {
    public enum Split {
        ENTER_NETHER,
        BASTION,
        FORTRESS,
        ENTER_STRONGHOLD,
        KILL_DRAGON
    }

    private static final Map<Split, Long> SPLITS =
        Collections.synchronizedMap(new EnumMap<Split, Long>(Split.class));

    private MCASplitsTracker() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(MCASplitsTracker::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        detectDimension(client.world);
        detectStructures(client);
        detectDragon(client.world);
    }

    private static void detectDimension(ClientWorld world) {
        if (world.getRegistryKey().equals(World.NETHER)) record(Split.ENTER_NETHER);
    }

    private static void detectStructures(MinecraftClient client) {
        ClientWorld world = client.world;
        int chunkX = client.player.getBlockPos().getX() >> 4;
        int chunkZ = client.player.getBlockPos().getZ() >> 4;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (!world.isChunkLoaded(chunkX + dx, chunkZ + dz)) continue;
                Chunk chunk = world.getChunk(chunkX + dx, chunkZ + dz);
                Map<StructureFeature<?>, ?> starts = chunk.getStructureStarts();

                if (starts.containsKey(StructureFeature.BASTION_REMNANT)) record(Split.BASTION);
                if (starts.containsKey(StructureFeature.FORTRESS)) record(Split.FORTRESS);
                if (starts.containsKey(StructureFeature.STRONGHOLD)) record(Split.ENTER_STRONGHOLD);
            }
        }
    }

    private static void detectDragon(ClientWorld world) {
        boolean dragonFound = false;
        for (Object entity : world.getEntities()) {
            if (entity instanceof EnderDragonEntity) {
                dragonFound = true;
                EnderDragonEntity dragon = (EnderDragonEntity) entity;
                if (!dragon.isAlive() && world.getRegistryKey().equals(World.END)) {
                    record(Split.KILL_DRAGON);
                }
            }
        }
        if (!dragonFound && world.getRegistryKey().equals(World.END)) {
            // No entity means it may have been removed after death; the scoreboard
            // advancement listener is recommended for definitive Dragon kills.
        }
    }

    /**
     * Records a split once and keeps its original timestamp.
     */
    public static void record(Split split) {
        Long previous = SPLITS.putIfAbsent(split, MCATimer.getRtaMilliseconds());
        if (previous == null) {
            MCAWebSocketClient client = MCAWebSocketClient.getInstance();
            if (client != null) client.sendSplit(split.name(), SPLITS.get(split));
            if (split == Split.KILL_DRAGON && client != null) {
                client.sendFinish(SPLITS.get(split));
            }
        }
        MCAHUD.refresh();
    }

    public static Map<Split, Long> getSplits() {
        // EnumMap's copy constructor throws on an empty non-EnumMap source,
        // so copy into a fresh EnumMap instead.
        EnumMap<Split, Long> copy = new EnumMap<Split, Long>(Split.class);
        synchronized (SPLITS) {
            copy.putAll(SPLITS);
        }
        return copy;
    }

    public static void reset() {
        SPLITS.clear();
    }
}
