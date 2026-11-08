package com.mca.client.gui;

import com.mca.client.network.MCAWebSocketClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * Post-match summary: two player bands, the winner and the ELO change.
 */
public final class MCAResultScreen extends Screen {
    private final MCAWebSocketClient.MatchResult result;

    public MCAResultScreen(MCAWebSocketClient.MatchResult result) {
        super(new LiteralText("Match Result"));
        this.result = result;
    }

    @Override
    protected void init() {
        int bw = 120;
        int y = this.height - 54;
        addButton(new MCAButton(this.width - 24 - bw * 2 - 6, y, bw, 22, "Find New Match", button -> {
            MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
            if (ws != null && ws.isOpen() && !ws.isQueued() && !ws.isInMatch()) ws.setQueueJoined(true);
            this.client.openScreen(new MCAQueueScreen(null));
        }));
        addButton(new MCAButton(this.width - 24 - bw, y, bw, 22, "Done",
            button -> this.client.openScreen(null), MCAUi.GRAY));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        MCAUi.backdrop(this, m, this.width, this.height);
        int cx = this.width / 2;
        MCAUi.centered(m, "Match Room - Ranked Mode", cx, 12, MCAUi.GRAY);

        MCAWebSocketClient.MatchResult r = this.result;
        int outcomeColor = r.won ? MCAUi.GREEN : MCAUi.RED;
        String outcome = r.won ? "VICTORY" : "DEFEAT";
        MCAUi.centeredScaled(m, outcome, cx, 34, 3f, outcomeColor);
        if (r.isForfeit()) {
            MCAUi.centered(m, r.won ? "Your opponent forfeited" : "You forfeited", cx, 62, MCAUi.GRAY);
        }

        int bandH = 40;
        int youY = this.height / 2 - bandH - 14;
        int oppY = this.height / 2 + 14;

        // You (band anchored left)
        DrawableHelper.fill(m, 0, youY, this.width * 3 / 4, youY + bandH, MCAUi.BAND);
        String me = r.selfName != null ? r.selfName : "You";
        int x = Math.max(24, this.width / 2 - 200);
        MCAUi.text(m, me, x, youY + 8, MCAUi.WHITE);
        if (r.eloAfter >= 0) {
            MCAUi.item(MCARank.icon(r.eloAfter), x, youY + 19, 1f);
            String line = MCARank.label(r.eloAfter) + " [" + r.eloAfter + "] ";
            MCAUi.text(m, line, x + 20, youY + 24, MCARank.color(r.eloAfter));
            MCAUi.text(m, "(" + MCAUi.signed(r.eloChange) + ")",
                x + 20 + this.textRenderer.getWidth(line), youY + 24,
                r.eloChange >= 0 ? MCAUi.GREEN : MCAUi.RED);
        }

        // Winner line between the bands
        String winnerName = r.won ? me : r.opponentName;
        String time = r.winnerTimeMs >= 0 ? " (" + MCAUi.formatPrecise(r.winnerTimeMs) + ")" : "";
        MCAUi.centered(m, "Winner: " + winnerName + time, cx, this.height / 2 - 4, MCAUi.GOLD);

        // Opponent (band anchored right)
        DrawableHelper.fill(m, this.width / 4, oppY, this.width, oppY + bandH, MCAUi.BAND);
        int rx = Math.min(this.width - 24, this.width / 2 + 200);
        MCAUi.right(m, r.opponentName, rx, oppY + 8, MCAUi.WHITE);
        if (r.opponentElo >= 0) {
            String line = MCARank.label(r.opponentElo) + " [" + r.opponentElo + "]";
            MCAUi.right(m, line, rx - 20, oppY + 24, MCARank.color(r.opponentElo));
            MCAUi.item(MCARank.icon(r.opponentElo), rx - 16, oppY + 19, 1f);
        }

        if (r.seedCategory != null) {
            MCAUi.centered(m, "Seed type: " + r.seedCategory, cx, oppY + bandH + 14, MCAUi.AQUA);
        }
        super.render(m, mouseX, mouseY, delta);
    }
}
