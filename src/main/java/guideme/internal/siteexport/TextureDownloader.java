package guideme.internal.siteexport;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.opengl.GL11;

public final class TextureDownloader {
    private TextureDownloader() {
    }

    /**
     * Downloads a texture from the GPU. The pixel operator receives and returns colors in the ABGR format used by
     * {@link NativeImage}.
     */
    public static NativeImage downloadTexture(AbstractTexture texture, int mipLevel, IntUnaryOperator pixelOp) {
        texture.bind();
        int[] intResult = new int[1];
        GL11.glGetTexLevelParameteriv(GL11.GL_TEXTURE_2D, mipLevel, GL11.GL_TEXTURE_WIDTH, intResult);
        int width = intResult[0];
        GL11.glGetTexLevelParameteriv(GL11.GL_TEXTURE_2D, mipLevel, GL11.GL_TEXTURE_HEIGHT, intResult);
        int height = intResult[0];

        var nativeImage = new NativeImage(width, height, false);
        nativeImage.downloadTexture(mipLevel, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                nativeImage.setPixelRGBA(x, y, pixelOp.applyAsInt(nativeImage.getPixelRGBA(x, y)));
            }
        }
        return nativeImage;
    }
}
