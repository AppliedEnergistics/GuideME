package guideme.render;

import com.mojang.blaze3d.platform.NativeImage;
import guideme.document.LytSize;
import guideme.internal.GuideME;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A texture that is used in the context of a single guide page and is automatically cleared from texture memory when
 * the guide page it was last used on is closed.
 */
public class GuidePageTexture {

    public static GuidePageTexture missing() {
        return new GuidePageTexture(GuideME.makeId("missing"), null);
    }

    private static final Logger LOG = LoggerFactory.getLogger(GuidePageTexture.class);

    // Textures in use by the current page, keyed by image id. Multiple instances may share the same image id
    // (i.e. the same image used twice on a page), and since the registered texture id is derived from the image id,
    // registering a second texture would close the first one while it may still be referenced by pending draws.
    private static final Map<Identifier, Identifier> usedTextures = new HashMap<>();

    private final Identifier id;

    private final ByteBuffer imageContent;

    private final LytSize size;

    private GuidePageTexture(Identifier id, byte @Nullable [] imageContent) {
        this.id = Objects.requireNonNull(id, "id");
        if (imageContent == null) {
            this.imageContent = null;
            this.size = new LytSize(32, 32);
        } else {
            this.imageContent = ByteBuffer.allocateDirect(imageContent.length);
            this.imageContent.put(imageContent).flip();

            var xOut = new int[1];
            var yOut = new int[1];
            var compOut = new int[1];
            if (!STBImage.stbi_info_from_memory(this.imageContent, xOut, yOut, compOut)) {
                throw new IllegalArgumentException(
                        "Couldn't determine size of image " + id + ": " + STBImage.stbi_failure_reason());
            }

            // Try to determine the size
            this.size = new LytSize(xOut[0], yOut[0]);
        }
    }

    public Identifier getId() {
        return id;
    }

    public LytSize getSize() {
        return size;
    }

    public static GuidePageTexture load(Identifier id, byte[] imageContent) {
        try {
            return new GuidePageTexture(id, imageContent);
        } catch (Exception e) {
            LOG.error("Failed to get image {}: {}", id, e.toString());
            return missing();
        }
    }

    public Identifier use() {
        if (imageContent == null) {
            return MissingTextureAtlasSprite.getLocation();
        }

        return usedTextures.computeIfAbsent(id, ignored -> {
            try {
                var nativeImage = NativeImage.read(imageContent);
                var textureId = GuideME.makeId("guidepage/" + id.getNamespace() + "/" + id.getPath());
                var texture = new DynamicTexture(textureId::toString, nativeImage);
                Minecraft.getInstance().getTextureManager().register(textureId, texture);
                return textureId;
            } catch (IOException e) {
                LOG.error("Failed to read image {}: {}", id, e.toString());
                return MissingTextureAtlasSprite.getLocation();
            }
        });
    }

    public static void releaseUsedTextures() {
        for (var textureId : usedTextures.values()) {
            if (!textureId.equals(MissingTextureAtlasSprite.getLocation())) {
                Minecraft.getInstance().getTextureManager().release(textureId);
            }
        }
        usedTextures.clear();
    }
}
