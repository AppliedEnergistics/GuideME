package guideme.scene.export;

import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RenderTypeIntrospection {
    private static final Logger LOG = LoggerFactory.getLogger(RenderTypeIntrospection.class);

    private RenderTypeIntrospection() {
    }

    public static List<Sampler> getSamplers(RenderType type) {

        var binding = type.state.textures.get("Sampler0");
        if (binding != null) {
            var textureId = binding.location();
            var texture = Minecraft.getInstance().getTextureManager().getTexture(textureId).getTexture();
            var sampler = binding.sampler().get();
            // The web viewer uses this for magnification, so base it on the mag filter. The block atlas samplers
            // use LINEAR only for minification (mipmapping) and NEAREST for magnification.
            var blur = sampler != null && sampler.getMagFilter() != FilterMode.NEAREST;
            var useMipmaps = texture.getMipLevels() > 1;

            return List.of(new Sampler(textureId, blur, useMipmaps));
        }

        return List.of();
    }

    public record Sampler(Identifier texture, boolean blur, boolean mipmap) {
    }
}
