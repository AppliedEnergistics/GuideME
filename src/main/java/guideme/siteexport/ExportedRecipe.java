package guideme.siteexport;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

/**
 * A previously exported recipe.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ExportedRecipe {
    /**
     * The string representation of the recipe types key, i.e. {@code minecraft:crafting}.
     */
    String type();

    /**
     * {@return string representation of the result item of this recipe, usually an item id}
     */
    String resultItem();

    /**
     * {@return the number of result items produced by this recipe}
     */
    int resultCount();

    /**
     * {@return the JSON representation of this recipe converted back to a generic map}
     */
    Map<String, Object> fields();

    /**
     * Convenience method to read an ingredient from the recipe, which was represented as a list of item ids.
     */
    default List<String> getIngredient(String fieldName) {
        var field = fields().get(fieldName);
        if (field == null) {
            return List.of();
        }
        if (field instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return null;
    }
}
