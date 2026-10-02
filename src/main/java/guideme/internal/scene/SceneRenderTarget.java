package guideme.internal.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * The color and depth target that guidebook scenes are currently being rendered into. Replaces the output texture
 * overrides that were removed from {@link RenderSystem} in Minecraft 26.3. If no target has been set, the main render
 * target of the game is used.
 */
@ApiStatus.Internal
public final class SceneRenderTarget {
    @Nullable
    private static GpuTextureView colorOverride;
    @Nullable
    private static GpuTextureView depthOverride;

    private SceneRenderTarget() {
    }

    /**
     * Sets the color and depth target for scene rendering until the returned scope is closed, which restores the
     * previous target.
     */
    public static Scope push(GpuTextureView color, GpuTextureView depth) {
        RenderSystem.assertOnRenderThread();
        var previousColor = colorOverride;
        var previousDepth = depthOverride;
        colorOverride = color;
        depthOverride = depth;
        return () -> {
            colorOverride = previousColor;
            depthOverride = previousDepth;
        };
    }

    public static GpuTextureView colorView() {
        if (colorOverride != null) {
            return colorOverride;
        }
        return Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
    }

    public static GpuTextureView depthView() {
        if (depthOverride != null) {
            return depthOverride;
        }
        return Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
    }

    /**
     * Opens a render pass that targets the current scene color and depth target.
     */
    public static RenderPass createRenderPass(Supplier<String> label) {
        return RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(label, colorView(), Optional.empty(), depthView(), OptionalDouble.empty());
    }

    /**
     * Prepares and renders all features submitted to the given storage into the current scene target.
     */
    public static void renderAllFeatures(FeatureRenderDispatcher dispatcher, SubmitNodeStorage nodes,
            Supplier<String> label) {
        try (var frame = dispatcher.prepareFrame(nodes);
                var pass = createRenderPass(label)) {
            RenderSystem.bindDefaultUniforms(pass);
            FeatureRenderDispatcher.renderAllFeatures(pass, frame);
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
