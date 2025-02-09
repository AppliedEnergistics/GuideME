package guideme.internal;

import java.util.Objects;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.TextureAtlasHolder;
import net.minecraft.resources.ResourceLocation;

class GuiSpriteAtlas extends TextureAtlasHolder {
    public GuiSpriteAtlas(TextureManager textureManager, ResourceLocation location,
            ResourceLocation atlasInfoLocation) {
        super(textureManager, location, atlasInfoLocation);
    }

    public TextureAtlas getTextureAtlas() {
        return Objects.requireNonNull(textureAtlas, "textureAtlas");
    }
}
