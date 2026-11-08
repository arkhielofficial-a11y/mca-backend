package com.mca.client.core;

import com.mca.client.gui.MCARank;
import com.mca.client.gui.MCAUi;
import com.mca.client.network.MCAWebSocketClient;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * In-game HUD: timers and a "Current Match" card on the right, own splits on
 * the left, and centered countdown / waiting / error notices.
 */
public final class MCAHUD {
    private static final int MARGIN = 6;

    private MCAHUD() {}

    public static void initialize() {
        HudRenderCallback.EVENT.register(MCAHUD::render);
    }

    public static void refresh() {}

    private static void render(MatrixStack m, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        TextRenderer text = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        boolean inMatch = ws != null && ws.isInMatch();

        renderTimers(m, sw);
        if (inMatch && MCASettings.showMatchCard) renderMatchCard(m, text, ws, sw, 38);
        if (MCASettings.showSplits) renderSplits(m, text);
        renderCenterNotice(m, text, ws, inMatch, sw, sh);
    }

    private static void renderTimers(MatrixStack m, int sw) {
        int y = 6;
        if (MCASettings.showRta) {
            MCAUi.rightScaled(m, MCASettings.formatTimer(MCATimer.getRtaMilliseconds()),
                sw - MARGIN, y, 1.5f, MCAUi.WHITE);
            y += 16;
        }
        if (MCASettings.showIgt) {
            MCAUi.right(m, MCASettings.formatTimer(MCATimer.getIgtMilliseconds()),
                sw - MARGIN, y, MCAUi.GREEN);
        }
    }

    private static void renderMatchCard(MatrixStack m, TextRenderer text,
                                        MCAWebSocketClient ws, int sw, int y) {
        String opponent = ws.getOpponentName();
        int oppElo = ws.getOpponentElo();
        String oppLine = oppElo >= 0 ? opponent + "  " + MCARank.label(oppElo) : opponent;
        String splitName = ws.getLastOpponentSplit();
        String splitLine = splitName != null
            ? MCAUi.prettySplit(splitName) + "  " + MCATimer.format(ws.getLastOpponentSplitMs())
            : "Started match";

        int width = Math.max(
            Math.max(text.getWidth("Current Match"), text.getWidth("Ranked Mode")),
            Math.max(text.getWidth(oppLine), text.getWidth(splitLine))
        ) + 10;
        int x2 = sw - MARGIN + 2;
        int x1 = x2 - width;
        DrawableHelper.fill(m, x1, y - 3, x2, y + 44, 0x70000000);
        DrawableHelper.fill(m, x2 - 2, y - 3, x2, y + 44, MCAUi.GOLD);

        int right = sw - MARGIN - 4;
        Text header = new LiteralText("Current Match").formatted(Formatting.BOLD);
        text.drawWithShadow(m, header, (float) (right - text.getWidth(header)), (float) y, MCAUi.GOLD);
        MCAUi.right(m, "Ranked Mode", right, y + 11, MCAUi.WHITE);
        MCAUi.right(m, oppLine, right, y + 22, oppElo >= 0 ? MCARank.color(oppElo) : MCAUi.AQUA);
        Text splitText = new LiteralText(splitLine).formatted(Formatting.ITALIC);
        text.drawWithShadow(m, splitText, (float) (right - text.getWidth(splitText)), (float) (y + 33), MCAUi.GRAY);
    }

    private static void renderSplits(MatrixStack m, TextRenderer text) {
        Map<MCASplitsTracker.Split, Long> splits = MCASplitsTracker.getSplits();
        if (splits.isEmpty()) return;
        int width = 0;
        for (Map.Entry<MCASplitsTracker.Split, Long> entry : splits.entrySet()) {
            width = Math.max(width, text.getWidth(MCAUi.prettySplit(entry.getKey().name()) + "  "
                + MCATimer.format(entry.getValue())));
        }
        int height = 14 + splits.size() * 11;
        DrawableHelper.fill(m, MARGIN - 3, MARGIN - 3, MARGIN + width + 6, MARGIN - 3 + height, 0x70000000);
        DrawableHelper.fill(m, MARGIN - 3, MARGIN - 3, MARGIN - 1, MARGIN - 3 + height, MCAUi.GREEN);
        MCAUi.text(m, "Splits", MARGIN + 2, MARGIN, MCAUi.GREEN);
        int y = MARGIN + 11;
        for (Map.Entry<MCASplitsTracker.Split, Long> entry : splits.entrySet()) {
            MCAUi.text(m, MCAUi.prettySplit(entry.getKey().name()) + "  "
                + MCATimer.format(entry.getValue()), MARGIN + 2, y, MCAUi.GOLD);
            y += 11;
        }
    }

    private static void renderCenterNotice(MatrixStack m, TextRenderer text, MCAWebSocketClient ws,
                                           boolean inMatch, int sw, int sh) {
        if (ws == null) return;
        long now = System.currentTimeMillis();
        int cx = sw / 2;

        if (inMatch) {
            long end = ws.getCountdownEndsAtMs();
            if (end == 0L) {
                notice(m, text, "Waiting for opponent...", cx, 10, MCAUi.GOLD);
            } else if (end > now) {
                long seconds = (end - now + 999L) / 1000L;
                MCAUi.centeredScaled(m, String.valueOf(seconds), cx, sh / 2 - 50, 6f, MCAUi.GREEN);
            } else if (now - end < 900L) {
                MCAUi.centeredScaled(m, "GO!", cx, sh / 2 - 50, 5f, MCAUi.GREEN);
            }
        } else if (ws.isQueued()) {
            notice(m, text, "Searching for opponent...  "
                + MCAUi.formatClock(now - ws.getQueuedAtMs()), cx, 10, MCAUi.GOLD);
        }

        String error = ws.getRecentError(6000L);
        if (error != null) notice(m, text, error, cx, 26, MCAUi.RED);
    }

    private static void notice(MatrixStack m, TextRenderer text, String message,
                               int cx, int y, int color) {
        int w = text.getWidth(message);
        DrawableHelper.fill(m, cx - w / 2 - 6, y - 3, cx + w / 2 + 6, y + 11, 0x80000000);
        MCAUi.centered(m, message, cx, y, color);
    }
}
