package guideme.document.block.recipes;

import guideme.document.DefaultStyles;
import guideme.document.LytSize;
import guideme.document.block.AlignItems;
import guideme.document.block.LytBlock;
import guideme.document.block.LytGuiSprite;
import guideme.document.block.LytHBox;
import guideme.document.block.LytParagraph;
import guideme.document.block.LytSlotGrid;
import guideme.document.block.LytVBox;
import guideme.internal.util.Platform;
import guideme.render.GuiAssets;
import guideme.render.RenderContext;
import guideme.scene.LytItemImage;
import guideme.siteexport.ExportableResourceProvider;
import guideme.siteexport.ResourceExporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Provides an easy way to define recipes that are rendered as follows:
 * <ul>
 *     <li>Title bar with optional icon.</li>
 *     <li>A grid of slots on the left side, representing the recipe inputs.</li>
 *     <li>A grid of slots on the right side, representing the recipe outputs.</li>
 *     <li>A big arrow pointing left to right between the two grids.</li>
 * </ul>
 * <p/>
 * Use the {@link #builder()} method to get started.
 */
public class LytStandardRecipeBox<T extends Recipe<?>> extends LytVBox implements ExportableResourceProvider {
    private final RecipeHolder<? extends T> holder;

    @ApiStatus.Internal
    LytStandardRecipeBox(RecipeHolder<? extends T> holder) {
        this.holder = holder;
    }

    public RecipeHolder<? extends T> getRecipe() {
        return holder;
    }

    @Override
    public void render(RenderContext context) {
        context.renderPanel(getBounds());
        super.render(context);
    }

    @Override
    public void exportResources(ResourceExporter exporter) {
        exporter.referenceRecipe(this.holder);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private LytBlock icon;
        private LytParagraph title = new LytParagraph();
        private LytSlotGrid input;
        private LytSlotGrid output;

        private Builder() {
            this.title.setStyle(DefaultStyles.CRAFTING_RECIPE_TYPE);
        }

        public <T extends Recipe<?>> LytStandardRecipeBox<T> build(RecipeHolder<T> recipe) {
            var box = new LytStandardRecipeBox<>(recipe);
            build(box);
            return box;
        }

        public Builder icon(ItemLike workbench) {
            return icon(workbench.asItem().getDefaultInstance());
        }

        public Builder icon(ItemStack workbench) {
            var itemImage = new LytItemImage();
            itemImage.setScale(0.5f);
            itemImage.setItem(workbench);
            this.icon = itemImage;
            return this;
        }

        public Builder title(String title) {
            this.title.appendText(title);
            return this;
        }

        public Builder input(Ingredient ingredient) {
            this.input = LytSlotGrid.row(List.of(ingredient), false);
            return this;
        }

        public Builder input(LytSlotGrid grid) {
            this.input = grid;
            return this;
        }

        public Builder output(LytSlotGrid grid) {
            this.output = grid;
            return this;
        }

        public Builder outputFromResultOf(RecipeHolder<?> recipe) {
            var resultItem = recipe.value().getResultItem(Platform.getClientRegistryAccess());
            if (!resultItem.isEmpty()) {
                output = new LytSlotGrid(1, 1);
                output.setItem(0, 0, resultItem);
            }
            return this;
        }

        @ApiStatus.Internal
        <T extends Recipe<?>> void build(LytStandardRecipeBox<T> box) {
            box.setGap(2);
            box.setPadding(5);

            var titleRow = new LytHBox();
            titleRow.setAlignItems(AlignItems.CENTER);
            if (icon != null) {
                titleRow.append(icon);
            }
            if (!title.isEmpty()) {
                titleRow.append(title);
            }
            titleRow.setGap(2);

            box.append(titleRow);

            var gridRow = new LytHBox();
            gridRow.setGap(2);
            gridRow.setAlignItems(AlignItems.CENTER);
            if (input != null) {
                gridRow.append(input);
            }
            if (input != null || output != null) {
                gridRow.append(new LytGuiSprite(GuiAssets.ARROW, new LytSize(24, 17)));
            }
            if (output != null) {
                gridRow.append(output);
            }
            box.append(gridRow);
        }
    }
}
