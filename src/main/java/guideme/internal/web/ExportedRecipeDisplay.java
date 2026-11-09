package guideme.internal.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * A recipe display exported for a recipe that was not exported as a recipe.
 *
 * @param recipeId The id of the recipe this display belongs to.
 */
record ExportedRecipeDisplay(String recipeId, boolean special, JsonObject display) implements RecipeEntry {
    /**
     * The id of the recipe display type, i.e. {@code minecraft:crafting_shapeless}.
     */
    String type() {
        return display.getAsJsonPrimitive("type").getAsString();
    }

    List<String> result() {
        return getItems("result");
    }

    int resultCount() {
        var resultCount = display.get("resultCount");
        return resultCount != null ? resultCount.getAsInt() : 1;
    }

    List<String> craftingStation() {
        return getItems("craftingStation");
    }

    int width() {
        return display.getAsJsonPrimitive("width").getAsInt();
    }

    List<List<String>> ingredients() {
        var ingredients = display.getAsJsonArray("ingredients");
        if (ingredients == null) {
            return List.of();
        }
        var result = new ArrayList<List<String>>(ingredients.size());
        for (var ingredient : ingredients) {
            result.add(toItemList(ingredient.getAsJsonArray()));
        }
        return result;
    }

    private List<String> getItems(String field) {
        var items = display.getAsJsonArray(field);
        return items != null ? toItemList(items) : List.of();
    }

    private static List<String> toItemList(JsonArray items) {
        var result = new ArrayList<String>(items.size());
        for (var item : items) {
            result.add(item.getAsString());
        }
        return result;
    }
}
