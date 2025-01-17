package guideme.guidebook.render;

import guideme.GuideME;
import guideme.api.color.LightDarkMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.GuiSpriteManager;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asset management
 */
public final class GuiAssets {
    private static final Logger LOG = LoggerFactory.getLogger(GuiAssets.class);

    private static final Map<ResourceLocation, GuiSprite> sprites = new ConcurrentHashMap<>();

    /**
     * @see GuiSpriteManager
     */
    public static final ResourceLocation GUI_SPRITE_ATLAS = ResourceLocation
            .withDefaultNamespace("textures/atlas/gui.png");

    public static final GuiSprite WINDOW_SPRITE = sprite("window");
    public static final GuiSprite INNER_BORDER_SPRITE = sprite("window_inner");
    public static final GuiSprite SLOT_BACKGROUND = sprite("slot");
    public static final GuiSprite SLOT_LARGE_BACKGROUND = sprite("slot_large");
    public static final GuiSprite SLOT_BORDER = sprite("slot_border");
    public static final GuiSprite SLOT = sprite("slot");
    public static final GuiSprite LARGE_SLOT = sprite("large_slot");
    public static final GuiSprite ARROW = sprite("recipe_arrow");

    private GuiAssets() {
    }

    public static GuiSprite sprite(String id) {
        return sprite(GuideME.makeId(id));
    }

    public static GuiSprite sprite(ResourceLocation id) {
        return sprites.computeIfAbsent(id, GuiSprite::new);
    }

    /**
     * Reset the cached information in the sprites.
     */
    public static void resetSprites() {
        LOG.debug("Resetting {} GUI sprites.", sprites.size());
        for (var sprite : sprites.values()) {
            sprite.reset();
        }
    }

    public static SpritePadding getWindowPadding() {
        return getNineSliceSprite(WINDOW_SPRITE, LightDarkMode.LIGHT_MODE).padding;
    }

    public static NineSliceSprite getNineSliceSprite(GuiSprite guiSprite, LightDarkMode mode) {
        if (!(guiSprite.spriteScaling() instanceof GuiSpriteScaling.NineSlice nineSlice)) {
            throw new IllegalStateException("Expected sprite " + guiSprite + " to be a nine-slice sprite!");
        }

        var sprite = guiSprite.atlasSprite(mode);

        var border = nineSlice.border();
        // Compute the delimiting U values *in the atlas* for the three slices.
        var u0 = sprite.getU0();
        var u1 = sprite.getU(border.left() / (float) nineSlice.width());
        var u2 = sprite.getU(1 - border.right() / (float) nineSlice.width());
        var u3 = sprite.getU1();
        // Compute the delimiting V values *in the atlas* for the three slices.
        var v0 = sprite.getV0();
        var v1 = sprite.getV(border.top() / (float) nineSlice.height());
        var v2 = sprite.getV(1 - border.bottom() / (float) nineSlice.height());
        var v3 = sprite.getV1();

        return new NineSliceSprite(
                sprite.atlasLocation(),
                new SpritePadding(border.left(), border.top(), border.right(), border.bottom()),
                new float[] { u0, u1, u2, u3, v0, v1, v2, v3 });
    }

    /**
     * @param uv First 4 U values delimiting the horizontal slices, then 4 V values delimiting the vertical slices.
     *           These values refer to the atlas.
     */
    public record NineSliceSprite(ResourceLocation atlasLocation,
            SpritePadding padding,
            float[] uv) {
    }
}
