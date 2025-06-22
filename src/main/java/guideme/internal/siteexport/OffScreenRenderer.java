package guideme.internal.siteexport;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import guideme.internal.util.Platform;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public class OffScreenRenderer implements AutoCloseable {
    private final NativeImage nativeImage;
    private final TextureTarget fb;
    private final GpuDevice device;
    private final CommandEncoder commandEncoder;
    private final GpuTexture colorTexture;
    private final GpuTexture depthTexture;
    private final PerspectiveProjectionMatrixBuffer projMatrixBuffer;
    private final Lighting lighting = new Lighting();

    public OffScreenRenderer(int width, int height) {
        nativeImage = new NativeImage(width, height, false);
        fb = new TextureTarget("GuideME OSR", width, height, true /* with depth */, false /* with stencil */);

        device = RenderSystem.getDevice();
        commandEncoder = device.createCommandEncoder();

        projMatrixBuffer = new PerspectiveProjectionMatrixBuffer("GuideME OffScreen");

        colorTexture = Objects.requireNonNull(fb.getColorTexture(), "colorTexture");
        var colorTextureView = Objects.requireNonNull(fb.getColorTextureView(), "colorTexture");
        depthTexture = Objects.requireNonNull(fb.getDepthTexture(), "depthTexture");
        var depthTextureView = Objects.requireNonNull(fb.getDepthTextureView(), "depthTexture");
        commandEncoder.createRenderPass(() -> "GuideME OffScreen", colorTextureView, OptionalInt.of(0),
                depthTextureView, OptionalDouble.of(1.0)).close();
    }

    @Override
    public void close() {
        nativeImage.close();
        fb.destroyBuffers();
        projMatrixBuffer.close();
        lighting.close();
    }

    public byte[] captureAsPng(Runnable r) {
        renderToBuffer(r);

        try {
            return Platform.exportAsPng(nativeImage);
        } catch (IOException e) {
            throw new RuntimeException("failed to encode image as PNG", e);
        }
    }

    public void captureAsPng(Runnable r, Path path) throws IOException {
        renderToBuffer(r);

        nativeImage.writeToFile(path);
    }

    public boolean isAnimated(Collection<TextureAtlasSprite> sprites) {
        return sprites.stream().anyMatch(s -> s.contents().animatedTexture != null);
    }

    public byte[] captureAsWebp(Runnable r, Collection<TextureAtlasSprite> sprites, WebPExporter.Format format) {
        var animatedSprites = sprites.stream()
                .filter(sprite -> sprite.contents().animatedTexture != null)
                .toList();

        // Not animated
        if (animatedSprites.isEmpty()) {
            return captureAsPng(r);
        }

        // This is an oversimplification. Not all animated textures may have the same loop frequency
        // But the greatest common divisor could be so inconvenient that we're essentially looping forever.
        var maxTime = animatedSprites.stream()
                .mapToInt(s -> s.contents().animatedTexture.frames.stream().mapToInt(SpriteContents.FrameInfo::time)
                        .sum())
                .max()
                .orElse(0);

        var textureManager = Minecraft.getInstance().getTextureManager();

        var tickers = animatedSprites.stream()
                .collect(Collectors.groupingBy(TextureAtlasSprite::atlasLocation))
                .entrySet().stream().collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().stream().map(TextureAtlasSprite::createTicker).toList()));
        for (var sprite : animatedSprites) {
            var atlas = textureManager.getTexture(sprite.atlasLocation());
            sprite.uploadFirstFrame(atlas.getTexture());
        }

        int width = nativeImage.getWidth();
        int height = nativeImage.getHeight();

        try (var webpWriter = new WebPExporter(width, height, format)) {
            for (var i = 0; i < maxTime; i++) {
                // Bind all animated textures to their corresponding frames
                for (var entry : tickers.entrySet()) {
                    var texture = textureManager.getTexture(entry.getKey());
                    for (var ticker : entry.getValue()) {
                        ticker.tickAndUpload(texture.getTexture());
                    }
                }

                renderToBuffer(r);

                webpWriter.writeFrame(i, nativeImage);
            }

            return webpWriter.finish();
        }
    }

    private void renderToBuffer(Runnable r) {
        var minecraft = Minecraft.getInstance();

        commandEncoder.clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1.0);
        var previousRt = minecraft.mainRenderTarget;
        minecraft.mainRenderTarget = fb;

        try {
            r.run();
        } finally {
            minecraft.mainRenderTarget = previousRt;
        }
        TextureDownloader.downloadTexture(colorTexture, 0, IntUnaryOperator.identity(), nativeImage, true);
    }

}
