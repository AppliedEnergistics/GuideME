package guideme.compiler.tags;

import guideme.compiler.PageCompiler;
import guideme.document.block.LytBlock;
import guideme.document.block.LytBlockContainer;
import guideme.internal.util.Platform;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/**
 * Shows a Recipe-Book-Like representation of the recipe needed to craft a given item.
 */
public class RecipeCompiler extends BlockTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("Recipe", "RecipeFor");
    }

    @Override
    protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
        // Find the recipe
        var recipeManager = Platform.getClientRecipeManager();
        if (recipeManager == null) {
            parent.appendError(compiler, "Cannot show recipe while not in-game", el);
            return;
        }

        if ("RecipeFor".equals(el.name())) {
            var itemAndId = MdxAttrs.getRequiredItemAndId(compiler, parent, el, "id");
            if (itemAndId == null) {
                return;
            }

            var id = itemAndId.getLeft();
            var item = itemAndId.getRight();

            for (var mapping : getMappings(compiler)) {
                var block = mapping.tryCreate(recipeManager, item);
                if (block != null) {
                    block.setSourceNode((MdAstNode) el);
                    parent.append(block);
                    return;
                }
            }

            // TODO This *can* be legit if there's no recipe due to datapacks
            parent.appendError(compiler, "Couldn't find recipe for " + id, el);
        } else {
            var recipeId = MdxAttrs.getRequiredId(compiler, parent, el, "id");
            if (recipeId == null) {
                return;
            }

            var recipe = recipeManager.byKey(recipeId).orElse(null);
            if (recipe == null) {
                parent.appendError(compiler, "Couldn't find recipe " + recipeId, el);
                return;
            }

            for (var mapping : getMappings(compiler)) {
                var block = mapping.tryCreate(recipe);
                if (block != null) {
                    block.setSourceNode((MdAstNode) el);
                    parent.append(block);
                    return;
                }
            }

            parent.appendError(compiler, "Couldn't find a handler for recipe " + recipeId, el);
        }
    }

    /**
     * Maps a recipe type to a factory that can create a layout block to display it.
     */
    private record RecipeTypeMapping<T extends Recipe<C>, C extends RecipeInput>(
            RecipeType<T> recipeType,
            Function<RecipeHolder<T>, LytBlock> factory) {
        @Nullable
        LytBlock tryCreate(RecipeManager recipeManager, Item resultItem) {
            var registryAccess = Platform.getClientRegistryAccess();

            // We try to find non-special recipes first then fall back to special
            List<RecipeHolder<T>> fallbackCandidates = new ArrayList<>();
            for (var recipe : recipeManager.byType(recipeType)) {
                if (recipe.value().isSpecial()) {
                    fallbackCandidates.add(recipe);
                    continue;
                }

                if (recipe.value().getResultItem(registryAccess).getItem() == resultItem) {
                    return factory.apply(recipe);
                }
            }

            for (var recipe : fallbackCandidates) {
                if (recipe.value().getResultItem(registryAccess).getItem() == resultItem) {
                    return factory.apply(recipe);
                }
            }

            return null;
        }

        @SuppressWarnings("unchecked")
        @Nullable
        LytBlock tryCreate(RecipeHolder<?> recipe) {
            if (recipeType == recipe.value().getType()) {
                return factory.apply((RecipeHolder<T>) recipe);
            }

            return null;
        }
    }

    private Iterable<RecipeTypeMapping<?, ?>> getMappings(PageCompiler compiler) {
        List<RecipeTypeMapping<?, ?>> result = new ArrayList<>();
        var mappings = new RecipeTypeMappingSupplier.RecipeTypeMappings() {
            @Override
            public <T extends Recipe<C>, C extends RecipeInput> void add(RecipeType<T> recipeType,
                    Function<RecipeHolder<T>, LytBlock> factory) {
                result.add(new RecipeTypeMapping<>(recipeType, factory));
            }
        };
        for (var extension : compiler.getExtensions(RecipeTypeMappingSupplier.EXTENSION_POINT)) {
            extension.collect(mappings);
        }
        return result;
    }
}
