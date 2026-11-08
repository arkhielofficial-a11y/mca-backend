package com.mca.mixin;

import com.mca.client.seed.MCASeedOverride;
import net.minecraft.client.gui.screen.world.MoreOptionsDialog;
import net.minecraft.world.gen.GeneratorOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides world creation options immediately before Minecraft starts generation.
 */
@Mixin(MoreOptionsDialog.class)
public abstract class MoreOptionsDialogSeedMixin {
    @Inject(method = "getGeneratorOptions", at = @At("RETURN"), cancellable = true)
    private void mca$applyServerSeed(
        boolean confirm,
        CallbackInfoReturnable<GeneratorOptions> callback
    ) {
        GeneratorOptions overridden = MCASeedOverride.apply(callback.getReturnValue());
        callback.setReturnValue(overridden);
    }
}
