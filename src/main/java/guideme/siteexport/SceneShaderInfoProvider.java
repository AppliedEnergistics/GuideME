package guideme.siteexport;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionPoint;
import org.jetbrains.annotations.Nullable;

/**
 * Allows mods to describe how their custom render pipelines should be rendered by the web viewer when game scenes are
 * exported. Pipelines that no provider handles use {@link SceneShaderInfo#fromPipeline}, which only understands the
 * conventions of the vanilla core shaders.
 */
public interface SceneShaderInfoProvider extends Extension {
    ExtensionPoint<SceneShaderInfoProvider> EXTENSION_POINT = new ExtensionPoint<>(SceneShaderInfoProvider.class);

    /**
     * @return The shader info for the given pipeline, or null if this provider does not handle it.
     */
    @Nullable
    SceneShaderInfo getShaderInfo(RenderPipeline pipeline);
}
