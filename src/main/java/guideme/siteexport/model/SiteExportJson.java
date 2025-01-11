package guideme.siteexport.model;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public class SiteExportJson {
    public int version = 2;

    public String defaultNamespace;

    public Map<ResourceLocation, ExportedPageJson> pages = new HashMap<>();

    public Map<String, JsonElement> pageIndices = new HashMap<>();

    /**
     * Recipes indexed by their recipe ID.
     */
    public Map<String, JsonElement> recipes = new HashMap<>();

    public Map<String, ItemInfoJson> items = new HashMap<>();

    public List<NavigationNodeJson> navigationRootNodes = new ArrayList<>();

    public Map<String, FluidInfoJson> fluids = new HashMap<>();

    public Map<String, Object> modData = new HashMap<>();
}
