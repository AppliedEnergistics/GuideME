package guideme.guidebook.render;

import guideme.api.color.LightDarkMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuiSprite {
    private static final Logger LOG = LoggerFactory.getLogger(GuiSprite.class);

    private final ResourceLocation id;
    private volatile CachedState cachedState;

    public GuiSprite(ResourceLocation id) {
        this.id = id;
    }

    public ResourceLocation get(LightDarkMode mode) {
        var state = getOrCreateCachedState();
        return mode == LightDarkMode.DARK_MODE ? state.darkId : id;
    }

    public GuiSpriteScaling spriteScaling() {
        var state = getOrCreateCachedState();
        return state.spriteScaling;
    }

    public TextureAtlasSprite atlasSprite(LightDarkMode mode) {
        var state = getOrCreateCachedState();
        return mode == LightDarkMode.LIGHT_MODE ? state.sprite : state.darkSprite;
    }

    private CachedState getOrCreateCachedState() {
        // Double-checked locking
        var result = cachedState;
        if (result != null) {
            return result;
        }

        synchronized (this) {
            var guiSprites = Minecraft.getInstance().getGuiSprites();

            var sprite = guiSprites.getSprite(id);
            var spriteScaling = guiSprites.getSpriteScaling(sprite);
            var darkId = id.withSuffix("_darkmode");
            var darkSprite = guiSprites.getSprite(darkId);

            if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                // Use the light sprite as the dark sprite
                darkId = id;
                darkSprite = sprite;
            } else {
                // Ensure people avoid the foot-gun of using different scaling
                var darkScaling = guiSprites.getSpriteScaling(darkSprite);
                if (!darkScaling.equals(spriteScaling)) {
                    LOG.warn(
                            "Dark-mode sprite {} uses different sprite-scaling from the light-mode version. Please ensure the same .mcmeta file content is used.",
                            darkId);
                }
            }

            cachedState = new CachedState(
                    sprite,
                    darkId,
                    darkSprite,
                    spriteScaling);
            return cachedState;
        }
    }

    private record CachedState(
            TextureAtlasSprite sprite,
            ResourceLocation darkId,
            TextureAtlasSprite darkSprite,
            // We always use the same sprite scaling for light and dark mode
            GuiSpriteScaling spriteScaling) {
    }

    void reset() {
        cachedState = null;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
