package com.mca.client.gui;

import com.mca.client.network.MCAWebSocketClient;
import java.util.List;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;

/**
 * Scrollable list of the player's recent finished matches. Scroll with the
 * mouse wheel or the arrow keys.
 */
public final class MCAHistoryScreen extends Screen {
    private static final int ROW_H = 24;

    private final Screen parent;
    private int firstRow;

    public MCAHistoryScreen(Screen parent) {
        super(new LiteralText("Match History"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        addButton(new MCAButton(cx - 104, this.height - 30, 100, 20, "Refresh", button -> refresh()));
        addButton(new MCAButton(cx + 4, this.height - 30, 100, 20, "Back",
            button -> this.client.openScreen(parent), MCAUi.GRAY));
        refresh();
    }

    private void refresh() {
        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        if (ws != null && ws.isOpen()) ws.requestHistory();
    }

    private int listTop() {
        return 74;
    }

    private int listBottom() {
        return this.height - 40;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_H);
    }

    private int maxFirstRow(int total) {
        return Math.max(0, total - visibleRows());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        int total = ws != null ? ws.getHistoryEntries().size() : 0;
        firstRow -= (int) Math.signum(amount) * 3;
        firstRow = Math.max(0, Math.min(maxFirstRow(total), firstRow));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_DOWN) return mouseScrolled(0, 0, -1.0 / 3.0);
        if (keyCode == GLFW.GLFW_KEY_UP) return mouseScrolled(0, 0, 1.0 / 3.0);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String timeAgo(long timestampMs) {
        if (timestampMs <= 0L) return "";
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestampMs) / 1000L);
        if (seconds < 60L) return "just now";
        if (seconds < 3600L) return (seconds / 60L) + "m ago";
        if (seconds < 172800L) return (seconds / 3600L) + "h ago";
        return (seconds / 86400L) + "d ago";
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        MCAUi.backdrop(this, m, this.width, this.height);
        int cx = this.width / 2;
        MCAUi.centeredScaled(m, "MATCH HISTORY", cx, 12, 1.5f, MCAUi.WHITE);

        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        if (ws == null || !ws.isOpen()) {
            MCAUi.centered(m, ws == null ? "Networking unavailable" : "Connecting to server...",
                cx, 60, ws == null ? MCAUi.RED : MCAUi.GOLD);
            super.render(m, mouseX, mouseY, delta);
            return;
        }

        List<MCAWebSocketClient.HistoryEntry> entries = ws.getHistoryEntries();
        if (!entries.isEmpty()) {
            int wins = 0;
            int net = 0;
            for (MCAWebSocketClient.HistoryEntry e : entries) {
                if (e.won) wins++;
                if (e.hasEloChange) net += e.eloChange;
            }
            int losses = entries.size() - wins;
            MCAUi.centered(m, "Last " + entries.size() + ":  " + wins + "W  " + losses + "L   "
                + MCAUi.signed(net) + " ELO", cx, 36, net >= 0 ? MCAUi.GREEN : MCAUi.RED);
        }

        int tw = Math.min(420, this.width - 40);
        int left = (this.width - tw) / 2;
        int right = left + tw;

        DrawableHelper.fill(m, left, 54, right, 70, 0xC0000000);
        MCAUi.text(m, "Result", left + 8, 58, MCAUi.GRAY);
        MCAUi.text(m, "Opponent", left + 62, 58, MCAUi.GRAY);
        MCAUi.right(m, "Time", right - 70, 58, MCAUi.GRAY);
        MCAUi.right(m, "ELO", right - 10, 58, MCAUi.GRAY);

        if (!ws.isHistoryLoaded()) {
            MCAUi.centered(m, "Loading match history...", cx, listTop() + 16, MCAUi.GRAY);
        } else if (entries.isEmpty()) {
            MCAUi.centered(m, "No finished matches yet. Start a match from the main menu.",
                cx, listTop() + 16, MCAUi.GRAY);
        } else {
            firstRow = Math.max(0, Math.min(maxFirstRow(entries.size()), firstRow));
            int rows = Math.min(visibleRows(), entries.size() - firstRow);
            for (int i = 0; i < rows; i++) {
                renderRow(m, entries.get(firstRow + i), left, right, listTop() + i * ROW_H, i);
            }

            int max = maxFirstRow(entries.size());
            if (max > 0) {
                int trackTop = listTop();
                int trackH = visibleRows() * ROW_H;
                int thumbH = Math.max(14, trackH * visibleRows() / entries.size());
                int thumbY = trackTop + (trackH - thumbH) * firstRow / max;
                DrawableHelper.fill(m, right + 4, trackTop, right + 8, trackTop + trackH, 0x80000000);
                DrawableHelper.fill(m, right + 4, thumbY, right + 8, thumbY + thumbH, MCAUi.GREEN);
            }
        }
        super.render(m, mouseX, mouseY, delta);
    }

    private void renderRow(MatrixStack m, MCAWebSocketClient.HistoryEntry e,
                           int left, int right, int y, int index) {
        int accent = e.won ? MCAUi.GREEN : MCAUi.RED;
        DrawableHelper.fill(m, left, y, right, y + ROW_H - 1, index % 2 == 0 ? 0x50000000 : 0x28000000);
        DrawableHelper.fill(m, left, y, left + 2, y + ROW_H - 1, accent);

        MCAUi.text(m, e.won ? "WIN" : "LOSS", left + 8, y + 8, accent);

        if (e.opponentElo >= 0) {
            MCAUi.item(MCARank.icon(e.opponentElo), left + 40, y + 4, 1f);
        }
        MCAUi.text(m, e.opponent, left + 62, y + 4, MCAUi.WHITE);
        String sub = timeAgo(e.finishedAtMs);
        if (e.seedCategory != null) sub = sub.isEmpty() ? e.seedCategory : sub + "  -  " + e.seedCategory;
        MCAUi.text(m, sub, left + 62, y + 14, MCAUi.DARK_GRAY);

        if (e.isForfeit()) {
            MCAUi.right(m, e.won ? "Opp. forfeited" : "Forfeited", right - 70, y + 4, MCAUi.GOLD);
        } else {
            MCAUi.right(m, e.timeMs >= 0L ? MCAUi.formatPrecise(e.timeMs) : "-",
                right - 70, y + 4, MCAUi.WHITE);
            if (e.opponentTimeMs >= 0L) {
                MCAUi.right(m, "vs " + MCAUi.formatPrecise(e.opponentTimeMs),
                    right - 70, y + 14, MCAUi.DARK_GRAY);
            }
        }

        if (e.hasEloChange) {
            MCAUi.right(m, MCAUi.signed(e.eloChange), right - 10, y + 8,
                e.eloChange >= 0 ? MCAUi.GREEN : MCAUi.RED);
        } else {
            MCAUi.right(m, "-", right - 10, y + 8, MCAUi.DARK_GRAY);
        }
    }
}
