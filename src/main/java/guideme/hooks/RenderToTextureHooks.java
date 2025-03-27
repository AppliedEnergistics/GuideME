package guideme.hooks;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;

public final class RenderToTextureHooks {

    public static RenderTarget targetOverride;

    public static GpuTexture replaceColorTarget(GpuTexture colorTarget) {
        if (targetOverride != null && colorTarget == Minecraft.getInstance().getMainRenderTarget().getColorTexture()) {
            return targetOverride.getColorTexture();
        }
        return colorTarget;
    }

    public static GpuTexture replaceDepthTarget(GpuTexture depthTarget) {
        if (targetOverride != null && depthTarget == Minecraft.getInstance().getMainRenderTarget().getDepthTexture()) {
            return targetOverride.getDepthTexture();
        }
        return depthTarget;
    }

}
