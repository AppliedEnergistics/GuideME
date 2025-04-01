package guideme.internal.siteexport;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import java.util.function.IntUnaryOperator;

final class TextureDownloader {
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

        try (var downloadBuffer = RenderSystem.getDevice().createBuffer(() -> "Texture output buffer",
                BufferType.PIXEL_PACK, BufferUsage.STATIC_READ, byteSize)) {
            CommandEncoder commandencoder = RenderSystem.getDevice().createCommandEncoder();

            commandencoder.copyTextureToBuffer(texture, downloadBuffer, 0, () -> {
            }, 0);

            try (var readView = commandencoder.readBuffer(downloadBuffer)) {
                if (flipY) {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int pixel = readView.data().getInt((x + y * width) * texture.getFormat().pixelSize());
                            nativeImage.setPixelABGR(x, height - y - 1, pixelOp.applyAsInt(pixel));
                        }
                    }
                } else {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int pixel = readView.data().getInt((x + y * width) * texture.getFormat().pixelSize());
                            nativeImage.setPixelABGR(x, y, pixelOp.applyAsInt(pixel));
                        }
                    }
                }
            }
        }
    }
}
