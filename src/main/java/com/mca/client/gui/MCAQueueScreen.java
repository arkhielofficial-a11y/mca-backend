package com.mca.client.gui;

import com.mca.client.network.MCAWebSocketClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * One screen for the whole pre-match flow: searching, match found, create world.
 * It reads the live state of the WebSocket client every frame.
 */
public final class MCAQueueScreen extends Screen {
    private final Screen parent;
    private MCAButton mainButton;
    private MCAButton backButton;

    public MCAQueueScreen(Screen parent) {
        super(new LiteralText("Matchmaking"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int baseY = this.height / 2 + 62;
        mainButton = addButton(new MCAButton(cx - 100, baseY, 200, 22, "Cancel Search",
            button -> onMain(), MCAUi.GREEN));
        backButton = addButton(new MCAButton(cx - 100, baseY + 28, 200, 20, "Hide",
            button -> onBack(), MCAUi.GRAY));
    }

    private void onMain() {
        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        if (ws == null || !ws.isOpen()) return;
        if (ws.isInMatch()) {
            // Vanilla create-world screen; the server seed is injected by a mixin.
            this.client.openScreen(CreateWorldScreen.create(this));
        } else if (ws.isQueued()) {
            ws.setQueueJoined(false);
        } else {
            ws.setQueueJoined(true);
        }
    }

    private void onBack() {
        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        boolean searching = ws != null && ws.isQueued() && !ws.isInMatch();
        // Hiding keeps the search running; going back from idle just leaves.
        if (!searching && ws != null && !ws.isInMatch()) {
            this.client.openScreen(parent != null ? parent : new MCAHomeScreen(null));
        } else {
            this.client.openScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        MCAUi.backdrop(this, m, this.width, this.height);
        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        boolean connected = ws != null && ws.isOpen();
        boolean inMatch = connected && ws.isInMatch();
        boolean queued = connected && ws.isQueued() && !inMatch;
        long now = System.currentTimeMillis();

        int cx = this.width / 2;
        int top = this.height / 2 - 90;
        int panelW = 320;
        MCAUi.panel(m, cx - panelW / 2, top, cx + panelW / 2, this.height / 2 + 54,
            inMatch ? MCAUi.GREEN_BORDER : MCAUi.PANEL_BORDER);

        MCAWebSocketClient.ProfileData profile = ws != null ? ws.getProfile() : null;

        if (!connected) {
            MCAUi.centeredScaled(m, "OFFLINE", cx, top + 22, 2f, MCAUi.RED);
            MCAUi.centered(m, "Cannot reach the MCA server.", cx, top + 52, MCAUi.GRAY);
            MCAUi.centered(m, "It will reconnect automatically.", cx, top + 64, MCAUi.GRAY);
            mainButton.active = false;
            mainButton.setMessage(new LiteralText("Join Queue"));
            backButton.setMessage(new LiteralText("Back"));
        } else if (inMatch) {
            renderMatchFound(m, ws, profile, cx, top, now);
            mainButton.active = true;
            mainButton.setMessage(new LiteralText("Create World"));
            backButton.setMessage(new LiteralText("Close"));
        } else if (queued) {
            renderSearching(m, ws, profile, cx, top, now);
            mainButton.active = true;
            mainButton.setMessage(new LiteralText("Cancel Search"));
            backButton.setMessage(new LiteralText("Hide (keep searching)"));
        } else {
            MCAUi.centeredScaled(m, "NOT IN QUEUE", cx, top + 22, 2f, MCAUi.GRAY);
            MCAUi.centered(m, "Join the queue to find a ranked opponent.", cx, top + 56, MCAUi.GRAY);
            mainButton.active = true;
            mainButton.setMessage(new LiteralText("Join Queue"));
            backButton.setMessage(new LiteralText("Back"));
        }

        String error = ws != null ? ws.getRecentError(6000L) : null;
        if (error != null) {
            MCAUi.centered(m, error, cx, top - 14, MCAUi.RED);
        }
        super.render(m, mouseX, mouseY, delta);
    }

    private void renderSearching(MatrixStack m, MCAWebSocketClient ws,
                                 MCAWebSocketClient.ProfileData profile, int cx, int top, long now) {
        String dots = "...".substring(0, (int) ((now / 400L) % 4L));
        MCAUi.centeredScaled(m, "SEARCHING" + dots, cx, top + 14, 1.8f, MCAUi.GOLD);

        long elapsed = Math.max(0L, now - ws.getQueuedAtMs());
        MCAUi.centeredScaled(m, MCAUi.formatClock(elapsed), cx, top + 42, 3f, MCAUi.WHITE);

        if (profile != null) {
            MCAUi.centered(m, profile.username + "  -  " + MCARank.label(profile.elo)
                + " [" + profile.elo + "]", cx, top + 76, MCARank.color(profile.elo));
        }
        // Mirrors the server: starts at 100, widens 2 per second, capped at 250.
        int range = (int) Math.min(250L, 100L + (elapsed / 1000L) * 2L);
        MCAUi.centered(m, "Matching opponents within +/-" + range + " ELO", cx, top + 94, MCAUi.GRAY);
        MCAUi.centered(m, "The range widens while you wait.", cx, top + 106, MCAUi.DARK_GRAY);
    }

    private void renderMatchFound(MatrixStack m, MCAWebSocketClient ws,
                                  MCAWebSocketClient.ProfileData profile, int cx, int top, long now) {
        long countdownEnd = ws.getCountdownEndsAtMs();
        if (countdownEnd > now) {
            long remaining = countdownEnd - now;
            MCAUi.centeredScaled(m, "STARTING IN", cx, top + 12, 1.2f, MCAUi.GRAY);
            MCAUi.centeredScaled(m, String.valueOf((remaining + 999L) / 1000L), cx, top + 32, 4f, MCAUi.GREEN);
        } else {
            MCAUi.centeredScaled(m, "MATCH FOUND", cx, top + 14, 2f, MCAUi.GREEN);
        }

        int rowY = top + 62;
        // You
        int myElo = profile != null ? profile.elo : -1;
        String me = ws.getSelfName() != null ? ws.getSelfName() : "You";
        MCAUi.right(m, me, cx - 18, rowY, MCAUi.WHITE);
        if (myElo >= 0) {
            MCAUi.right(m, MCARank.label(myElo) + " [" + myElo + "]", cx - 18, rowY + 12, MCARank.color(myElo));
        }
        MCAUi.centered(m, "VS", cx, rowY + 6, MCAUi.GOLD);
        // Opponent
        int oppElo = ws.getOpponentElo();
        MCAUi.text(m, ws.getOpponentName(), cx + 18, rowY, MCAUi.WHITE);
        if (oppElo >= 0) {
            MCAUi.text(m, MCARank.label(oppElo) + " [" + oppElo + "]", cx + 18, rowY + 12, MCARank.color(oppElo));
        }

        String seedType = ws.getSeedCategory();
        if (seedType != null) {
            MCAUi.centered(m, "Seed type: " + seedType, cx, top + 96, MCAUi.AQUA);
        }
        MCAUi.centered(m, "Create a new world - the seed is applied automatically.", cx, top + 112, MCAUi.GRAY);
        MCAUi.centered(m, "In a world, press R for an instant reset.", cx, top + 124, MCAUi.DARK_GRAY);
    }
}
