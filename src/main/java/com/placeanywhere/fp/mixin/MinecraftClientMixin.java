package com.placeanywhere.fp.mixin;

import com.placeanywhere.fp.FreePlacementMode;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自由放置模组的 Mixin：注入 Minecraft 的左键/右键处理。
 *
 * 优先级设为 1500（高于 core 的 1000），确保在自由放置模式激活时先于 core 处理。
 * 自由放置模式未激活时直接放行，不影响 core 的自由方块交互逻辑。
 */
@Mixin(value = Minecraft.class, priority = 1500)
public class MinecraftClientMixin {

    private static final long USE_COOLDOWN_MS = 200L;
    private static long lastUseMs = 0L;

    /** 左键：自由放置模式激活时改为放置方块。 */
    @Inject(method = "continueBreakingBlock", at = @At("HEAD"), cancellable = true)
    private void freeplacement$onContinueBreakingBlock(boolean breaking, CallbackInfo ci) {
        if (!FreePlacementMode.isActive()) return;
        Minecraft self = (Minecraft) (Object) this;
        if (self.player == null || self.player.level() == null) return;

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

    /** 右键：自由放置模式激活时也放置方块（和左键一样），阻止原版放置普通方块。
     *  加 200ms 冷却防止按住右键时连续放置。 */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void freeplacement$onStartUseItem(CallbackInfo ci) {
        if (!FreePlacementMode.isActive()) return;
        Minecraft self = (Minecraft) (Object) this;
        if (self.player == null || self.player.level() == null) return;
        if (!self.options.keyUse.isDown()) return;
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
