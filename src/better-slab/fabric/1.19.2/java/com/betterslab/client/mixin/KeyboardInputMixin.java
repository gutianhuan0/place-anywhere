package com.betterslab.client.mixin;

import com.betterslab.client.BetterSlabClient;
import com.betterslab.client.ModKeyBindings;
import com.betterslab.config.BetterSlabConfig;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 KeyboardInput.tick()，在 Alt+半砖 按住时清零移动输入。
 * 不清零 sneaking/jumping——保留潜行和跳跃能力。
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void preventMovementOnAlt(boolean slowDown, CallbackInfo ci) {
        if (BetterSlabConfig.get().preventMovement && BetterSlabClient.isAltHeldWithSlab()) {
            this.movementForward = 0.0f;
            this.movementSideways = 0.0f;
            this.pressingForward = false;
            this.pressingBack = false;
            this.pressingLeft = false;
            this.pressingRight = false;
            // Alt+Space 时清零跳跃（阻止原版跳跃），保留潜行
            if (ModKeyBindings.isMovementKeyPressed(ModKeyBindings.KEY_SPACE)) {
                this.jumping = false;
            }
            // 保留 sneaking：Alt+Shift 既选择下半砖模式又保持潜行
        }
    }
}
