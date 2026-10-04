package guideme.internal.web;

import com.google.gson.JsonElement;
import guideme.internal.siteexport.model.ExportedPageJson;
import guideme.internal.siteexport.model.FluidInfoJson;
import guideme.internal.siteexport.model.IndexModel;
import guideme.internal.siteexport.model.ItemInfoJson;
import guideme.internal.siteexport.model.NavigationNodeJson;
import guideme.internal.siteexport.model.SiteExportJson;
import guideme.siteexport.web.ExportedGuide;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

class ExportedGuideImpl implements ExportedGuide {
    private final IndexModel index;
    private final SiteExportJson json;
    private final Map<String, String> pageByItemIndex = new HashMap<>();

    /**
     * Maps category name to list of page-ids within that category.
     */
    private final Map<String, List<String>> pagesByCategoryIndex = new HashMap<>();

    private final Map<String, ExportedRecipe> recipes = new HashMap<>();

    private final Map<String, List<ExportedRecipe>> recipesByResult = new HashMap<>();

    public ExportedGuideImpl(IndexModel index, SiteExportJson json) {
        this.index = index;
        this.json = json;

        // Wrap recipes first
        for (var entry : json.recipes.entrySet()) {
            recipes.put(entry.getKey(), new ExportedRecipe(entry.getKey(), entry.getValue()));
        }
        // Then index by result. Some recipe types (i.e. AE2 entropy) have no result item.
        for (var exportedRecipe : recipes.values()) {
            if (exportedRecipe.recipe().has("resultItem")) {
                recipesByResult.computeIfAbsent(exportedRecipe.resultItem(), _ -> new ArrayList<>())
                        .add(exportedRecipe);
            }
        }

        // Page indices were renamed when they moved from AE2 into GuideME (1.21.1)
        visitIndex(
                List.of("guideme.indices.ItemIndex", "appeng.client.guidebook.indices.ItemIndex"),
                (key, value) -> pageByItemIndex.put(key, value.getAsString()));
        visitIndex(
                List.of("guideme.indices.CategoryIndex", "appeng.client.guidebook.indices.CategoryIndex"),
                (key, value) -> {
                    var elements = pagesByCategoryIndex.computeIfAbsent(key, _ -> new ArrayList<>());
                    for (var jsonElement : value.getAsJsonArray()) {
                        elements.add(jsonElement.getAsString());
                    }
                });
    }

    /**
     * Visits the first page index that exists under any of the given names.
     */
    private void visitIndex(List<String> indexNames, BiConsumer<String, JsonElement> visitor) {
        JsonElement indexContent = null;
        for (var indexName : indexNames) {
            indexContent = json.pageIndices.get(indexName);
            if (indexContent != null) {
                break;
            }
        }
        if (indexContent == null) {
            return;
        }

        // Indices are serialized as [key, value, key, value, key, value] in one large array.
        var array = indexContent.getAsJsonArray();
        for (int i = 0; i < array.size(); i += 2) {
            var key = array.get(i).getAsString();
            var value = array.get(i + 1);
            visitor.accept(key, value);
        }
    }

    @Override
    public String getDefaultNamespace() {
        return json.defaultNamespace;
    }

    public ExportedPageJson getRequiredPage(String pageId) {
        var page = json.pages.get(pageId);
        if (page == null) {
            throw new IllegalArgumentException("Missing page: " + pageId);
        }
        return page;
    }

    public Map<String, ExportedPageJson> getPages() {
        return json.pages;
    }

    public String getPageBasePath(String pageId) {
        var pagePath = getSlugsFromPageId(pageId);
        return String.join("/", pagePath);
    }

    public String[] getSlugsFromPageId(String pageId) {
        String[] parts = pageId.split(":", 2);
        String namespace = parts[0];
        String resource = parts[1];

        String[] pagePath = resource.split("/");
        pagePath[pagePath.length - 1] = pagePath[pagePath.length - 1].replaceAll("\\.md$", "");

        if (namespace.equals(getDefaultNamespace())) {
            return pagePath;
        } else {
            String[] fullPath = new String[pagePath.length + 1];
            fullPath[0] = namespace;
            System.arraycopy(pagePath, 0, fullPath, 1, pagePath.length);
            return fullPath;
        }
    }

