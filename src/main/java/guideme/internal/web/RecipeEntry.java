package guideme.internal.web;

/**
 * Something that can be shown for a recipe on the website: Either an exported recipe, or one of the recipe displays of
 * a recipe that was not exported as a recipe.
 */
sealed interface RecipeEntry permits ExportedRecipeImpl, ExportedRecipeDisplay {
    /**
     * Like in-game, special recipes are only used for an item if there is no other recipe for it.
     */
    boolean special();
}
