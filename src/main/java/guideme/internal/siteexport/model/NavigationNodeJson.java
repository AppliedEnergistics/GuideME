package guideme.internal.siteexport.model;

import guideme.internal.screen.GuideNavBar;
import guideme.navigation.NavigationNode;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public class NavigationNodeJson {
    public String pageId;
    public String title;
    public ItemStack icon;
    public List<NavigationNodeJson> children;
    public int position;
    public boolean hasPage;
    /**
     * If set, the number of levels below this node that the navigation shows. Otherwise, one less than its parent
     * shows, starting with {@link GuideNavBar#DEFAULT_MAX_DEPTH} levels including the root level.
     */
    public Integer maxDepth;

    public static NavigationNodeJson of(NavigationNode node) {
        var jsonNode = new NavigationNodeJson();
        if (node.pageId() != null) {
            jsonNode.pageId = node.pageId().toString();
        }
        jsonNode.title = node.title();
        if (!node.icon().isEmpty()) {
            jsonNode.icon = node.icon();
        }
        jsonNode.children = node.children().stream().map(NavigationNodeJson::of).toList();
        jsonNode.position = node.position();
        jsonNode.hasPage = node.hasPage();
        jsonNode.maxDepth = node.maxDepth();
        return jsonNode;
    }
}
