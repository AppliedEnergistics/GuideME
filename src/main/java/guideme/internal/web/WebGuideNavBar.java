package guideme.internal.web;

import static guideme.internal.web.HtmlUtils.createHtmlElement;

import guideme.internal.siteexport.model.NavigationNodeJson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private String generateLink(NavigationNodeJson node) {
        var text = HtmlUtils.escapeHtml(node.title);

        if (!node.hasPage) {
            return text;
        }

        var linkContent = new StringBuilder();
        if (node.icon != null) {
            var itemInfo = context.guide().getItemInfo(node.icon);
            linkContent.append(createHtmlElement("img",
                    Map.of("src", context.resolveAssetPath(itemInfo.icon), "alt", "", "class", "item-icon")));
        }

        if (!node.children.isEmpty()) {
            linkContent.append(createHtmlElement("svg", Map.of(), createHtmlElement("path", Map.of())));
        }
        linkContent.append(text);

        var href = context.getRelativePagePath(node.pageId);
        var attrs = new HashMap<String, Object>(Map.of("href", href));
        if (node.pageId.equals(context.pageId())) {
            attrs.put("class", "active");
        }

        return createHtmlElement("a", attrs, linkContent.toString());
    }

    private String generateLevel(List<NavigationNodeJson> nodes) {
        if (nodes.isEmpty()) {
            return "";
        }

        return nodes.stream().map(node -> {
            if (!node.children.isEmpty()) {
                // Expanded by default if any of the current nodes descendents is the current page
                boolean expanded = isSelfOrChildCurrentPage(node);
                var childrenHtml = createHtmlElement("div", Map.of("class", "sublevel"), generateLevel(node.children));
                return createHtmlElement("details", expanded ? Map.of("open", "") : Map.of(),
                        createHtmlElement("summary", Map.of(), generateLink(node)) + "\n" + childrenHtml);
            } else {
                return generateLink(node);
            }
        }).collect(Collectors.joining("\n"));
    }

    public static String generate(WebPageCompileContext context) {
        var navbar = new WebGuideNavBar(context);
        return createHtmlElement("div", Map.of("class", "navbar"),
                navbar.generateLevel(context.guide().getRootNavigationNodes()));
    }

}
