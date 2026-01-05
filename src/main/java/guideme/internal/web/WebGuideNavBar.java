package guideme.internal.web;

import guideme.internal.siteexport.model.NavigationNodeJson;
import guideme.siteexport.web.HtmlFragment;
import guideme.siteexport.web.HtmlNode;
import guideme.siteexport.web.HtmlTag;
import java.util.List;

class WebGuideNavBar {
    private final WebPageCompileContext context;

    private WebGuideNavBar(WebPageCompileContext context) {
        this.context = context;
    }

    private boolean isSelfOrChildCurrentPage(NavigationNodeJson node) {
        if (context.pageId().equals(node.pageId)) {
            return true;
        }
        return node.children.stream().anyMatch(this::isSelfOrChildCurrentPage);
    }

    private HtmlNode generateLink(NavigationNodeJson node) {
        if (!node.hasPage) {
            return HtmlNode.text(node.title);
        }

        var link = HtmlNode.tag("a");
        if (node.pageId.equals(context.pageId())) {
            link.setClassName("active");
        }
        link.setAttribute("href", context.getRelativePagePath(node.pageId));

        if (node.icon != null) {
            var itemInfo = context.guide().getItemInfo(node.icon);
            link.append(HtmlNode.tag("img")
                    .setClassName("item-icon")
                    .setAttribute("src", context.resolveAssetPath(itemInfo.icon))
                    .setAttribute("alt", ""));
        }

        if (!node.children.isEmpty()) {
            link.append(HtmlNode.tag("svg").append(HtmlNode.tag("path")));
        }
        link.append(node.title);

        return link;
    }

    private HtmlFragment generateLevel(List<NavigationNodeJson> nodes) {
        var fragment = new HtmlFragment();

        for (var node : nodes) {
            if (!node.children.isEmpty()) {
                // Expanded by default if any of the current nodes descendents is the current page
                boolean expanded = isSelfOrChildCurrentPage(node);
                var details = HtmlNode.tag("details");
                if (expanded) {
                    details.setAttribute("open", null);
                }
                details.append(HtmlNode.tag("summary", generateLink(node)))
                        .append(HtmlNode.tag("div", generateLevel(node.children)).setClassName("sublevel"));
                fragment.append(details);
            } else {
                fragment.append(generateLink(node));
            }
        }
        return fragment;
    }

    public static HtmlTag generate(WebPageCompileContext context) {
        var navbar = new WebGuideNavBar(context);
        return HtmlNode.tag("div")
                .setClassName("navbar")
                .append(navbar.generateLevel(context.guide().getRootNavigationNodes()));
    }

}
