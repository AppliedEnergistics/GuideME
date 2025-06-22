package guideme.scene.export;

import com.mojang.blaze3d.textures.FilterMode;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RenderTypeIntrospection {
    private static final Logger LOG = LoggerFactory.getLogger(RenderTypeIntrospection.class);

    private RenderTypeIntrospection() {
    }

    public static List<Sampler> getSamplers(RenderType type) {
        if (!(type instanceof RenderType.CompositeRenderType compositeRenderType)) {
            return List.of();
        }

        var state = compositeRenderType.state;
        if (state.textureState instanceof RenderStateShard.TextureStateShard textureShard) {
            if (textureShard.texture.isPresent()) {
                var textureId = textureShard.texture.get();
                var texture = Minecraft.getInstance().getTextureManager().getTexture(textureId).getTexture();
                var blur = texture.minFilter != FilterMode.NEAREST;

                return List.of(new Sampler(textureId, blur, texture.useMipmaps));
            } else {
                LOG.warn("Render type {} is using dynamic texture", type);
            }
        } else if (state.textureState != RenderStateShard.NO_TEXTURE) {
            LOG.warn("Cannot handle texturing of render-type {}", type);
        }

        return List.of();
    }

    public record Sampler(ResourceLocation texture, boolean blur, boolean mipmap) {
    }
}
