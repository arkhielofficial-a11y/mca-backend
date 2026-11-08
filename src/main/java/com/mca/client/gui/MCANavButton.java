package com.mca.client.gui;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;

/**
 * Right-aligned menu entry: label on the left, icon on the right.
 */
public final class MCANavButton extends net.minecraft.client.gui.widget.ButtonWidget {
    private final String label;
    private final ItemStack icon;

    public MCANavButton(int x, int y, int w, int h, String label, ItemStack icon, PressAction action) {
        super(x, y, w, h, new LiteralText(label), action);
        this.label = label;
        this.icon = icon;
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;
        boolean hover = this.active && mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;
        if (hover) {
            fill(m, this.x, this.y, this.x + this.width, this.y + this.height, 0x60FFFFFF);
            fill(m, this.x + this.width - 2, this.y, this.x + this.width, this.y + this.height, MCAUi.GREEN);
        }
        MCAUi.right(m, this.label, this.x + this.width - 28, this.y + (this.height - 8) / 2,
            hover ? MCAUi.WHITE : MCAUi.GRAY);
        MCAUi.item(this.icon, this.x + this.width - 22, this.y + (this.height - 16) / 2, 1f);
    }
}
