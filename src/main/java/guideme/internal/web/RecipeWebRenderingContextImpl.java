package guideme.internal.web;

import guideme.internal.siteexport.model.ItemInfoJson;
import guideme.libs.mdast.model.MdAstParent;
import guideme.siteexport.RecipeWebRenderingContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static guideme.internal.web.HtmlUtils.createHtmlElement;

class RecipeWebRenderingContextImpl extends WebRenderingContextImpl implements RecipeWebRenderingContext {
    private final WebPageCompiler webPageCompiler;
    private final WebPageCompileContext context;
    private final MdAstParent<?> node;
    private final ExportedRecipe recipe;
    private final StringBuilder result = new StringBuilder();

    public RecipeWebRenderingContextImpl(WebPageCompiler webPageCompiler,
                                         WebPageCompileContext context,
                                         MdAstParent<?> node,
                                         ExportedRecipe recipe) {
        super(context, webPageCompiler, node);
        this.webPageCompiler = webPageCompiler;
        this.context = context;
        this.node = node;
        this.recipe = recipe;
    }

    public String getResult() {
        return result.toString();
    }

    @Override
    public String slotHtml(String itemId) {
        return slotHtml(List.of(itemId));
    }

    @Override
    public String slotHtml(List<String> itemIds) {
        return createRecipeIngredient(context, node, itemIds);
    }

    @Override
    public String arrowHtml() {
        return createRecipeArrow();
    }

    @Override
    public RecipeBoxBuilder recipeBox(String headerHtml, String tooltipItemId) {
        var tooltipItem = context.guide().getItemInfo(tooltipItemId);

        var recipeBoxContent = new StringBuilder();
        recipeBoxContent.append(createRecipeDisplayName(context, node, craftingStationItemId, header, tooltipItem));

        return new RecipeBoxBuilder() {
            @Override
            public RecipeBoxBuilder resultSlot() {
                return slot(recipe.resultItem());
            }

            @Override
            public RecipeBoxBuilder craftingIngredientGrid() {
                recipeBoxContent.append(createRecipeIngredientGrid(context, node, recipe));
                return this;
            }

            @Override
            public RecipeBoxBuilder shapelessSlots(List<List<String>> ingredients) {
                recipeBoxContent.append(compileRecipeIngredientGrid(context, node, ingredients, true, null));
                return this;
            }

            @Override
            public RecipeBoxBuilder arrow() {
                recipeBoxContent.append(createRecipeArrow());
                return this;
            }

            @Override
            public RecipeBoxBuilder slot(List<String> itemId) {
                recipeBoxContent.append(createRecipeIngredient(context, node, itemId));
                return this;
            }

            @Override
            public RecipeBoxBuilder rawHtml(String html) {
                recipeBoxContent.append(html);
                return this;
            }

            @Override
            public RecipeBoxBuilder grid(int columns, int rows, Consumer<GridBuilder> customizer) {
                var gridContent = new HashMap<Integer, String>();

                var gridBuilder = new GridBuilder() {
                    @Override
                    public GridBuilder image(int column, int row, String assetName) {
                        Objects.checkIndex(column, columns);
                        Objects.checkIndex(row, rows);
                        return rawHtml(column, row, createHtmlElement("img", Map.of(
                                "alt", "",
                                "src", context.resolveAssetPath(assetName)
                        )));
                    }

                    @Override
                    public GridBuilder slot(int column, int row, List<String> itemId) {
                        Objects.checkIndex(column, columns);
                        Objects.checkIndex(row, rows);
                        return rawHtml(column, row, slotHtml(itemId));
                    }

                    @Override
                    public GridBuilder rawHtml(int column, int row, String html) {
                        Objects.checkIndex(column, columns);
                        Objects.checkIndex(row, rows);
                        gridContent.put(row * columns + column, html);
                        return this;
                    }
                };
                customizer.accept(gridBuilder);

                var gridHtmlContent = new StringBuilder();
                for (int i = 0; i < (columns * rows); i++) {
                    var content = gridContent.get(i);
                    if (content == null) {
                        gridHtmlContent.append(createHtmlElement("span", Map.of()));
                    } else {
                        gridHtmlContent.append(content);
                    }
                }

                recipeBoxContent.append(createHtmlElement("div",
                        Map.of("class", "grid", "style", "grid-template-columns: repeat(" + columns + ", auto);"),
                        gridHtmlContent.toString()
                ));
                return this;
            }

            @Override
            public void build() {
                result.append(createHtmlElement("div", Map.of("class", "recipe-container"), createMinecraftFrame(
                        createRecipeBoxLayout(recipeBoxContent.toString()))));
            }
        };
    }

