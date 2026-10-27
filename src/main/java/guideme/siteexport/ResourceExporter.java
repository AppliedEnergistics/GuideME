package guideme.siteexport;

import java.nio.file.Path;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface ResourceExporter {
    default void referenceItem(ItemLike item) {
        referenceItem(new ItemStack(item));
    }

    void referenceItem(ItemStack stack);

    void referenceFluid(Fluid fluid);

    String exportTexture(ResourceLocation texture);

    /**
     * Exports a texture by id and returns the relative path to refer to it from other content that is being exported.
     */
    String exportTexture(ResourceLocation texture, NamedPixelModifier pixelOp);

    /**
     * Exports an atlas sprite and returns the relative path to refer to it from other content that is being exported.
     */
    String exportSprite(TextureAtlasSprite texture);

    /**
     * Exports a sprite by id and returns the relative path to refer to it from other content that is being exported.
     */
    String exportSprite(TextureAtlasSprite texture, NamedPixelModifier pixelOp);

    /**
     * @return The new resource id after applying cache busting.
     */
    Path copyResource(ResourceLocation id);

    Path getPathForWriting(ResourceLocation assetId);

    /**
     * Generates a resource location for a page specific resource.
     */
    Path getPageSpecificPathForWriting(String suffix);

    @Nullable
    ResourceLocation getCurrentPageId();

    Path getOutputFolder();

    default String getPathRelativeFromOutputFolder(Path p) {
        return "/" + getOutputFolder().relativize(p).toString().replace('\\', '/');
    }

    /**
     * Generates a resource location for a page specific resource.
     */
    ResourceLocation getPageSpecificResourceLocation(String suffix);

    void referenceRecipe(RecipeHolder<?> recipe);
}
