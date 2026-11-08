package com.mca.client.gui;

import com.mca.client.network.MCAWebSocketClient;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;

/**
 * Scrollable ranking table. Scroll with the mouse wheel or the arrow keys.
 */
public final class MCALeaderboardScreen extends Screen {
    private static final int ROW_H = 20;
    private static KeyBinding leaderboardKey;

    private final Screen parent;
    private int firstRow;

    public MCALeaderboardScreen(Screen parent) {
        super(new LiteralText("MCA Leaderboard"));
        this.parent = parent;
    }

    /** The registered leaderboard key, or null before initialize(). */
    public static KeyBinding getLeaderboardKey() {
        return leaderboardKey;
    }

    /**
     * Registers the leaderboard key (opens straight to the table).
     */
    public static void initialize() {
        leaderboardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mca.leaderboard",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            "category.mca"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(MCALeaderboardScreen::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        if (leaderboardKey.wasPressed() && !(client.currentScreen instanceof MCALeaderboardScreen)) {
            client.openScreen(new MCALeaderboardScreen(client.currentScreen));
        }
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
        if (ws != null && ws.isOpen()) {
            ws.requestLeaderboard();
            ws.requestProfile();
        }
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
        int total = ws != null ? ws.getLeaderboardEntries().size() : 0;
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

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        MCAUi.backdrop(this, m, this.width, this.height);
        int cx = this.width / 2;
        MCAUi.centeredScaled(m, "LEADERBOARD", cx, 12, 1.5f, MCAUi.WHITE);

        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        if (ws == null || !ws.isOpen()) {
            MCAUi.centered(m, ws == null ? "Networking unavailable" : "Connecting to server...",
                cx, 60, ws == null ? MCAUi.RED : MCAUi.GOLD);
            super.render(m, mouseX, mouseY, delta);
            return;
        }

        MCAWebSocketClient.ProfileData me = ws.getProfile();
        if (me != null) {
            MCAUi.centered(m, "You: #" + me.rank + "  " + MCARank.label(me.elo) + "  " + me.elo + " ELO",
                cx, 36, MCARank.color(me.elo));
        }

        int tw = Math.min(380, this.width - 40);
        int left = (this.width - tw) / 2;
        int right = left + tw;

        // Header row
        DrawableHelper.fill(m, left, 54, right, 70, 0xC0000000);
        MCAUi.text(m, "#", left + 8, 58, MCAUi.GRAY);
        MCAUi.text(m, "Player", left + 56, 58, MCAUi.GRAY);
        MCAUi.right(m, "ELO", right - 80, 58, MCAUi.GRAY);
        MCAUi.right(m, "W / L", right - 10, 58, MCAUi.GRAY);

        List<MCAWebSocketClient.LeaderboardEntry> entries = ws.getLeaderboardEntries();
        if (entries.isEmpty()) {
            MCAUi.centered(m, "Loading leaderboard...", cx, listTop() + 16, MCAUi.GRAY);
        } else {
            firstRow = Math.max(0, Math.min(maxFirstRow(entries.size()), firstRow));
            int rows = Math.min(visibleRows(), entries.size() - firstRow);
            for (int i = 0; i < rows; i++) {
                MCAWebSocketClient.LeaderboardEntry e = entries.get(firstRow + i);
                int y = listTop() + i * ROW_H;
                boolean mine = me != null && e.username.equalsIgnoreCase(me.username);
                int bg = mine ? 0x6032A852 : (i % 2 == 0 ? 0x50000000 : 0x28000000);
                DrawableHelper.fill(m, left, y, right, y + ROW_H - 1, bg);
                if (mine) DrawableHelper.fill(m, left, y, left + 2, y + ROW_H - 1, MCAUi.GREEN);

                int rankColor = e.rank == 1 ? 0xFFFFD34D : e.rank == 2 ? 0xFFD8D8D8
                    : e.rank == 3 ? 0xFFCD7F32 : MCAUi.GRAY;
                MCAUi.text(m, String.valueOf(e.rank), left + 8, y + 6, rankColor);
                MCAUi.item(MCARank.icon(e.elo), left + 30, y + 1, 1f);
                MCAUi.text(m, e.username, left + 56, y + 6, mine ? MCAUi.GREEN : MCAUi.WHITE);
                MCAUi.right(m, String.valueOf(e.elo), right - 80, y + 6, MCARank.color(e.elo));
                MCAUi.right(m, e.wins + " / " + e.losses, right - 10, y + 6, MCAUi.GRAY);
            }

            // Scrollbar
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
}
