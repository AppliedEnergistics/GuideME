package guideme.internal.siteexport.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SiteExportJson {
    public int version = 2;

    public String defaultNamespace;

    /**
     * ID of the page the guide opens on. Missing in exports from older versions.
     */
    public String startPage;

    public Map<String, ExportedPageJson> pages = new HashMap<>();

    public Map<String, JsonElement> pageIndices = new HashMap<>();

    /**
     * Recipes indexed by their recipe ID.
     */
    public Map<String, JsonObject> recipes = new HashMap<>();

    public Map<String, ItemInfoJson> items = new HashMap<>();

    public List<NavigationNodeJson> navigationRootNodes = new ArrayList<>();

    /**
     * The number of navigation levels shown, including the root level, unless {@link NavigationNodeJson#maxDepth}
     * changes it below a node. Missing in exports from older versions, which show all levels.
     */
    public Integer navigationDefaultMaxDepth;

    public Map<String, FluidInfoJson> fluids = new HashMap<>();

    public Map<String, Object> modData = new HashMap<>();
}
