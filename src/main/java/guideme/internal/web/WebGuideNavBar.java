package guideme.internal.web;

import guideme.internal.siteexport.model.NavigationNodeJson;
import guideme.web.html.HtmlFragment;
import guideme.web.html.HtmlNode;
import guideme.web.html.HtmlTag;
import java.util.List;

class WebGuideNavBar {
    /**
     * The pixel-art chevron of the in-game navigation bar, pointing right. It is rotated by CSS when expanded.
     */
    private static final String CHEVRON_PATH = "M1.5 0h1v1h1v1h1v1h1v1h-1v1h-1v1h-1v1h-1v-2h1v-1h1v-1h-1v-1h-1z";

    private final WebPageCompileContext context;

    private WebGuideNavBar(WebPageCompileContext context) {
        this.context = context;
    }

    /**
     * How many levels of descendants of the node are shown, given the levels its parent passes down to it.
     */
    private static int getLevelsBelow(NavigationNodeJson node, int inheritedLevelsBelow) {
        return node.maxDepth != null ? node.maxDepth : inheritedLevelsBelow;
    }

    private static List<NavigationNodeJson> getShownChildren(NavigationNodeJson node, int levelsBelow) {
        return levelsBelow > 0 ? node.children : List.of();
    }

    private boolean isSelfOrShownChildCurrentPage(NavigationNodeJson node, int levelsBelow) {
        if (context.pageId().equals(node.pageId)) {
            return true;
        }
        for (var child : getShownChildren(node, levelsBelow)) {
            if (isSelfOrShownChildCurrentPage(child, getLevelsBelow(child, levelsBelow - 1))) {
                return true;
            }
        }
        return false;
    }

    private HtmlNode generateLink(NavigationNodeJson node) {
        HtmlTag link;
        if (node.hasPage) {
            link = HtmlNode.tag("a");
            if (node.pageId.equals(context.pageId())) {
                link.setClassName("active");
            }
            link.setAttribute("href", context.getRelativePagePath(node.pageId));
        } else {
            // Used when there's a header but no link
            link = HtmlNode.tag("span").setClassName("expandableHeader");
        }

        if (node.icon != null) {
            var itemInfo = context.guide().getItemInfo(node.icon);
            link.append(HtmlNode.tag("img")
                    .setClassName("item-icon")
                    .setAttribute("src", context.resolveAssetPath(itemInfo.icon))
                    .setAttribute("alt", ""));
        }

        link.append(node.title);

        return link;
    }

    /**
     * The expand/collapse indicator. It is outside the link, so clicking it only toggles the level.
     */
    private static HtmlNode generateToggle() {
        return HtmlNode.tag("span")
                .setClassName("nav-toggle")
                .setAttribute("aria-hidden", "true")
                .append(HtmlNode.tag("svg")
                        .setAttribute("viewBox", "0 0 7 7")
                        .append(HtmlNode.tag("path").setAttribute("d", CHEVRON_PATH)));
    }

    private HtmlFragment generateLevel(List<NavigationNodeJson> nodes, int inheritedLevelsBelow) {
        var fragment = new HtmlFragment();

        for (var node : nodes) {
            var levelsBelow = getLevelsBelow(node, inheritedLevelsBelow);
            var children = getShownChildren(node, levelsBelow);
            if (!children.isEmpty()) {
                // Expanded by default if the current page is shown in this level, or below it
                boolean expanded = isSelfOrShownChildCurrentPage(node, levelsBelow);
                var details = HtmlNode.tag("details");
                if (expanded) {
                    details.setAttribute("open", null);
                }
                details.append(HtmlNode.tag("summary").append(generateToggle()).append(generateLink(node)))
                        .append(HtmlNode.tag("div", generateLevel(children, levelsBelow - 1))
                                .setClassName("sublevel"));
                fragment.append(details);
            } else {
                fragment.append(generateLink(node));
            }
        }
        return fragment;
    }

    static HtmlTag generate(WebPageCompileContext context) {
        var navbar = new WebGuideNavBar(context);
        var rootLevelsBelow = context.guide().getNavigationDefaultMaxDepth() - 1;
        return HtmlNode.tag("div")
                .setClassName("navbar")
                .append(navbar.generateLevel(context.guide().getRootNavigationNodes(), rootLevelsBelow));
    }

}
