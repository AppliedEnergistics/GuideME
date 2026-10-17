package guideme.scene.export;

import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.ArrayList;
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

    /**
     * {@return the textures bound to Sampler0, Sampler1 and so on, in order} Most shaders only use Sampler0, but some
     * use more (i.e. the end portal uses Sampler1 for its layers).
     */
    public static List<Sampler> getSamplers(RenderType type) {
        var result = new ArrayList<Sampler>();
        for (int i = 0;; i++) {
            var binding = type.state.textures.get("Sampler" + i);
            if (binding == null) {
                break;
            }
            var textureId = binding.location();
            var texture = Minecraft.getInstance().getTextureManager().getTexture(textureId).getTexture();
            var sampler = binding.sampler().get();
            // The web viewer uses this for magnification, so base it on the mag filter. The block atlas samplers
            // use LINEAR only for minification (mipmapping) and NEAREST for magnification.
            var blur = sampler != null && sampler.getMagFilter() != FilterMode.NEAREST;
            var useMipmaps = texture.getMipLevels() > 1;

            result.add(new Sampler(textureId, blur, useMipmaps));
        }
        return result;
    }

    public record Sampler(Identifier texture, boolean blur, boolean mipmap) {
    }
}
