package guideme.render;

import guideme.color.LightDarkMode;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
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
            var guiSprites = Minecraft.getInstance().getModelManager().getAtlas(GuiAssets.GUI_SPRITE_ATLAS);

            var sprite = guiSprites.getSprite(id);
            var spriteScaling = getSpriteScaling(id);
            var darkId = id.withSuffix("_darkmode");
            var darkSprite = guiSprites.getSprite(darkId);

            if (darkSprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                // Use the light sprite as the dark sprite
                darkId = id;
                darkSprite = sprite;
            } else {
                // Ensure people avoid the foot-gun of using different scaling
                var darkScaling = getSpriteScaling(id);
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

    private GuiSpriteScaling getSpriteScaling(ResourceLocation id) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(
                id.withPrefix("textures/gui/sprites/").withSuffix(".png")).orElse(null);

        if (resource == null) {
            return GuiSpriteScaling.DEFAULT;
        }

        ResourceMetadata metadata;
        try {
            metadata = resource.metadata();
        } catch (IOException e) {
            LOG.error("Failed to load metadata for {}", id, e);
            return GuiSpriteScaling.DEFAULT;
        }

        try {
            return metadata.getSection(GuiSpriteScaling.SERIALIZER).orElse(GuiSpriteScaling.DEFAULT);
        } catch (Exception e) {
            LOG.error("Failed to read sprite scaling for {}", id, e);
            return GuiSpriteScaling.DEFAULT;
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
