package com.mca.mixin;

import com.mca.client.gui.MCAButton;
import com.mca.client.gui.MCAHomeScreen;
import com.mca.client.gui.MCAIconButton;
import com.mca.client.gui.MCAScreens;
import com.mca.client.network.MCAWebSocketClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the MCA entry button to the title screen (a green icon next to
 * Singleplayer) and a Forfeit button to the pause menu during a match.
 */
@Mixin(Screen.class)
public abstract class ScreenLeaderboardMixin {
    @Shadow
    protected int width;

    @Shadow
    protected int height;

    @Shadow
    protected <T extends ClickableWidget> T addButton(T button) {
        throw new IllegalStateException("Mixin shadow");
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mca$addButtons(CallbackInfo callback) {
        Object self = this;
        if (self instanceof TitleScreen) {
            // Singleplayer is at (width / 2 - 100, height / 4 + 48), 200 x 20.
            this.addButton(new MCAIconButton(
                this.width / 2 + 104,
                this.height / 4 + 48,
                20,
                new ItemStack(Items.ENDER_EYE),
                button -> MinecraftClient.getInstance().openScreen(
                    new MCAHomeScreen(MinecraftClient.getInstance().currentScreen)
                )
            ));
        } else if (self instanceof GameMenuScreen) {
            MCAWebSocketClient ws = MCAWebSocketClient.getInstance();
            if (ws != null && ws.isInMatch()) {
                this.addButton(new MCAButton(
                    8, 8, 110, 20, "Forfeit Match",
                    button -> MCAScreens.confirmForfeit(),
                    0xFFFF5555
                ));
            }
        }
    }
}
