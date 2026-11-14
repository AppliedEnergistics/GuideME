package guideme.internal.siteexport.model;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * The recipe displays of a recipe that was not exported as a recipe.
 */
public class RecipeDisplaysJson {
    /**
     * Special recipes are only used for an item if there is no other recipe for it.
     */
    public boolean special;

    public List<JsonObject> displays = new ArrayList<>();
}
