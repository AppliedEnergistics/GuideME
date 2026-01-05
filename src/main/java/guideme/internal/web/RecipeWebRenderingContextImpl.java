package guideme.internal.web;

import guideme.internal.siteexport.model.ItemInfoJson;
import guideme.libs.mdast.model.MdAstParent;
import guideme.siteexport.RecipeWebRenderingContext;
import guideme.siteexport.web.HTMLFragment;
import guideme.siteexport.web.HTMLNode;
import guideme.siteexport.web.HTMLTag;
import guideme.siteexport.web.HTMLText;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

class RecipeWebRenderingContextImpl extends WebRenderingContextImpl implements RecipeWebRenderingContext {
  private final WebPageCompiler webPageCompiler;
  private final WebPageCompileContext context;
  private final MdAstParent<?> node;
  private final ExportedRecipe recipe;
  private final Consumer<HTMLNode> output;

  public RecipeWebRenderingContextImpl(WebPageCompiler webPageCompiler,
                                       WebPageCompileContext context,
                                       MdAstParent<?> node,
                                       ExportedRecipe recipe,
                                       Consumer<HTMLNode> output) {
    super(context, webPageCompiler, node);
    this.webPageCompiler = webPageCompiler;
    this.context = context;
    this.node = node;
    this.recipe = recipe;
    this.output = output;
  }

  @Override
  public HTMLNode slotHtml(String itemId) {
    return slotHtml(List.of(itemId));
  }

  @Override
  public HTMLNode slotHtml(List<String> itemIds) {
    return createRecipeIngredient(context, node, itemIds);
  }

  @Override
  public HTMLNode arrowHtml() {
    return createRecipeArrow();
  }

  @Override
  public RecipeBoxBuilder recipeBox(String craftingStationItemId, String title, String tooltipItemId) {
    var tooltipItem = context.guide().getItemInfo(tooltipItemId);
    return recipeBox(
        createRecipeDisplayName(context, node, craftingStationItemId, title, tooltipItem)
    );
  }

  @Override
  public RecipeBoxBuilder recipeBox(HTMLNode header) {

    var recipeBoxContent = new HTMLFragment();
    recipeBoxContent.append(header);

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
      public RecipeBoxBuilder append(HTMLNode node) {
        recipeBoxContent.append(node);
        return this;
      }

      @Override
      public RecipeBoxBuilder grid(int columns, int rows, Consumer<GridBuilder> customizer) {
        var gridContent = new HashMap<Integer, HTMLNode>();

        var gridBuilder = new GridBuilder() {
          @Override
          public GridBuilder image(int column, int row, String assetName) {
            Objects.checkIndex(column, columns);
            Objects.checkIndex(row, rows);
            return set(column, row, HTMLNode.tag("img")
                .setAttribute("alt", "")
                .setAttribute("src", context.resolveAssetPath(assetName))
            );
          }

          @Override
          public GridBuilder slot(int column, int row, List<String> itemId) {
            Objects.checkIndex(column, columns);
            Objects.checkIndex(row, rows);
            return set(column, row, slotHtml(itemId));
          }

          @Override
          public GridBuilder set(int column, int row, HTMLNode node) {
            Objects.checkIndex(column, columns);
            Objects.checkIndex(row, rows);
            gridContent.put(row * columns + column, node);
            return this;
          }
        };
        customizer.accept(gridBuilder);

        var gridContainer = HTMLNode.tag("div")
            .setClassName("grid")
            .setAttribute("style", "grid-template-columns: repeat(" + columns + ", auto);");
        for (int i = 0; i < (columns * rows); i++) {
          var content = gridContent.get(i);
          if (content == null) {
            gridContainer.append(HTMLNode.tag("span"));
          } else {
            gridContainer.append(content);
          }
        }

        recipeBoxContent.append(gridContainer);
        return this;
      }

      @Override
      public void build() {
        output.accept(HTMLNode.tag("div")
            .setClassName("recipe-container")
            .append(createMinecraftFrame().append(createRecipeBoxLayout().append(recipeBoxContent))));
      }
    };
  }

  private HTMLTag createRecipeIngredientGrid(WebPageCompileContext context, MdAstParent<?> node,
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

  private HTMLTag compileRecipeIngredientGrid(
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

    HTMLTag gridContainer = HTMLNode.tag("div").setClassName(className);
    for (var ingredient : processedIngredients) {
      gridContainer.append(createRecipeIngredient(context, node, ingredient));
    }
    return gridContainer;
  }

  private HTMLNode createRecipeIngredient(WebPageCompileContext context, MdAstParent<?> node, List<String> itemIds) {
    if (itemIds.isEmpty()) {
      return HTMLNode.tag("div").setClassName("empty-ingredient-box");
    } else if (itemIds.size() == 1) {
      return HTMLNode.tag("div")
          .setClassName("ingredient-box")
          .append(webPageCompiler.createItemIcon(context, node, itemIds.getFirst(), false));
    } else {
      var cyclingIngredientBox = HTMLNode.tag("div").setClassName("ingredient-box cycling");
      for (var itemId : itemIds) {
        cyclingIngredientBox.append(webPageCompiler.createItemIcon(context, node, itemId, false));
      }
      return cyclingIngredientBox;
    }
  }

  private HTMLNode createRecipeArrow() {
    return HTMLNode.tag("svg")
        .setClassName("recipe-arrow")
        .setAttribute("viewBox", "0 0 85 50")
        .append(HTMLNode.tag("path")
            .setAttribute("d", "M 0 20 H 60 V 0 L 85 25 L 60 50 L 60 30 L 0 30 Z")
            .setAttribute("fill", "#8b8b8b"));
  }

  private HTMLTag createRecipeDisplayName(WebPageCompileContext context, MdAstParent<?> node, String iconId,
                                          String title, ItemInfoJson resultItem) {
    return HTMLNode.tag("div")
        .setAttribute("title", resultItem.displayName)
        .append(webPageCompiler.createItemIcon(context, node, iconId, true))
        .append(title);
  }

  private HTMLTag createMinecraftFrame() {
    return HTMLNode.tag("div").setClassName("minecraft-frame");
  }

  private HTMLTag createRecipeBoxLayout() {
    return HTMLNode.tag("div").setClassName("recipeBoxLayout");
  }

}
