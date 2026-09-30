package guideme.internal.siteexport;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import guideme.internal.GuideME;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.renderer.RenderPipelines;

public final class TextureDownloader {
    /**
     * The base pipeline is used by RenderTarget#blitAndBlendToTexture, but we require the alpha channel to be copied
     * as-is.
     */
    public static final RenderPipeline COPY_BLIT = RenderPipelines.ENTITY_OUTLINE_BLIT.toBuilder()
            .withLocation(GuideME.makeId("copy_blit"))
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ZERO)))
            .build();

    private static final long DOWNLOAD_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(10);

    private TextureDownloader() {
    }

    /**
     * The download callback only runs once the GPU completed the commands of the current submit, which normally ends
     * with the frame. Since we need the data immediately, we submit early and wait for the GPU to catch up.
     */
    private static void awaitDownload(CommandEncoder commandEncoder) {
        try (var fence = commandEncoder.createFence()) {
            commandEncoder.submit();
            if (!fence.awaitCompletion(DOWNLOAD_TIMEOUT_NS)) {
                throw new IllegalStateException("Timed out waiting for texture download");
            }
        }

        RenderSystem.executePendingTasks();
    }

    public static NativeImage downloadTexture(GpuTexture texture, int mipLevel, IntUnaryOperator pixelOp) {
        var nativeImage = new NativeImage(texture.getWidth(mipLevel), texture.getHeight(mipLevel), false);
        downloadTexture(texture, mipLevel, pixelOp, nativeImage);
        return nativeImage;
    }

    public static void downloadTexture(GpuTexture texture, int mipLevel, IntUnaryOperator pixelOp,
            NativeImage nativeImage) {
        downloadTexture(texture, mipLevel, pixelOp, nativeImage, false);
    }

    public static void downloadTexture(GpuTexture texture, int mipLevel, IntUnaryOperator pixelOp,
            NativeImage nativeImage, boolean flipY) {
        var width = texture.getWidth(mipLevel);
        var height = texture.getHeight(mipLevel);

        if (nativeImage.getWidth() != width || nativeImage.getHeight() != height) {
            throw new IllegalArgumentException("Image dimensions must match that of texture");
        }

        // Load the framebuffer back into CPU memory
        int byteSize = texture.getFormat().blockSize() * width * height;

        var device = RenderSystem.getDevice();
        try (var downloadBuffer = device.createBuffer(() -> "Texture output buffer",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, byteSize)) {
            var commandencoder = device.createCommandEncoder();

            Runnable saveImage = () -> {
                try (var mappedView = downloadBuffer.map(true, false)) {
                    if (flipY) {
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                int pixel = mappedView.data().getInt((x + y * width) * texture.getFormat().blockSize());
                                nativeImage.setPixelABGR(x, height - y - 1, pixelOp.applyAsInt(pixel));
                            }
                        }
                    } else {
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                int pixel = mappedView.data().getInt((x + y * width) * texture.getFormat().blockSize());
                                nativeImage.setPixelABGR(x, y, pixelOp.applyAsInt(pixel));
                            }
                        }
                    }
                }
            };

            // We might need an intermediate buffer
            if ((texture.usage() & GpuBuffer.USAGE_COPY_SRC) == 0) {
                // We blit it to a temporary framebuffer and then copy that
                try (var tempFramebuffer = device.createTexture(() -> "GuideME temp color copy",
                        GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, width,
                        height, 1, 1);
                        var tempFramebufferView = device.createTextureView(tempFramebuffer)) {

                    try (var pass = commandencoder.createRenderPass(() -> "Blit texture", tempFramebufferView,
                            Optional.empty());
                            var view = device.createTextureView(texture)) {
                        pass.setPipeline(COPY_BLIT);
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.bindTexture("InSampler", view,
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                        // Full-screen triangle, same as RenderTarget#blitAndBlendToTexture
                        pass.draw(3, 1, 0, 0);
                    }

                    commandencoder.copyTextureToBuffer(tempFramebuffer, downloadBuffer, 0, saveImage, mipLevel);
                }
            } else {
                commandencoder.copyTextureToBuffer(texture, downloadBuffer, 0, saveImage, mipLevel);
            }

            awaitDownload(commandencoder);
        }
    }
}
