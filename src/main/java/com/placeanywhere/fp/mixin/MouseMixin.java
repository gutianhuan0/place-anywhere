package com.placeanywhere.fp.mixin;

import com.placeanywhere.fp.FreePlacementMode;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void freeplacement$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (FreePlacementMode.isActive() && vertical != 0) {
            FreePlacementMode.onScroll(vertical);
            ci.cancel();
        }
    }
}
