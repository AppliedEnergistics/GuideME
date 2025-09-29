package guideme.internal.siteexport;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import guideme.internal.GuideME;
import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.renderer.RenderPipelines;

public final class TextureDownloader {
    /**
     * The base pipeline is used by RenderTarget#blitAndBlendToTexture, but we require the alpha channel to be copied
     * as-is.
     */
    public static final RenderPipeline COPY_BLIT = RenderPipelines.ENTITY_OUTLINE_BLIT.toBuilder()
            .withLocation(GuideME.makeId("copy_blit"))
            .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ZERO))
            .withColorWrite(true, true)
            .build();

    private TextureDownloader() {
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
        int byteSize = texture.getFormat().pixelSize() * width * height;

        var device = RenderSystem.getDevice();
        try (var downloadBuffer = device.createBuffer(() -> "Texture output buffer",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, byteSize)) {
            var commandencoder = device.createCommandEncoder();

            Runnable saveImage = () -> {
                try (var mappedView = commandencoder.mapBuffer(downloadBuffer, true, false)) {
                    if (flipY) {
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                int pixel = mappedView.data().getInt((x + y * width) * texture.getFormat().pixelSize());
                                nativeImage.setPixelABGR(x, height - y - 1, pixelOp.applyAsInt(pixel));
                            }
                        }
                    } else {
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                int pixel = mappedView.data().getInt((x + y * width) * texture.getFormat().pixelSize());
                                nativeImage.setPixelABGR(x, y, pixelOp.applyAsInt(pixel));
                            }
                        }
                    }
                }
            };

            // We might need an intermediate buffer
            if ((texture.usage() & GpuBuffer.USAGE_COPY_SRC) == 0) {
                var indicesStorage = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                var indicesBuffer = indicesStorage.getBuffer(6);

                // We blit it to a temporary framebuffer and then copy that
                try (var tempFramebuffer = device.createTexture(() -> "GuideME temp color copy",
                        GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.RGBA8, width,
                        height, 1, 1);
                        var tempFramebufferView = device.createTextureView(tempFramebuffer)) {
                    tempFramebuffer.setAddressMode(AddressMode.CLAMP_TO_EDGE);

                    try (var pass = commandencoder.createRenderPass(() -> "Blit texture", tempFramebufferView,
                            OptionalInt.empty());
                            var view = device.createTextureView(texture)) {
                        pass.setPipeline(COPY_BLIT);
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.setIndexBuffer(indicesBuffer, indicesStorage.type());
                        pass.bindSampler("InSampler", view);
                        pass.draw(0, 3);
                    }

                    commandencoder.copyTextureToBuffer(tempFramebuffer, downloadBuffer, 0, saveImage, mipLevel);
                }

                try (var fence = commandencoder.createFence()) {
                    fence.awaitCompletion(100000000);
                }

                RenderSystem.executePendingTasks();

            } else {
                commandencoder.copyTextureToBuffer(texture, downloadBuffer, 0, saveImage, mipLevel);

                try (var fence = commandencoder.createFence()) {
                    fence.awaitCompletion(100000000);
                }

                RenderSystem.executePendingTasks();
            }
        }
    }
}
