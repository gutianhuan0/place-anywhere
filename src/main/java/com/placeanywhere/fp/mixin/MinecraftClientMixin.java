package com.placeanywhere.fp.mixin;

import com.placeanywhere.fp.FreePlacementMode;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;







@Mixin(value = MinecraftClient.class, priority = 1500)
public class MinecraftClientMixin {

    private static final long USE_COOLDOWN_MS = 200L;
    private static long lastUseMs = 0L;


    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void freeplacement$onHandleBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (!FreePlacementMode.isActive()) return;
        MinecraftClient self = (MinecraftClient) (Object) this;
        if (self.player == null || self.player.getWorld() == null) return;

        if (!breaking) {
            FreePlacementMode.resetMineHandled();
            return;
        }
        if (FreePlacementMode.isMineHandled()) {
            ci.cancel();
            return;
        }
        if (FreePlacementMode.tryPlace()) {
            FreePlacementMode.setMineHandled();
            ci.cancel();
        }
    }



    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void freeplacement$onDoItemUse(CallbackInfo ci) {
        if (!FreePlacementMode.isActive()) return;
        MinecraftClient self = (MinecraftClient) (Object) this;
        if (self.player == null || self.player.getWorld() == null) return;
        if (!self.options.useKey.isPressed()) return;
        long now = System.currentTimeMillis();
        if (now - lastUseMs < USE_COOLDOWN_MS) {
            ci.cancel();
            return;
        }
        lastUseMs = now;
        if (FreePlacementMode.tryPlace()) {
            ci.cancel();
        }
    }
}
