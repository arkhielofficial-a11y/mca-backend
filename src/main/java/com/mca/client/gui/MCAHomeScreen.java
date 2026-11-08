package com.mca.client.gui;

import com.mca.MCA;
import com.mca.client.network.MCAWebSocketClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;

/**
 * Main MCA hub: start a match on the left, leaderboard/profile on the right.
 */
public final class MCAHomeScreen extends Screen {
    private final Screen parent;
    private MCAButton startButton;

    public MCAHomeScreen(Screen parent) {
        super(new LiteralText("MCA"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = 24;
        int buttonWidth = 180;
        int backY = this.height - 40;
        int startY = backY - 30;

        startButton = addButton(new MCAButton(
            left, startY, buttonWidth, 24, "Start a Match",
            button -> this.client.openScreen(new MCAMatchSelectScreen(this))
        ));
        addButton(new MCAButton(
            left, backY, buttonWidth, 24, "Back to Main Menu",
            button -> this.client.openScreen(parent),
            MCAUi.GRAY
        ));

        int navWidth = 190;
        int navX = this.width - 24 - navWidth;
        int navCount = 6;
        int navTop = 86;
        int navBottom = this.height - 40;
        // 30px per entry when there is room; tighten on short windows.
        int step = Math.max(22, Math.min(30, (navBottom - navTop) / navCount));
        int navH = step - 4;
        int navY = Math.max(navTop, this.height / 2 - step * navCount / 2);
        addButton(new MCANavButton(
            navX, navY, navWidth, navH, "Leaderboard", new ItemStack(Items.TOTEM_OF_UNDYING),
            button -> this.client.openScreen(new MCALeaderboardScreen(this))
        ));
        addButton(new MCANavButton(
            navX, navY + step, navWidth, navH, "Profile", new ItemStack(Items.PLAYER_HEAD),
            button -> this.client.openScreen(new MCAProfileScreen(this))
        ));
        addButton(new MCANavButton(
            navX, navY + step * 2, navWidth, navH, "Match History", new ItemStack(Items.WRITABLE_BOOK),
            button -> this.client.openScreen(new MCAHistoryScreen(this))
        ));
        addButton(new MCANavButton(
            navX, navY + step * 3, navWidth, navH, "Splits & Stats", new ItemStack(Items.COMPASS),
            button -> this.client.openScreen(new MCAStatsScreen(this))
        ));
        addButton(new MCANavButton(
            navX, navY + step * 4, navWidth, navH, "Settings", new ItemStack(Items.COMPARATOR),
            button -> this.client.openScreen(new MCASettingsScreen(this))
        ));
        addButton(new MCANavButton(
            navX, navY + step * 5, navWidth, navH, "Refresh Data", new ItemStack(Items.CLOCK),
            button -> refresh()
        ));
        refresh();
    }

    private void refresh() {
        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        if (ws != null && ws.isOpen()) {
            ws.requestProfile();
            ws.requestLeaderboard();
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
        startButton.active = connected;

        // Title block
        int cx = this.width / 2;
        int tw = 260;
        int th = 52;
        int tx = cx - tw / 2;
        int ty = 26;
        MCAUi.panel(m, tx, ty, tx + tw, ty + th, MCAUi.GREEN_BORDER);
        MCAUi.centeredScaled(m, "MINECRAFT ARENA", cx, ty + 9, 2f, MCAUi.GREEN);
        MCAUi.centered(m, "Ranked speedrun - 1v1", cx, ty + th - 15, MCAUi.GRAY);

        renderProfileChip(m, ws);

        // Footer: version and connection status
        String version = FabricLoader.getInstance().getModContainer(MCA.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("dev");
        MCAUi.right(m, "MCA Client v" + version, this.width - 12, this.height - 14, MCAUi.DARK_GRAY);

        String status;
        int statusColor;
        if (ws == null) {
            status = "Not configured - set serverUrl and authToken in config/mca.properties";
            statusColor = MCAUi.RED;
        } else if (!connected) {
            status = "Connecting to server...";
            statusColor = MCAUi.GOLD;
        } else {
            status = "Connected";
            statusColor = MCAUi.GREEN;
        }
        int statusY = this.height - 26;
        MCAUi.right(m, status, this.width - 12, statusY, statusColor);
        DrawableHelper.fill(m, this.width - 12 - this.textRenderer.getWidth(status) - 10, statusY + 2,
            this.width - 12 - this.textRenderer.getWidth(status) - 4, statusY + 8, statusColor);

        super.render(m, mouseX, mouseY, delta);
    }

    private void renderProfileChip(MatrixStack m, MCAWebSocketClient ws) {
        int x = 12;
        int y = 12;
        int w = 168;
        int h = 34;
        MCAUi.panel(m, x, y, x + w, y + h, MCAUi.PANEL_BORDER);
        MCAWebSocketClient.ProfileData profile = ws != null ? ws.getProfile() : null;
        if (profile == null) {
            MCAUi.text(m, ws != null && ws.isOpen() ? "Loading profile..." : "Offline", x + 8, y + 13, MCAUi.GRAY);
            return;
        }
        MCAUi.item(MCARank.icon(profile.elo), x + 6, y + 9, 1f);
        MCAUi.text(m, profile.username, x + 28, y + 6, MCAUi.WHITE);
        MCAUi.text(m, MCARank.label(profile.elo) + "  " + profile.elo, x + 28, y + 18,
            MCARank.color(profile.elo));
        MCAUi.right(m, "#" + profile.rank, x + w - 6, y + 6, MCAUi.GOLD);
    }
}
