package guideme.internal.web;

import guideme.internal.siteexport.model.NavigationNodeJson;
import guideme.siteexport.web.HTMLFragment;
import guideme.siteexport.web.HTMLNode;
import guideme.siteexport.web.HTMLTag;

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

  private HTMLNode generateLink(NavigationNodeJson node) {
    if (!node.hasPage) {
      return HTMLNode.text(node.title);
    }

    var link = HTMLNode.tag("a");
    if (node.pageId.equals(context.pageId())) {
      link.setClassName("active");
    }
    link.setAttribute("href", context.getRelativePagePath(node.pageId));

    if (node.icon != null) {
      var itemInfo = context.guide().getItemInfo(node.icon);
      link.append(HTMLNode.tag("img")
          .setClassName("item-icon")
          .setAttribute("src", context.resolveAssetPath(itemInfo.icon))
          .setAttribute("alt", ""));
    }

    if (!node.children.isEmpty()) {
      link.append(HTMLNode.tag("svg").append(HTMLNode.tag("path")));
    }
    link.append(node.title);

    return link;
  }

  private HTMLFragment generateLevel(List<NavigationNodeJson> nodes) {
    var fragment = new HTMLFragment();

    for (var node : nodes) {
      if (!node.children.isEmpty()) {
        // Expanded by default if any of the current nodes descendents is the current page
        boolean expanded = isSelfOrChildCurrentPage(node);
        var details = HTMLNode.tag("details");
        if (expanded) {
          details.setAttribute("open", null);
        }
        details.append(HTMLNode.tag("summary", generateLink(node)))
            .append(HTMLNode.tag("div", generateLevel(node.children)).setClassName("sublevel"));
        fragment.append(details);
      } else {
        fragment.append(generateLink(node));
      }
    }
    return fragment;
  }

  public static HTMLTag generate(WebPageCompileContext context) {
    var navbar = new WebGuideNavBar(context);
    return HTMLNode.tag("div")
        .setClassName("navbar")
        .append(navbar.generateLevel(context.guide().getRootNavigationNodes()));
  }

}
