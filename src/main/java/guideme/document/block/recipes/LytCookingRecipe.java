package guideme.document.block.recipes;

import guideme.document.DefaultStyles;
import guideme.document.LytRect;
import guideme.document.block.LytSlot;
import guideme.internal.GuidebookText;
import guideme.internal.util.Platform;
import guideme.layout.LayoutContext;
import guideme.render.GuiAssets;
import guideme.render.RenderContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Blocks;

@Deprecated(forRemoval = true)
public class LytCookingRecipe extends LytRecipeBox {
    private final ItemStack icon;

    private final Component title;

    private final AbstractCookingRecipe recipe;

    private final LytSlot inputSlot;

    private final LytSlot resultSlot;

    public LytCookingRecipe(ItemStack icon,
            Component title,
            RecipeHolder<? extends AbstractCookingRecipe> holder) {
        super(holder);
        this.icon = icon;
        this.title = title;
        this.recipe = holder.value();
        setPadding(5);
        paddingTop = 15;

        append(inputSlot = new LytSlot(recipe.getIngredients().get(0)));
        append(resultSlot = new LytSlot(recipe.getResultItem(Platform.getClientRegistryAccess())));
    }

    @Override
    protected LytRect computeBoxLayout(LayoutContext context, int x, int y, int availableWidth) {
        var inputBounds = inputSlot.layout(
                context,
                x,
                y,
                availableWidth);

        var resultBounds = resultSlot.layout(
                context,
                inputBounds.right() + 28,
                y,
                availableWidth);
        return LytRect.union(inputBounds, resultBounds);
    }

    @Override
    public void render(RenderContext context) {
        context.renderPanel(getBounds());

        context.renderItem(
                icon,
                bounds.x() + paddingLeft,
                bounds.y() + 4,
                8,
                8);
        context.renderText(
                title.getString(),
                DefaultStyles.CRAFTING_RECIPE_TYPE.mergeWith(DefaultStyles.BASE_STYLE),
                bounds.x() + paddingLeft + 10,
                bounds.y() + 4);

        context.fillIcon(
                new LytRect(bounds.right() - 25 - 24, bounds.y() + 10 + (bounds.height() - 27) / 2, 24, 17),
                GuiAssets.ARROW);

        super.render(context);
    }

    public static LytCookingRecipe createSmelting(RecipeHolder<SmeltingRecipe> recipe) {
        return new LytCookingRecipe(
                Blocks.FURNACE.asItem().getDefaultInstance(),
                GuidebookText.Smelting.text(),
                recipe);
    }

    public static LytCookingRecipe createBlasting(RecipeHolder<BlastingRecipe> recipe) {
        return new LytCookingRecipe(
                Blocks.BLAST_FURNACE.asItem().getDefaultInstance(),
                GuidebookText.Blasting.text(),
                recipe);
    }
}
