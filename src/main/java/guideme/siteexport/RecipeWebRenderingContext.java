package guideme.siteexport;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface RecipeWebRenderingContext {
    ExportedItemInfo getExportedItem(String itemId);

    /**
     * Add a standard recipe box, which has a Minecraft-style frame, and shows an item-icon for the crafting station in
     * its header alongside a title. The name of an item will be shown as the tooltip.
     */
    RecipeBoxBuilder recipeBox(String craftingStationItemId, String header, String tooltipItemId);

    String slotHtml(String itemId);

    String slotHtml(List<String> itemIds);

    String arrowHtml();

    interface RecipeBoxBuilder {
        /**
         * Creates a standard ingredient grid using the same logic used for Vanilla crafting recipes. The exported
         * recipe is expected to have an {@code ingredient} list. If the {@code shapeless} field is set to {@code true},
         * then no width is required, but if it is missing or {@code false}, the width of the recipe grid must have been
         * encoded in {@code width}.
         */
        RecipeBoxBuilder craftingIngredientGrid();

        RecipeBoxBuilder shapelessSlots(List<List<String>> ingredients);

        RecipeBoxBuilder arrow();

        /**
         * Adds a result slot with the current recipes result.
         */
        RecipeBoxBuilder resultSlot();

        RecipeBoxBuilder slot(String itemId);

        /**
         * Appends raw HTML.
         */
        RecipeBoxBuilder rawHtml(String html);

        void build();
    }
}
