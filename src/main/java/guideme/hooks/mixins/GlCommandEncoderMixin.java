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
        depthBuffer = RenderToTextureHooks.replaceDepthTest(depthBuffer);

        return original.call(colorBuffer, clearColor, depthBuffer, clearDepth);
    }

}
