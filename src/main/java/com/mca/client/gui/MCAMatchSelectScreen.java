package com.mca.client.gui;

import com.mca.client.network.MCAWebSocketClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;

/**
 * Mode picker. Only Ranked is wired to the backend today; the other cards are
 * shown disabled so players can see what is planned.
 */
public final class MCAMatchSelectScreen extends Screen {
    private final Screen parent;
    private MCACard ranked;

    public MCAMatchSelectScreen(Screen parent) {
        super(new LiteralText("Select Match"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int gap = 8;
        int cardW = Math.max(90, Math.min(140, (this.width - 48 - gap * 2) / 3));
        int rowH = Math.max(70, Math.min(96, (this.height - 130) / 2));
        int gridW = cardW * 3 + gap * 2;
        int gx = (this.width - gridW) / 2;
        int gy = 44;

        ranked = addButton(new MCACard(
            gx, gy, cardW, rowH * 2 + gap, "Ranked Mode",
            new ItemStack(Items.ENDER_EYE), 4f, button -> startRanked()
        ));
        MCACard casual = addButton(new MCACard(
            gx + cardW + gap, gy, cardW, rowH, "Casual Mode",
            new ItemStack(Items.GOLD_INGOT), 2f, button -> {}
        ));
        MCACard weekly = addButton(new MCACard(
            gx + cardW + gap, gy + rowH + gap, cardW, rowH, "Weekly Race",
            new ItemStack(Items.MAP), 2f, button -> {}
        ));
        MCACard join = addButton(new MCACard(
            gx + (cardW + gap) * 2, gy, cardW, rowH, "Join a Room",
            new ItemStack(Items.IRON_PICKAXE), 2f, button -> {}
        ));
        MCACard create = addButton(new MCACard(
            gx + (cardW + gap) * 2, gy + rowH + gap, cardW, rowH, "Create a Room",
            new ItemStack(Items.CRAFTING_TABLE), 2f, button -> {}
        ));
        casual.active = false;
        weekly.active = false;
        join.active = false;
        create.active = false;

        addButton(new MCAButton(
            this.width / 2 - 100, this.height - 34, 200, 20, "Back",
            button -> this.client.openScreen(parent), MCAUi.GRAY
        ));
    }

    private void startRanked() {
        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        if (ws == null || !ws.isOpen()) return;
        if (!ws.isQueued() && !ws.isInMatch()) ws.setQueueJoined(true);
        this.client.openScreen(new MCAQueueScreen(this));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        MCAUi.backdrop(this, m, this.width, this.height);
        MCAUi.centeredScaled(m, "SELECT MATCH", this.width / 2, 16, 1.5f, MCAUi.WHITE);

        MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
        boolean connected = ws != null && ws.isOpen();
        ranked.active = connected;
        MCAWebSocketClient.ProfileData profile = ws != null ? ws.getProfile() : null;
        if (!connected) {
            ranked.setSubtitle("Server offline");
        } else if (profile != null) {
            ranked.setSubtitle(MCARank.label(profile.elo) + "  " + profile.elo);
        } else {
            ranked.setSubtitle("Find a 1v1 opponent");
        }
        super.render(m, mouseX, mouseY, delta);
    }
}
