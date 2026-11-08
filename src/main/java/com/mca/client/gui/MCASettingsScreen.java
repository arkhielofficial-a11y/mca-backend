package com.mca.client.gui;

import com.mca.client.core.MCAFastReset;
import com.mca.client.core.MCASettings;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * Client options: which HUD elements are shown, timer precision, and a
 * read-only view of the MCA keybinds (rebind them in Options > Controls).
 */
public final class MCASettingsScreen extends Screen {
    private static final int ROW_STEP = 24;

    private final Screen parent;

    public MCASettingsScreen(Screen parent) {
        super(new LiteralText("MCA Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int w = 240;
        int x = cx - w / 2;
        int y = 54;

        addToggle(x, y, w, "RTA timer", "Shown", "Hidden",
            () -> MCASettings.showRta, v -> MCASettings.showRta = v);
        addToggle(x, y + ROW_STEP, w, "IGT timer", "Shown", "Hidden",
            () -> MCASettings.showIgt, v -> MCASettings.showIgt = v);
        addToggle(x, y + ROW_STEP * 2, w, "Splits panel", "Shown", "Hidden",
            () -> MCASettings.showSplits, v -> MCASettings.showSplits = v);
        addToggle(x, y + ROW_STEP * 3, w, "Match card", "Shown", "Hidden",
            () -> MCASettings.showMatchCard, v -> MCASettings.showMatchCard = v);
        addToggle(x, y + ROW_STEP * 4, w, "Timer precision", "Milliseconds", "Tenths",
            () -> MCASettings.preciseTimers, v -> MCASettings.preciseTimers = v);

        int by = this.height - 30;
        addButton(new MCAButton(cx - 124, by, 120, 20, "Reset to Defaults", button -> {
            MCASettings.resetToDefaults();
            this.client.openScreen(new MCASettingsScreen(parent));
        }, MCAUi.GOLD));
        addButton(new MCAButton(cx + 4, by, 120, 20, "Done",
            button -> this.client.openScreen(parent), MCAUi.GRAY));
    }

    private void addToggle(int x, int y, int w, String label, String onText, String offText,
                           BooleanSupplier get, Consumer<Boolean> set) {
        addButton(new MCAButton(x, y, w, 20, toggleLabel(label, get.getAsBoolean(), onText, offText),
            button -> {
                set.accept(!get.getAsBoolean());
                MCASettings.save();
                button.setMessage(new LiteralText(
                    toggleLabel(label, get.getAsBoolean(), onText, offText)));
            }));
    }

    private static String toggleLabel(String label, boolean on, String onText, String offText) {
        return label + ": " + (on ? onText : offText);
    }

    private static String keyName(KeyBinding key) {
        return key == null ? "-" : key.getBoundKeyLocalizedText().getString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        MCAUi.backdrop(this, m, this.width, this.height);
        int cx = this.width / 2;
        MCAUi.centeredScaled(m, "SETTINGS", cx, 12, 1.5f, MCAUi.WHITE);
        MCAUi.centered(m, "HUD", cx, 40, MCAUi.GRAY);

        // Keybinds are hidden on very short windows instead of overlapping the buttons.
        int keyTop = 54 + ROW_STEP * 5 + 8;
        if (keyTop + 40 <= this.height - 36) {
            int pw = 240;
            int px = cx - pw / 2;
            MCAUi.panel(m, px, keyTop, px + pw, keyTop + 40, MCAUi.PANEL_BORDER);
            MCAUi.text(m, "Fast reset", px + 10, keyTop + 8, MCAUi.GRAY);
            MCAUi.right(m, keyName(MCAFastReset.getResetKey()), px + pw - 10, keyTop + 8, MCAUi.WHITE);
            MCAUi.text(m, "Leaderboard", px + 10, keyTop + 20, MCAUi.GRAY);
            MCAUi.right(m, keyName(MCALeaderboardScreen.getLeaderboardKey()),
                px + pw - 10, keyTop + 20, MCAUi.WHITE);
            MCAUi.centered(m, "Rebind in Options > Controls > MCA", cx, keyTop + 46, MCAUi.DARK_GRAY);
        }
        super.render(m, mouseX, mouseY, delta);
    }
}
