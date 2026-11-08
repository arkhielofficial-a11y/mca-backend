package com.mca.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * Flat dark button with an accent underline on hover.
 */
public class MCAButton extends net.minecraft.client.gui.widget.ButtonWidget {
    private final int accent;

    public MCAButton(int x, int y, int w, int h, String label, PressAction action) {
        this(x, y, w, h, label, action, MCAUi.GREEN);
    }

    public MCAButton(int x, int y, int w, int h, String label, PressAction action, int accent) {
        super(x, y, w, h, new LiteralText(label), action);
        this.accent = accent;
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;
        boolean hover = this.active && mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;
        int border = !this.active ? 0xFF333333 : hover ? this.accent : 0xFF5A5A5A;
        int body = !this.active ? 0xC0181818 : hover ? 0xE0262E26 : 0xD0202020;
        fill(m, this.x, this.y, this.x + this.width, this.y + this.height, border);
        fill(m, this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, body);
        if (hover) {
            fill(m, this.x + 1, this.y + this.height - 3,
                this.x + this.width - 1, this.y + this.height - 1, this.accent);
        }
        int color = !this.active ? 0xFF777777 : hover ? 0xFFFFFFFF : 0xFFE0E0E0;
        drawCenteredText(
            m,
            MinecraftClient.getInstance().textRenderer,
            this.getMessage(),
            this.x + this.width / 2,
            this.y + (this.height - 8) / 2,
            color
        );
    }
}
