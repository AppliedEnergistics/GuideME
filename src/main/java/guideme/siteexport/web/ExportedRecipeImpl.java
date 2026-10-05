package guideme.siteexport.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;

record ExportedRecipeImpl(String id, JsonObject recipe) implements ExportedRecipe {
    @Override
    public String type() {
        return recipe.getAsJsonPrimitive("type").getAsString();
    }

    @Override
    public String resultItem() {
        return recipe.getAsJsonPrimitive("resultItem").getAsString();
    }

    @Override
    public int resultCount() {
        // Not all recipe types export the count (i.e. AE2 charger and transform recipes)
        var resultCount = recipe.get("resultCount");
        return resultCount != null ? resultCount.getAsInt() : 1;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> fields() {
        return (Map<String, Object>) new Gson().fromJson(recipe, Map.class);
    }
}