    private String createRecipeIngredientGrid(WebPageCompileContext context, MdAstParent<?> node,
                                              ExportedRecipe recipe) {
        var shapeless = recipe.recipe().getAsJsonPrimitive("shapeless").getAsBoolean();
        var ingredientsJson = recipe.recipe().getAsJsonArray("ingredients");

        // Convert JSON ingredients to List<List<String>>
        List<List<String>> ingredients = new ArrayList<>();
        for (var ingredientElement : ingredientsJson) {
            var ingredientArray = ingredientElement.getAsJsonArray();
            List<String> ingredientList = new ArrayList<>();
            for (var item : ingredientArray) {
                ingredientList.add(item.getAsString());
            }
            ingredients.add(ingredientList);
        }

        Integer width = shapeless ? null : recipe.recipe().getAsJsonPrimitive("width").getAsInt();

        return compileRecipeIngredientGrid(context, node, ingredients, shapeless, width);
    }

    private String compileRecipeIngredientGrid(
            WebPageCompileContext context,
            MdAstParent<?> node,
            List<List<String>> ingredients,
            boolean shapeless,
            @Nullable Integer width) {
        String className;
        List<List<String>> processedIngredients = new ArrayList<>(ingredients);

        if (shapeless) {
            // Shapeless recipes do not show empty cells
            processedIngredients = processedIngredients.stream()
                    .filter(ingredient -> !ingredient.isEmpty())
                    .toList();

            if (processedIngredients.size() <= 1) {
                className = "ingredients-box-shapeless-1col";
            } else if (processedIngredients.size() <= 2) {
                className = "ingredients-box-shapeless-2col";
            } else {
                className = "ingredients-box-shapeless-3col";
            }
        } else {
            className = "ingredients-box";

            if (width == null) {
                return webPageCompiler.compileError(node, "Width is required for shaped recipes");
            }

            // Pad out the ingredient grid to 3x3 for shaped recipes
            List<List<String>> sparseIngredients = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                sparseIngredients.add(new ArrayList<>());
            }

            for (int i = 0; i < ingredients.size(); i++) {
                // Map index over to the sparse array
                int row = i / width;
                int col = i % width;

                // Shift recipes that only have 1 column to the middle
                if (width == 1) {
                    col++;
                }

                sparseIngredients.set(row * 3 + col, ingredients.get(i));
            }

            processedIngredients = sparseIngredients;
        }

        StringBuilder slotsHtml = new StringBuilder();
        for (var ingredient : processedIngredients) {
            slotsHtml.append(createRecipeIngredient(context, node, ingredient));
        }

        return createHtmlElement("div", Map.of("class", className), slotsHtml.toString());
    }

    private String createRecipeIngredient(WebPageCompileContext context, MdAstParent<?> node, List<String> itemIds) {
        if (itemIds.isEmpty()) {
            return createHtmlElement("div", Map.of("class", "empty-ingredient-box"));
        } else if (itemIds.size() == 1) {
            return createHtmlElement("div", Map.of("class", "ingredient-box"),
                    webPageCompiler.createItemIcon(context, node, itemIds.getFirst(), false));
        } else {
            var ingredientList = new StringBuilder();
            for (var itemId : itemIds) {
                ingredientList.append(webPageCompiler.createItemIcon(context, node, itemId, false)).append('\n');
            }
            return createHtmlElement("div", Map.of("class", "ingredient-box cycling"), ingredientList.toString());
        }
    }

    private String createRecipeArrow() {
        return createHtmlElement("svg", Map.of("class", "recipe-arrow", "viewBox", "0 0 85 50"),
                createHtmlElement("path",
                        Map.of("d", "M 0 20 H 60 V 0 L 85 25 L 60 50 L 60 30 L 0 30 Z", "fill", "#8b8b8b")));
    }

    private String createRecipeDisplayName(WebPageCompileContext context, MdAstParent<?> node, String iconId,
                                           String title, ItemInfoJson resultItem) {
        return createHtmlElement(
                "div",
                Map.of("title", resultItem.displayName),
                webPageCompiler.createItemIcon(context, node, iconId, true) + "\n" +
                        title + "\n");
    }

    private String createMinecraftFrame(String content) {
        return createHtmlElement("div", Map.of("class", "minecraft-frame"), content);
    }

    private String createRecipeBoxLayout(String content) {
        return createHtmlElement("div", Map.of("class", "recipeBoxLayout"), content);
    }

}
