package com.mca.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

/**
 * Shared colors and drawing helpers so every MCA screen looks the same.
 */
public final class MCAUi {
    public static final int GREEN = 0xFF55E060;
    public static final int GREEN_BORDER = 0xFF2F8F3A;
    public static final int GREEN_DARK = 0xFF1E4D25;
    public static final int RED = 0xFFFF5555;
    public static final int GOLD = 0xFFFFD34D;
    public static final int AQUA = 0xFF55FFFF;
    public static final int GRAY = 0xFFAAAAAA;
    public static final int DARK_GRAY = 0xFF777777;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int PANEL = 0xD0141414;
    public static final int PANEL_BORDER = 0xFF4A4A4A;
    public static final int BAND = 0xB0000000;

    private MCAUi() {}

    private static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    /** Dims the screen: vanilla background plus a dark overlay. */
    public static void backdrop(Screen screen, MatrixStack m, int width, int height) {
        screen.renderBackground(m);
        DrawableHelper.fill(m, 0, 0, width, height, 0x80000000);
    }

    /** Bordered dark panel. */
    public static void panel(MatrixStack m, int x1, int y1, int x2, int y2, int border) {
        DrawableHelper.fill(m, x1, y1, x2, y2, border);
        DrawableHelper.fill(m, x1 + 1, y1 + 1, x2 - 1, y2 - 1, PANEL);
    }

    public static void text(MatrixStack m, String s, int x, int y, int color) {
        font().drawWithShadow(m, s, (float) x, (float) y, color);
    }

    public static void centered(MatrixStack m, String s, int cx, int y, int color) {
        font().drawWithShadow(m, s, cx - font().getWidth(s) / 2f, (float) y, color);
    }

    public static void right(MatrixStack m, String s, int rightX, int y, int color) {
        font().drawWithShadow(m, s, (float) (rightX - font().getWidth(s)), (float) y, color);
    }

    public static void centeredScaled(MatrixStack m, String s, int cx, int y, float scale, int color) {
        TextRenderer tr = font();
        m.push();
        m.translate(cx, y, 0);
        m.scale(scale, scale, 1f);
        tr.drawWithShadow(m, s, -tr.getWidth(s) / 2f, 0f, color);
        m.pop();
    }

    public static void rightScaled(MatrixStack m, String s, int rightX, int y, float scale, int color) {
        TextRenderer tr = font();
        m.push();
        m.translate(rightX - tr.getWidth(s) * scale, y, 0);
        m.scale(scale, scale, 1f);
        tr.drawWithShadow(m, s, 0f, 0f, color);
        m.pop();
    }

    public static void leftScaled(MatrixStack m, String s, int x, int y, float scale, int color) {
        TextRenderer tr = font();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        tr.drawWithShadow(m, s, 0f, 0f, color);
        m.pop();
    }

    /** Draws an item icon scaled up (16px * scale) at the given position. */
    public static void item(ItemStack stack, int x, int y, float scale) {
        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) x, (float) y, 0f);
        RenderSystem.scalef(scale, scale, 1f);
        MinecraftClient.getInstance().getItemRenderer().renderGuiItemIcon(stack, 0, 0);
        RenderSystem.popMatrix();
    }

    /** Horizontal progress bar. frac is clamped to 0..1. */
    public static void bar(MatrixStack m, int x, int y, int w, int h, float frac, int color) {
        float f = Math.max(0f, Math.min(1f, frac));
        DrawableHelper.fill(m, x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        DrawableHelper.fill(m, x, y, x + w, y + h, 0xFF303030);
        DrawableHelper.fill(m, x, y, x + Math.round(w * f), y + h, color);
    }

    /** mm:ss.SSS (hh:mm:ss.SSS past one hour). */
    public static String formatPrecise(long ms) {
        long t = Math.max(0L, ms);
        long millis = t % 1000L;
        long totalSeconds = t / 1000L;
        long seconds = totalSeconds % 60L;
        long minutes = (totalSeconds / 60L) % 60L;
        long hours = totalSeconds / 3600L;
        return hours > 0L
            ? String.format(Locale.ROOT, "%d:%02d:%02d.%03d", hours, minutes, seconds, millis)
            : String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }

    /** mm:ss for waiting timers. */
    public static String formatClock(long ms) {
        long totalSeconds = Math.max(0L, ms) / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    public static String signed(int n) {
        return (n >= 0 ? "+" : "") + n;
    }

    public static String prettySplit(String name) {
        if (name == null) return "";
        switch (name) {
            case "ENTER_NETHER": return "Entered Nether";
            case "BASTION": return "Found Bastion";
            case "FORTRESS": return "Found Fortress";
            case "ENTER_STRONGHOLD": return "Found Stronghold";
            case "KILL_DRAGON": return "Killed Dragon";
            default: return name;
        }
    }
}
