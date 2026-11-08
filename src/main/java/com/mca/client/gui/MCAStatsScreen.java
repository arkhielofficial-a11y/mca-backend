package com.mca.client.gui;

import com.mca.client.core.MCASplitsTracker;
import com.mca.client.core.MCAStatsStore;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/**
 * Personal bests per split (best / average / last) and the most recent runs.
 * Data is stored locally and only covers ranked matches played on this client.
 */
public final class MCAStatsScreen extends Screen {
    private static final int SPLIT_ROW_H = 18;
    private static final int RUN_ROW_H = 16;
    private static final int MAX_RUNS_SHOWN = 5;

    private final Screen parent;
    private MCASplitsTracker.Split[] splits;
    private MCAStatsStore.SplitStat[] stats;
    private List<MCAStatsStore.Run> runs;

    public MCAStatsScreen(Screen parent) {
        super(new LiteralText("Stats"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Snapshot once: the data only changes when a match ends.
        splits = MCASplitsTracker.Split.values();
        stats = new MCAStatsStore.SplitStat[splits.length];
        for (int i = 0; i < splits.length; i++) stats[i] = MCAStatsStore.getSplit(splits[i].name());
        runs = MCAStatsStore.getRuns();

        int cx = this.width / 2;
        int by = this.height - 30;
        addButton(new MCAButton(cx - 104, by, 100, 20, "Reset Stats",
            button -> confirmReset(), MCAUi.RED));
        addButton(new MCAButton(cx + 4, by, 100, 20, "Back",
            button -> this.client.openScreen(parent), MCAUi.GRAY));
    }

    private void confirmReset() {
        final MinecraftClient mc = this.client;
        final Screen back = this.parent;
        mc.openScreen(new ConfirmScreen(
            confirmed -> {
                if (confirmed) MCAStatsStore.clear();
                mc.openScreen(new MCAStatsScreen(back));
            },
            new LiteralText("Reset all stats?"),
            new LiteralText("This deletes your saved personal bests and run history on this computer.")
        ));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        MCAUi.backdrop(this, m, this.width, this.height);
        int cx = this.width / 2;
        MCAUi.centeredScaled(m, "SPLITS & STATS", cx, 12, 1.5f, MCAUi.WHITE);

        int tw = Math.min(400, this.width - 40);
        int left = (this.width - tw) / 2;
        int right = left + tw;

        // Summary line: best finish is the best Dragon kill time.
        long bestFinish = MCAStatsStore.getSplit(MCASplitsTracker.Split.KILL_DRAGON.name()).best;
        String summary = "Best finish: " + (bestFinish >= 0L ? MCAUi.formatPrecise(bestFinish) : "-")
            + "     Runs saved: " + runs.size();
        MCAUi.centered(m, summary, cx, 34, bestFinish >= 0L ? MCAUi.GOLD : MCAUi.GRAY);

        // Per-split table
        int headerY = 50;
        DrawableHelper.fill(m, left, headerY, right, headerY + 16, 0xC0000000);
        MCAUi.text(m, "Split", left + 8, headerY + 4, MCAUi.GRAY);
        MCAUi.right(m, "Best", right - 190, headerY + 4, MCAUi.GRAY);
        MCAUi.right(m, "Average", right - 100, headerY + 4, MCAUi.GRAY);
        MCAUi.right(m, "Last", right - 10, headerY + 4, MCAUi.GRAY);

        int rowsTop = headerY + 18;
        for (int i = 0; i < splits.length; i++) {
            MCAStatsStore.SplitStat s = stats[i];
            int y = rowsTop + i * SPLIT_ROW_H;
            DrawableHelper.fill(m, left, y, right, y + SPLIT_ROW_H - 1,
                i % 2 == 0 ? 0x50000000 : 0x28000000);
            MCAUi.text(m, MCAUi.prettySplit(splits[i].name()), left + 8, y + 5, MCAUi.WHITE);
            MCAUi.right(m, cell(s.best), right - 190, y + 5, s.best >= 0L ? MCAUi.GOLD : MCAUi.DARK_GRAY);
            MCAUi.right(m, cell(s.average()), right - 100, y + 5, MCAUi.GRAY);
            boolean lastIsBest = s.last >= 0L && s.last == s.best;
            MCAUi.right(m, cell(s.last), right - 10, y + 5,
                lastIsBest ? MCAUi.GREEN : s.last >= 0L ? MCAUi.WHITE : MCAUi.DARK_GRAY);
        }

        int runsTop = rowsTop + splits.length * SPLIT_ROW_H + 10;
        int fit = Math.max(0, Math.min(MAX_RUNS_SHOWN, (this.height - 40 - runsTop - 14) / RUN_ROW_H));
        if (runs.isEmpty()) {
            if (runsTop + 12 < this.height - 36) {
                MCAUi.centered(m, "No runs recorded yet. Finish a ranked match to see your splits.",
                    cx, runsTop, MCAUi.GRAY);
            }
        } else if (fit > 0) {
            MCAUi.text(m, "Recent runs", left + 2, runsTop, MCAUi.GRAY);
            for (int i = 0; i < Math.min(fit, runs.size()); i++) {
                renderRun(m, runs.get(i), left, right, runsTop + 12 + i * RUN_ROW_H);
            }
        }
        super.render(m, mouseX, mouseY, delta);
    }

    private static String cell(long ms) {
        return ms >= 0L ? MCAUi.formatPrecise(ms) : "-";
    }

    private void renderRun(MatrixStack m, MCAStatsStore.Run run, int left, int right, int y) {
        int accent = run.won ? MCAUi.GREEN : MCAUi.RED;
        DrawableHelper.fill(m, left, y, right, y + RUN_ROW_H - 1, 0x40000000);
        DrawableHelper.fill(m, left, y, left + 2, y + RUN_ROW_H - 1, accent);
        MCAUi.text(m, run.won ? "WIN" : "LOSS", left + 8, y + 4, accent);
        MCAUi.text(m, run.opponent != null ? "vs " + run.opponent : "", left + 44, y + 4, MCAUi.WHITE);
        if (!run.newBests.isEmpty()) {
            MCAUi.right(m, "NEW PB", right - 100, y + 4, MCAUi.GOLD);
        }
        MCAUi.right(m, run.timeMs >= 0L ? MCAUi.formatPrecise(run.timeMs)
            : "forfeit".equals(run.reason) ? "Forfeit" : "-", right - 10, y + 4, MCAUi.GRAY);
    }
}
