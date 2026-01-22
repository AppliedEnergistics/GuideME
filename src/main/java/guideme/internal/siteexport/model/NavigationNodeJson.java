package guideme.internal.siteexport.model;

import guideme.internal.siteexport.SiteExportWriter;
import guideme.navigation.NavigationNode;
import java.util.List;

public class NavigationNodeJson {
    public String pageId;
    public String title;
    public String icon;
    public List<NavigationNodeJson> children;
    public int position;
    public boolean hasPage;

    public static NavigationNodeJson of(NavigationNode node) {
        var jsonNode = new NavigationNodeJson();
        if (node.pageId() != null) {
            jsonNode.pageId = node.pageId().toString();
        }
        jsonNode.title = node.title();
        if (node.icon() != null) {
            jsonNode.icon = SiteExportWriter.serializeItemStack(node.icon().create());
        }
        jsonNode.children = node.children().stream().map(NavigationNodeJson::of).toList();
        jsonNode.position = node.position();
        jsonNode.hasPage = node.hasPage();
        return jsonNode;
    }
}
