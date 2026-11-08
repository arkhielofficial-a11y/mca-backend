package com.mca.client.gui;

import com.mca.client.network.MCAWebSocketClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.text.LiteralText;

/**
 * Small navigation helpers shared by mixins and screens.
 */
public final class MCAScreens {
    private MCAScreens() {}

    /** Asks for confirmation, then forfeits the running match. */
    public static void confirmForfeit() {
        final MinecraftClient client = MinecraftClient.getInstance();
        client.openScreen(new ConfirmScreen(
            confirmed -> {
                MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
                if (confirmed && ws != null) ws.sendForfeit();
                client.openScreen(null);
            },
            new LiteralText("Forfeit this match?"),
            new LiteralText("You will lose ELO. The server only allows a forfeit after the minimum match time.")
        ));
    }
}
