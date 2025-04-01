package guideme.hooks.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTexture;
import guideme.hooks.RenderToTextureHooks;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GlCommandEncoder.class)
public class GlCommandEncoderMixin {

    @WrapMethod(method = "createRenderPass(Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;")
    public RenderPass overrideRenderTargets(GpuTexture colorBuffer,
            OptionalInt clearColor,
            @Nullable GpuTexture depthBuffer,
            OptionalDouble clearDepth,
            Operation<RenderPass> original) {
        colorBuffer = RenderToTextureHooks.replaceColorTarget(colorBuffer);
        depthBuffer = RenderToTextureHooks.replaceDepthTarget(depthBuffer);

        return original.call(colorBuffer, clearColor, depthBuffer, clearDepth);
    }

    @WrapMethod(method = "clearColorTexture")
    public void overrideRenderTargetForClearColorTexture(GpuTexture colorBuffer, int color, Operation<Void> original) {
        colorBuffer = RenderToTextureHooks.replaceColorTarget(colorBuffer);
        original.call(colorBuffer, color);
    }

    @WrapMethod(method = "clearColorAndDepthTextures")
    public void overrideClearColorAndDepthTextures(GpuTexture colorBuffer, int color, GpuTexture depthBuffer,
            double depthValue, Operation<Void> original) {
        colorBuffer = RenderToTextureHooks.replaceColorTarget(colorBuffer);
        depthBuffer = RenderToTextureHooks.replaceDepthTarget(depthBuffer);
        original.call(colorBuffer, color, depthBuffer, depthValue);
    }

    @WrapMethod(method = "clearDepthTexture")
    public void overrideClearDepthTexture(GpuTexture depthBuffer, double depthValue, Operation<Void> original) {
        depthBuffer = RenderToTextureHooks.replaceDepthTarget(depthBuffer);
        original.call(depthBuffer, depthValue);
    }

    @WrapMethod(method = "clearStencilTexture")
    public void overrideClearStencilTexture(GpuTexture depthBuffer, int stencilValue, Operation<Void> original) {
        depthBuffer = RenderToTextureHooks.replaceDepthTarget(depthBuffer);
        original.call(depthBuffer, stencilValue);
    }
}
