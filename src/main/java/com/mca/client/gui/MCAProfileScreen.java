package com.mca.client.gui;

import com.mca.client.network.MCAWebSocketClient;
import java.util.Locale;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;

/**
 * Player card: tier badge, ELO progress toward the next tier, and basic stats.
 */
public final class MCAProfileScreen extends Screen {
    private final Screen parent;

    public MCAProfileScreen(Screen parent) {
        super(new LiteralText("Profile"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 + 88;
        addButton(new MCAButton(cx - 104, y, 100, 20, "Refresh", button -> {
            MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
            if (ws != null) ws.requestProfile();
        }));
        addButton(new MCAButton(cx + 4, y, 100, 20, "Back",
            button -> this.client.openScreen(parent), MCAUi.GRAY));
        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        if (ws != null && ws.isOpen()) ws.requestProfile();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        MCAUi.backdrop(this, m, this.width, this.height);
        int cx = this.width / 2;
        int px = cx - 170;
        int py = this.height / 2 - 100;
        int pw = 340;
        int ph = 184;
        MCAUi.panel(m, px, py, px + pw, py + ph, MCAUi.PANEL_BORDER);

        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        MCAWebSocketClient.ProfileData p = ws != null ? ws.getProfile() : null;
        if (p == null) {
            String text = ws != null && ws.isOpen() ? "Loading profile..." : "Offline - profile unavailable";
            MCAUi.centered(m, text, cx, py + ph / 2 - 4, MCAUi.GRAY);
            super.render(m, mouseX, mouseY, delta);
            return;
        }

        int tierColor = MCARank.color(p.elo);
        // Header: badge + name + tier
        MCAUi.item(MCARank.icon(p.elo), px + 22, py + 18, 3f);
        MCAUi.leftScaled(m, p.username, px + 86, py + 16, 2f, MCAUi.WHITE);
        MCAUi.text(m, MCARank.label(p.elo), px + 86, py + 40, tierColor);
        MCAUi.leftScaled(m, String.valueOf(p.elo), px + 86, py + 54, 1.5f, tierColor);
        MCAUi.right(m, "#" + p.rank, px + pw - 14, py + 18, MCAUi.GOLD);
        MCAUi.right(m, "Global rank", px + pw - 14, py + 30, MCAUi.DARK_GRAY);

        // Progress to next tier
        int barX = px + 20;
        int barW = pw - 40;
        int barY = py + 92;
        MCAUi.bar(m, barX, barY, barW, 8, MCARank.progress(p.elo), tierColor);
        int next = MCARank.nextThreshold(p.elo);
        if (next < 0) {
            MCAUi.centered(m, "Top tier reached", cx, barY + 12, MCAUi.GRAY);
        } else {
            MCAUi.text(m, p.elo + " ELO", barX, barY + 12, MCAUi.GRAY);
            MCAUi.right(m, (next - p.elo) + " to " + MCARank.nextName(p.elo), barX + barW, barY + 12, MCAUi.GRAY);
        }

        // Stats
        int games = p.wins + p.losses;
        String winRate = games == 0
            ? "-"
            : String.format(Locale.ROOT, "%.0f%%", p.wins * 100.0 / games);
        String[] labels = {"Wins", "Losses", "Win rate", "Matches"};
        String[] values = {String.valueOf(p.wins), String.valueOf(p.losses), winRate, String.valueOf(games)};
        int colW = (pw - 40) / 4;
        for (int i = 0; i < 4; i++) {
            int colCx = px + 20 + colW * i + colW / 2;
            MCAUi.centeredScaled(m, values[i], colCx, py + 126, 1.5f, i == 0 ? MCAUi.GREEN : MCAUi.WHITE);
            MCAUi.centered(m, labels[i], colCx, py + 144, MCAUi.GRAY);
        }
        super.render(m, mouseX, mouseY, delta);
    }
}
