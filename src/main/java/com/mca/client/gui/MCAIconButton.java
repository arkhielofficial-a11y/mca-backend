package com.mca.client.gui;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;

/**
 * Small square button showing one item icon (used next to Singleplayer).
 */
public final class MCAIconButton extends net.minecraft.client.gui.widget.ButtonWidget {
    private final ItemStack icon;

    public MCAIconButton(int x, int y, int size, ItemStack icon, PressAction action) {
        super(x, y, size, size, new LiteralText(""), action);
        this.icon = icon;
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;
        boolean hover = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;
        fill(m, this.x, this.y, this.x + this.width, this.y + this.height,
            hover ? MCAUi.GREEN : MCAUi.GREEN_BORDER);
        fill(m, this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1,
            hover ? 0xFF2E7D32 : MCAUi.GREEN_DARK);
        MCAUi.item(this.icon, this.x + (this.width - 16) / 2, this.y + (this.height - 16) / 2, 1f);
    }
}