    /**
     * This needs to apply a reverse-mapping from the user-visible path for a page to the internal page-id it originated
     * from.
     */
    public String getPageIdFromSlugs(String[] pagePath) {
        // We strip the default namespace prefix from page IDs by default, so try with the default namespace first
        String pageId = getDefaultNamespace() + ":" + String.join("/", pagePath) + ".md";

        if (json.pages.containsKey(pageId)) {
            return pageId;
        }

        // Try again with the first path segment as the namespace
        if (pagePath.length > 0) {
            String[] remainingPath = new String[pagePath.length - 1];
            System.arraycopy(pagePath, 1, remainingPath, 0, remainingPath.length);
            pageId = pagePath[0] + ":" + String.join("/", remainingPath) + ".md";

            if (json.pages.containsKey(pageId)) {
                return pageId;
            }
        }

        throw new IllegalArgumentException("Cannot find page for path " + String.join("/", pagePath));
    }

    /**
     * Supports relative resource locations such as: ./somepath, which would resolve relative to a given anchor
     * location. Relative locations must not be namespaced since we would otherwise run into the problem if namespaced
     * locations potentially having a different namespace than the anchor.
     */
    public String resolveLink(String idText, String anchor) {
        if (!idText.contains(":")) {
            var anchorParts = anchor.split(":", 2);
            var anchorNs = anchorParts[0];
            var anchorPath = anchorParts[1];
            var relativeId = URI.create("http://dummy/" + anchorPath).resolve(idText).getPath().substring(1);
            return anchorNs + ":" + relativeId;
        }

        // if it contains a ":" it's assumed to be absolute
        return idText;
    }

    @Override
    public boolean pageExists(String pageId) {
        return json.pages.containsKey(pageId);
    }

    @Nullable
    public List<String> getPagesByCategory(String category) {
        return pagesByCategoryIndex.get(category);
    }

    /**
     * Resolves a potentially namespace-less id to an id that is guaranteed to have a namespace.
     */
    @Override
    public String resolveId(String idText) {
        if (!idText.contains(":")) {
            return getDefaultNamespace() + ":" + idText;
        }
        return idText;
    }

    @Override
    @Nullable
    public String getPageIdForItem(String itemId) {
        return this.pageByItemIndex.get(resolveId(itemId));
    }

    @Override
    public @Nullable ItemInfoJson tryGetItemInfo(String itemId) {
        itemId = this.resolveId(itemId);
        return json.items.get(itemId);
    }

    @Override
    public ItemInfoJson getItemInfo(String itemId) {
        var item = tryGetItemInfo(itemId);
        if (item == null) {
            throw new IllegalArgumentException("Couldn't find item '" + itemId + "'");
        }
        return item;
    }

    @Override
    public @Nullable FluidInfoJson tryGetFluidInfo(String fluidId) {
        fluidId = this.resolveId(fluidId);
        return json.fluids.get(fluidId);
    }

    @Override
    public FluidInfoJson getFluidInfo(String fluidId) {
        var fluid = tryGetFluidInfo(fluidId);
        if (fluid == null) {
            throw new IllegalArgumentException("Couldn't find fluid '" + fluidId + "'");
        }
        return fluid;
    }

    @Override
    public Object getExtraData(String identifier) {
        return json.modData.get(identifier);
    }

    public List<NavigationNodeJson> getRootNavigationNodes() {
        return json.navigationRootNodes;
    }

    public String getGameMajorVersion() {
        return Objects.requireNonNullElse(index.gameMajorVersion(), index.gameVersion());
    }

    @Override
    public List<ExportedRecipe> getRecipesForItem(String id) {
        id = resolveId(id);

        return recipesByResult.getOrDefault(id, List.of());
    }

    @Override
    @Nullable
    public ExportedRecipe getRecipeById(String id) {
        id = resolveId(id);

        return recipes.get(id);
    }

    @Nullable
    public NavigationNodeJson findNavigationNodeForPage(String pageId) {
        return findNavigationNodeForPage(pageId, getRootNavigationNodes());
    }

    @Nullable
    private NavigationNodeJson findNavigationNodeForPage(
            String pageId,
            List<NavigationNodeJson> nodes) {
        for (var node : nodes) {
            if (node.hasPage && node.pageId.equals(pageId)) {
                return node;
            }
        }
        for (var node : nodes) {
            var matchingNode = findNavigationNodeForPage(pageId, node.children);
            if (matchingNode != null) {
                return matchingNode;
            }
        }
        return null;
    }
}
