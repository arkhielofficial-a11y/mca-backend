package com.mca.client.gui;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;

/**
 * Large mode-selection card: big icon, title and optional subtitle.
 * Disabled cards are dimmed and show a "COMING SOON" tag.
 */
public final class MCACard extends net.minecraft.client.gui.widget.ButtonWidget {
    private final String title;
    private final ItemStack icon;
    private final float iconScale;
    private String subtitle;

    public MCACard(int x, int y, int w, int h, String title, ItemStack icon,
                   float iconScale, PressAction action) {
        super(x, y, w, h, new LiteralText(title), action);
        this.title = title;
        this.icon = icon;
        this.iconScale = iconScale;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;
        boolean hover = this.active && mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;
        int border = !this.active ? 0xFF2E2E2E : hover ? MCAUi.GREEN : 0xFF6A6A6A;
        fill(m, this.x, this.y, this.x + this.width, this.y + this.height, border);
        fill(m, this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1,
            hover ? 0xE01C241C : 0xE0101010);

        int iconSize = Math.round(16 * this.iconScale);
        int iconX = this.x + (this.width - iconSize) / 2;
        int iconY = this.y + Math.max(8, (this.height - iconSize) / 2 - 12);
        MCAUi.item(this.icon, iconX, iconY, this.iconScale);
        if (!this.active) {
            fill(m, iconX - 2, iconY - 2, iconX + iconSize + 2, iconY + iconSize + 2, 0xB0101010);
        }

        int cx = this.x + this.width / 2;
        int titleY = this.y + this.height - (this.subtitle != null ? 30 : 20);
        MCAUi.centered(m, this.title, cx, titleY, this.active ? MCAUi.WHITE : MCAUi.DARK_GRAY);
        if (this.subtitle != null) {
            MCAUi.centered(m, this.subtitle, cx, titleY + 12, this.active ? MCAUi.GRAY : MCAUi.DARK_GRAY);
        }
        if (this.visible && this.height > 40 && !this.active && this.subtitle == null) {
            MCAUi.centered(m, "COMING SOON", cx, this.y + 6, 0xFF8A6D1F);
        }
    }
}
