package guideme.web;

import java.util.List;
import java.util.Set;

final class CraftingRecipeRenderer implements RecipeWebRenderer {
    @Override
    public Set<String> getSupportedTypes() {
        return Set.of("minecraft:crafting");
    }

    @Override
    public void render(RecipeWebRenderingContext builder, ExportedRecipe recipe) {
        var shapeless = Boolean.TRUE.equals(recipe.fields().get("shapeless"));

        var type = "Crafting";
        if (shapeless) {
            type += " (Shapeless)";
        }

        builder.recipeBox("minecraft:crafting_table", type, recipe.resultItem())
                .craftingIngredientGrid()
                .arrow()
                .slot(recipe.resultItem())
                .build();
    }
}

final class SmeltingRecipeRenderer implements RecipeWebRenderer {
    @Override
    public Set<String> getSupportedTypes() {
        return Set.of("minecraft:smelting", "minecraft:blasting");
    }

    @Override
    public void render(RecipeWebRenderingContext builder, ExportedRecipe recipe) {
        var ingredient = recipe.getIngredient("ingredient");

        var craftingStation = "minecraft:furnace";
        if (recipe.type().equals("minecraft:blasting")) {
            craftingStation = "minecraft:blast_furnace";
        }

        builder.recipeBox(craftingStation, "Smelting", recipe.resultItem())
                .append(HtmlNode.tag("div")
                        .setClassName("smelting-input-box")
                        .append(builder.slotHtml(ingredient))
                        .append(HtmlNode.tag("div").setClassName("fire")))
                .arrow()
                .slot(recipe.resultItem())
                .build();
    }
}

final class SmithingRecipeRenderer implements RecipeWebRenderer {
    @Override
    public Set<String> getSupportedTypes() {
        return Set.of("minecraft:smithing");
    }

    @Override
    public void render(RecipeWebRenderingContext builder, ExportedRecipe recipe) {
        var base = recipe.getIngredient("base");
        var addition = recipe.getIngredient("addition");
        var template = recipe.getIngredient("template");

        // Trim recipes have no result item, since the result is the base item with the trim applied
        var result = recipe.fields().containsKey("resultItem") ? List.of(recipe.resultItem()) : base;
        if (result.isEmpty()) {
            builder.recipeBox(builder.compileError("Smithing recipe without result")).build();
            return;
        }

        builder.recipeBox("minecraft:smithing_table", "Smithing", result.getFirst())
                .shapelessSlots(List.of(template, base, addition))
                .arrow()
                .slot(result)
                .build();
    }
}
