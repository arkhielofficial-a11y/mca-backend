package com.mca.client.gui;

import com.mca.client.network.MCAWebSocketClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Opens the right screen when the server pushes a "match found" or a result.
 * Runs on the client tick so screens are only opened from the main thread.
 */
public final class MCAUiEvents {
    private static int seenFoundSeq;
    private static int seenResultSeq;

    private MCAUiEvents() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(MCAUiEvents::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        if (ws == null) return;

        int foundSeq = ws.getMatchFoundSeq();
        if (foundSeq != seenFoundSeq) {
            seenFoundSeq = foundSeq;
            if (!(client.currentScreen instanceof MCAQueueScreen)) {
                client.openScreen(new MCAQueueScreen(null));
            }
        }

        int resultSeq = ws.getResultSeq();
        if (resultSeq != seenResultSeq) {
            seenResultSeq = resultSeq;
            MCAWebSocketClient.MatchResult result = ws.getLastResult();
            if (result != null) client.openScreen(new MCAResultScreen(result));
        }
    }
}
