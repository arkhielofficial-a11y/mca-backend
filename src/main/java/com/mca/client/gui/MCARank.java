package com.mca.client.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Maps an ELO value to a tier (Coal ... Netherite) with three divisions each.
 * Division I is the lowest part of a tier, III the highest.
 */
public final class MCARank {
    private static final int[] LOWER = {0, 600, 900, 1200, 1500, 2000};
    private static final String[] NAMES = {"Coal", "Iron", "Gold", "Emerald", "Diamond", "Netherite"};
    private static final int[] COLORS = {
        0xFFAAAAAA, 0xFFE6E6E6, 0xFFFFD34D, 0xFF55FF77, 0xFF55E8FF, 0xFFB388FF
    };

    private MCARank() {}

    private static int index(int elo) {
        int value = Math.max(0, elo);
        for (int i = LOWER.length - 1; i >= 0; i--) {
            if (value >= LOWER[i]) return i;
        }
        return 0;
    }

    public static int color(int elo) {
        return COLORS[index(elo)];
    }

    /** 1..3, or 0 for the top tier which has no divisions. */
    public static int division(int elo) {
        int i = index(elo);
        if (i == LOWER.length - 1) return 0;
        int span = LOWER[i + 1] - LOWER[i];
        int d = (Math.max(0, elo) - LOWER[i]) * 3 / span;
        return Math.min(2, d) + 1;
    }

    public static String label(int elo) {
        int d = division(elo);
        String[] roman = {"", "I", "II", "III"};
        return NAMES[index(elo)] + (d == 0 ? "" : " " + roman[d]);
    }

    /** Progress inside the current tier, 0..1 (1 for the top tier). */
    public static float progress(int elo) {
        int i = index(elo);
        if (i == LOWER.length - 1) return 1f;
        return (Math.max(0, elo) - LOWER[i]) / (float) (LOWER[i + 1] - LOWER[i]);
    }

    /** ELO needed for the next tier, or -1 at the top. */
    public static int nextThreshold(int elo) {
        int i = index(elo);
        return i == LOWER.length - 1 ? -1 : LOWER[i + 1];
    }

    public static String nextName(int elo) {
        int i = index(elo);
        return i == LOWER.length - 1 ? "" : NAMES[i + 1];
    }

    public static ItemStack icon(int elo) {
        switch (index(elo)) {
            case 0: return new ItemStack(Items.COAL);
            case 1: return new ItemStack(Items.IRON_INGOT);
            case 2: return new ItemStack(Items.GOLD_INGOT);
            case 3: return new ItemStack(Items.EMERALD);
            case 4: return new ItemStack(Items.DIAMOND);
            default: return new ItemStack(Items.NETHERITE_INGOT);
        }
    }
}
