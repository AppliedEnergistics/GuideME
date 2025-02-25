package guideme.compiler.tags;

import guideme.compiler.PageCompiler;
import guideme.document.DefaultStyles;
import guideme.document.block.LytBlock;
import guideme.document.block.LytBlockContainer;
import guideme.document.block.LytParagraph;
import guideme.document.block.LytPlaceholderBlock;
import guideme.document.block.LytVBox;
import guideme.internal.network.RecipeForReply;
import guideme.internal.network.RequestManager;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstNode;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;

/**
 * Shows a Recipe-Book-Like representation of the recipe needed to craft a given item.
 */
public class RecipeCompiler extends BlockTagCompiler {
    private static final Logger LOG = LoggerFactory.getLogger(RecipeCompiler.class);

    @Nullable
    private List<RecipeTypeMapping<?>> sharedMappings;

    @Override
    public Set<String> getTagNames() {
        return Set.of("Recipe", "RecipeFor");
    }

    @Override
    protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
        if ("RecipeFor".equals(el.name())) {
            var itemAndId = MdxAttrs.getRequiredItemAndId(compiler, parent, el, "id");
            if (itemAndId == null) {
                return;
            }

            var id = itemAndId.getLeft();
            var item = itemAndId.getRight();

            var placeholder = new LytPlaceholderBlock(
                    RequestManager.getInstance()
                            .requestRecipeFor(item.getDefaultInstance())
                            .thenApply(RecipeForReply::displays)
                            .thenApply(displays -> createRecipeDisplaysCarousel(compiler, displays))
            );
            placeholder.setSourceNode((MdAstNode) el);
            parent.append(placeholder);
        } else {
            var recipeId = MdxAttrs.getRequiredId(compiler, parent, el, "id");
            if (recipeId == null) {
                return;
            }
//
//            var recipeKey = ResourceKey.create(Registries.RECIPE, recipeId);
//            var recipe = recipeManager.byKey(recipeKey).orElse(null);
//            if (recipe == null) {
//                parent.appendError(compiler, "Couldn't find recipe " + recipeId, el);
//                return;
//            }
//
//            for (var mapping : getMappings(compiler)) {
//                var block = mapping.tryCreate(recipe);
//                if (block != null) {
//                    block.setSourceNode((MdAstNode) el);
//                    parent.append(block);
//                    return;
//                }
//            }

//            parent.appendError(compiler, "Couldn't find a handler for recipe " + recipeId, el);
        }
    }

    private LytBlock createRecipeDisplaysCarousel(PageCompiler compiler, List<RecipeDisplay> displays) {
        var blocks = new LytVBox();

        for (var display : displays) {
            boolean foundHandler = false;
            for (var mapping : getMappings(compiler)) {
                var block = mapping.tryCreate(display);
                if (block != null) {
                    blocks.append(block);
                    foundHandler = true;
                    break;
                }
            }
            if (!foundHandler) {
                var errorParagraph = new LytParagraph();
                errorParagraph.setStyle(DefaultStyles.ERROR_TEXT);
                errorParagraph.appendText("Found no handler for recipe display type " + display.getClass());
                blocks.append(errorParagraph);
            }
        }

        return blocks;
    }

    /**
     * Maps a recipe type to a factory that can create a layout block to display it.
     */
    private record RecipeTypeMapping<T extends RecipeDisplay>(Class<T> displayClass, Function<T, LytBlock> factory) {
        @Nullable
        LytBlock tryCreate(RecipeDisplay display) {
            if (displayClass.isInstance(display)) {
                return factory.apply(displayClass.cast(display));
            }
            return null;
        }
    }

    private Iterable<RecipeTypeMapping<?>> getMappings(PageCompiler compiler) {
        List<RecipeTypeMapping<?>> result = new ArrayList<>();
        var mappings = new RecipeTypeMappingSupplier.RecipeTypeMappings() {
            @Override
            public <T extends RecipeDisplay> void add(Class<T> displayClass, Function<T, LytBlock> factory) {
                result.add(new RecipeTypeMapping<>(displayClass, factory));
            }
        };
        for (var extension : compiler.getExtensions(RecipeTypeMappingSupplier.EXTENSION_POINT)) {
            extension.collect(mappings);
        }

        result.addAll(getSharedMappings());

        return result;
    }

    private List<? extends RecipeTypeMapping<?>> getSharedMappings() {
        if (sharedMappings != null) {
            return sharedMappings;
        }

        Set<String> displayTypes = new HashSet<>();
        List<RecipeTypeMapping<?>> result = new ArrayList<>();
        var mappings = new RecipeTypeMappingSupplier.RecipeTypeMappings() {
            @Override
            public <T extends RecipeDisplay> void add(Class<T> displayClass, Function<T, LytBlock> factory) {
                Objects.requireNonNull(displayClass, "displayClass");
                Objects.requireNonNull(factory, "factory");

                displayTypes.add(displayClass.getName());
                result.add(new RecipeTypeMapping<>(displayClass, factory));
            }
        };

        var it = ServiceLoader.load(RecipeTypeMappingSupplier.class).stream().iterator();
        while (it.hasNext()) {
            var provider = it.next();
            try {
                provider.get().collect(mappings);
            } catch (Exception e) {
                LOG.error("Failed to collect shared recipe type mappings from {}", provider.type(), e);
            }
        }

        var recipeTypesSorted = new ArrayList<>(displayTypes);
        Collections.sort(recipeTypesSorted);
        LOG.info("Discovered shared recipe type mappings: {}", recipeTypesSorted);

        return sharedMappings = List.copyOf(result);
    }
}
