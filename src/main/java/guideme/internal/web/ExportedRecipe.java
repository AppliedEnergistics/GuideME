package guideme.internal.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;

record ExportedRecipe(String id, JsonObject recipe) implements guideme.siteexport.web.ExportedRecipe {
    public String type() {
        return recipe.getAsJsonPrimitive("type").getAsString();
    }

    public String resultItem() {
        return recipe.getAsJsonPrimitive("resultItem").getAsString();
    }

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
