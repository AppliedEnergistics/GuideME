package guideme.internal.web;

import com.google.gson.Gson;
import guideme.compiler.tags.MdxAttrs;
import guideme.internal.siteexport.model.ItemInfoJson;
import guideme.internal.siteexport.model.NavigationNodeJson;
import guideme.libs.mdast.MdAstYamlFrontmatter;
import guideme.libs.mdast.gfm.model.GfmTable;
import guideme.libs.mdast.gfm.model.GfmTableRow;
import guideme.libs.mdast.gfmstrikethrough.MdAstDelete;
import guideme.libs.mdast.mdx.model.MdxJsxAttribute;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.mdx.model.MdxJsxFlowElement;
import guideme.libs.mdast.mdx.model.MdxJsxTextElement;
import guideme.libs.mdast.model.MdAstAnyContent;
import guideme.libs.mdast.model.MdAstBlockquote;
import guideme.libs.mdast.model.MdAstBreak;
import guideme.libs.mdast.model.MdAstCode;
import guideme.libs.mdast.model.MdAstDefinition;
import guideme.libs.mdast.model.MdAstEmphasis;
import guideme.libs.mdast.model.MdAstHeading;
import guideme.libs.mdast.model.MdAstImage;
import guideme.libs.mdast.model.MdAstInlineCode;
import guideme.libs.mdast.model.MdAstLink;
import guideme.libs.mdast.model.MdAstList;
import guideme.libs.mdast.model.MdAstListItem;
import guideme.libs.mdast.model.MdAstLiteral;
import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParagraph;
import guideme.libs.mdast.model.MdAstParent;
import guideme.libs.mdast.model.MdAstRoot;
import guideme.libs.mdast.model.MdAstStrong;
import guideme.libs.mdast.model.MdAstText;
import guideme.libs.mdast.model.MdAstThematicBreak;
import guideme.libs.micromark.extensions.gfm.Align;
import guideme.scene.annotation.InWorldBoxAnnotation;
import guideme.scene.annotation.InWorldLineAnnotation;
import guideme.siteexport.CustomElementWebRenderer;
import guideme.siteexport.DefaultValue;
import guideme.siteexport.RecipeWebRenderer;
import guideme.siteexport.web.HTMLFragment;
import guideme.siteexport.web.HTMLNode;
import guideme.siteexport.web.HTMLTag;
import guideme.siteexport.web.HTMLText;
import net.minecraft.util.StringRepresentable;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static guideme.internal.web.HtmlUtils.guiScaledDimension;

class WebPageCompiler {
  private static final Logger LOG = LoggerFactory.getLogger(WebPageCompiler.class);

  private final ExportedGuide guide;
  private final WebAssetsBundle webAssetsBundle;
  private final StaticSiteGenerator.Options options;
  private final Map<String, RecipeWebRenderer> recipeRenderersByType = new HashMap<>();
  private final Map<String, CustomElementWebRenderer> customRendererByName = new HashMap<>();

  public WebPageCompiler(ExportedGuide guide, WebAssetsBundle webAssetsBundle, StaticSiteGenerator.Options options) {
    this.guide = guide;
    this.webAssetsBundle = webAssetsBundle;
    this.options = options;

    registerRecipeRenderers(List.of(
        new CraftingRecipeRenderer(),
        new SmeltingRecipeRenderer(),
        new SmithingRecipeRenderer()));
    registerRecipeRenderers(ServiceLoader.load(RecipeWebRenderer.class));

    for (var renderer : ServiceLoader.load(CustomElementWebRenderer.class)) {
      for (var tagName : renderer.getTagNames()) {
        customRendererByName.put(tagName, renderer);
      }
    }
  }

  private void registerRecipeRenderers(Iterable<RecipeWebRenderer> renderers) {
    for (var recipeWebRenderer : renderers) {
      for (var type : recipeWebRenderer.getSupportedTypes()) {
        var previous = recipeRenderersByType.put(type, recipeWebRenderer);
        if (previous != null) {
          LOG.warn("Duplicate web recipe renderer registration for type {}: {} was replaced by {}",
              type, previous, recipeWebRenderer);
        }
      }
    }
  }

  public void compile(String pageId) {
    try {
      var page = guide.getRequiredPage(pageId);

      var templates = new TemplateContainer();
      var context = new WebPageCompileContext(options, guide, pageId, page, templates);
      var compiled = compilePage(context);

      var pagePath = context.resolveOutputPath(guide.getPageBasePath(pageId) + ".html");
      var pageHtml = webAssetsBundle.realizeLayoutTemplate(
          context,
          new LayoutPlaceholders(
              pagePath.getParent(),
              compiled.title,
              compiled.content.toString()));

      // Append the page templates
      pageHtml = pageHtml.replace("</body>", context.templates().templates.stream().map(Object::toString).collect(Collectors.joining()) + "</body>");

      Files.writeString(pagePath, pageHtml, StandardCharsets.UTF_8);

    } catch (Exception e) {
      LOG.error("Error while compiling web page for {}", pageId, e);
      throw new RuntimeException("Error while compiling web page for " + pageId, e);
    }
  }

  // ==================== Compilation Methods ====================

  public record CompiledPage(@Nullable String title, HTMLFragment content) {
  }

  HTMLFragment compileChildren(WebPageCompileContext context, MdAstParent<?> parent) {
    return compileChildren(context, parent.children(), parent);
  }

  HTMLFragment compileChildren(WebPageCompileContext context, List<?> children, MdAstParent<?> parent) {
    var elements = new HTMLFragment();
    for (Object child : children) {
      if (child instanceof MdAstNode node) {
        compileContent(context, node, parent, elements::append);
      }
    }

    return elements;
  }

  private void assertNodeType(MdAstNode node, String expectedType) {
    if (!node.type().equals(expectedType)) {
      throw new IllegalStateException(
          "Expected root node to have type '" + expectedType + "', but got: " + node.type());
    }
  }

  private HTMLTag compileHeading(WebPageCompileContext context, MdAstHeading node) {
    return HTMLNode.tag("h" + node.depth, compileChildren(context, node));
  }

  private static final Pattern DOUBLE_QUOTE_STRING = Pattern.compile("^\\s*\"([^\"]+)\"\\s*$");
  private static final Pattern SINGLE_QUOTE_STRING = Pattern.compile("^\\s*'([^']+)'\\s*$");

  private HTMLNode compileTextExpression(WebPageCompileContext context, MdAstNode node) {
    // We support simple strings, but not actual JS programs
    // This assumes the node has a 'value' field - we'll need to handle this carefully
    if (node instanceof MdAstLiteral literal) {
      String value = literal.value;

      var m = DOUBLE_QUOTE_STRING.matcher(value);
      if (m.matches()) {
        return HTMLNode.text(m.group(1));
      }

      m = SINGLE_QUOTE_STRING.matcher(value);
      if (m.matches()) {
        return HTMLNode.text(m.group(1));
      }

      return compileError(node, "Unsupported JSX expression: " + value);
    }

    return compileError(node, "Unsupported JSX expression node type");
  }

  private void compileContent(WebPageCompileContext context, MdAstNode node, MdAstParent<?> parent, Consumer<HTMLNode> output) {
    String type = node.type();

    switch (type) {
      // We do not support definitions or footnote definitions
      case MdAstDefinition.TYPE, "footnoteDefinition", MdAstYamlFrontmatter.TYPE -> {
        // ignore frontmatter, handled already in ExportedPage
      }
      case MdAstHeading.TYPE -> output.accept(compileHeading(context, (MdAstHeading) node));


      //====================== Phrasing Content
      case MdAstBreak.TYPE -> output.accept(HTMLNode.tag("br"));
      case MdAstImage.TYPE -> output.accept(compileImage(context, (MdAstImage) node));
      case MdAstStrong.TYPE -> output.accept(HTMLNode.tag("strong", compileChildren(context, (MdAstStrong) node)));
      case MdAstLink.TYPE -> output.accept(compileLink(context, (MdAstLink) node));
      case MdAstDelete.TYPE -> output.accept(HTMLNode.tag("del", compileChildren(context, (MdAstDelete) node)));
      case MdAstEmphasis.TYPE -> output.accept(HTMLNode.tag("em", compileChildren(context, (MdAstEmphasis) node)));
      case MdAstText.TYPE -> output.accept(HTMLNode.text(((MdAstText) node).value));
      case MdAstInlineCode.TYPE -> {
        String codeValue = ((MdAstInlineCode) node).value.replaceAll("\r?\n|\r", " ");
        output.accept(HTMLNode.tag("code").append(codeValue));
      }

      //====================== Block Content
      case MdAstThematicBreak.TYPE -> output.accept(HTMLNode.tag("hr"));
      case MdAstParagraph.TYPE -> output.accept(HTMLNode.tag("p", compileChildren(context, (MdAstParagraph) node)));
      case MdAstBlockquote.TYPE ->
          output.accept(HTMLNode.tag("blockquote", compileChildren(context, (MdAstBlockquote) node)));
      case MdAstCode.TYPE -> {
        MdAstCode codeNode = (MdAstCode) node;
        String className = codeNode.lang != null ? "language-" + codeNode.lang : null;
        var codeElement = HTMLNode.tag("code")
            .setClassName(className)
            .append(codeNode.value);
        output.accept(HTMLNode.tag("pre", codeElement));
      }
      case MdAstList.TYPE -> output.accept(compileList(context, (MdAstList) node));
      case MdAstListItem.TYPE -> output.accept(compileListItem(context, (MdAstListItem) node, parent));
      case GfmTable.TYPE -> output.accept(compileTable(context, (GfmTable) node));

      // Expressions like "{' '}"
      case "mdxFlowExpression", "mdxTextExpression" -> output.accept(compileTextExpression(context, node));

      // Text- and Block-Level JSX or HTML Element
      case MdxJsxFlowElement.TYPE -> compileCustomElement(context, (MdxJsxFlowElement) node, output);
      case MdxJsxTextElement.TYPE -> compileCustomElement(context, (MdxJsxTextElement) node, output);

      default -> output.accept(compileError(node, "Unhandled node type"));
    }
  }

  private CompiledPage compilePage(WebPageCompileContext context) {
    MdAstRoot astRoot = context.page().astRoot;
    assertNodeType(astRoot, MdAstRoot.TYPE);

    String title = null;

    // Clone root - we'll modify the children list
    List<MdAstAnyContent> clonedChildren = new ArrayList<>(astRoot.children());

    // Pull out first heading if it's level 1 and use as page title
    for (int i = 0; i < clonedChildren.size(); i++) {
      MdAstAnyContent child = clonedChildren.get(i);
      if (child instanceof MdAstHeading heading) {
        if (heading.depth == 1) {
          title = compileHeading(context, heading).textContent();

          // Wrap the existing heading such that it can be re-enabled for mobile clients
          MdxJsxFlowElement wrapper = new MdxJsxFlowElement();
          wrapper.name = "div";

          MdxJsxAttribute classAttr = new MdxJsxAttribute();
          classAttr.name = "className";
          classAttr.setValue("inlinePageTitle");
          wrapper.attributes.add(classAttr);

          wrapper.children().add(heading);

          clonedChildren.set(i, wrapper);
        }
        break;
      }
    }

    // Create a temporary parent to hold the cloned children
    MdAstRoot tempRoot = new MdAstRoot();
    tempRoot.children().addAll(clonedChildren);

    var content = compileChildren(context, tempRoot);

    return new CompiledPage(title, content);
  }

  // ==================== Helper Methods ====================

  HTMLTag compileError(MdAstNode node, String message) {
    LOG.warn("Compilation error at {}: {}", node, message);
    return HTMLNode.tag("span")
        .setStyles(Map.of(
            "color", "red",
            "font-weight", "bold"
        ))
        .append("Error: " + message);
  }

  // Placeholder methods - these need to be implemented based on the corresponding TypeScript files
  private HTMLTag compileLink(WebPageCompileContext context, MdAstLink node) {

    var href = node.url;
    var title = Objects.requireNonNullElse(node.title, "");
    var content = compileChildren(context, node);

    // Internal vs. external links
    if (href.indexOf("://") > 0 || href.indexOf("//") == 0) {
      return HTMLNode.tag("a")
          .setAttribute("href", href)
          .setAttribute("title", title)
          .append(content);
    }

    // Split fragment+url
    var urlParts = href.split("#", 2);
    var path = urlParts[0];

    // Determine the page id, account for relative paths
    var pageId = guide.resolveLink(path, context.pageId());

    if (!guide.pageExists(pageId)) {
      return compileError(node, "Page does not exist");
    }

    var url = guide.getRelativePagePath(pageId, context.pageId());
    if (urlParts.length > 1) {
      url += "#" + urlParts[1];
    }

    return HTMLNode.tag("a")
        .setAttribute("href", url)
        .setAttribute("title", title)
        .append(content);
  }

  private HTMLTag compileImage(WebPageCompileContext context, MdAstImage node) {
    // TODO: Implement based on image.tsx
    String src = node.url;
    String alt = node.alt != null ? node.alt : "";
    return HTMLNode.tag("img")
        .setAttribute("src", context.resolveAssetPath(src))
        .setAttribute("alt", alt);
  }

  private HTMLTag compileList(WebPageCompileContext context, MdAstList node) {
    // TODO: Implement based on list.tsx
    String tag = node.ordered ? "ol" : "ul";
    var content = compileChildren(context, node);
    return HTMLNode.tag(tag, content);
  }

  private HTMLTag compileListItem(WebPageCompileContext context, MdAstListItem node, MdAstParent<?> parent) {
    // TODO: Implement based on listItem.tsx
    var content = compileChildren(context, node);
    return HTMLNode.tag("li", content);
  }

  private HTMLTag compileTable(WebPageCompileContext context, GfmTable table) {

    var errors = new HTMLFragment();

    var rows = getFilteredChildren(table, GfmTableRow.class, errors::append);

    var tableTag = HTMLNode.tag("table");

    if (!rows.isEmpty()) {
      // Generate a one-row thead for the first table row
      tableTag.append(
          HTMLNode.tag(
              "thead",
              compileTableRow(context, rows.removeFirst(), table)));
    }

    if (!rows.isEmpty()) {
      var tbody = HTMLNode.tag("tbody");
      for (var row : rows) {
        tbody.append(compileTableRow(context, row, table));
      }
      tableTag.append(tbody);
    }

    return tableTag;
  }

  private HTMLTag compileTableRow(
      WebPageCompileContext context,
      GfmTableRow node,
      GfmTable parent) {
    var siblings = parent != null ? parent.children() : null;

    // Generate a body row when without parent.
    var rowIndex = siblings != null ? siblings.indexOf(node) : 1;
    var tagName = rowIndex == 0 ? "th" : "td";
    var cellAlignments = parent != null && parent.type().equals("table") ? parent.align : null;
    var length = cellAlignments != null ? cellAlignments.size() : node.children().size();
    var cellIndex = -1;
    var row = HTMLNode.tag("tr");

    while (++cellIndex < length) {
      var cellTag = HTMLNode.tag(tagName);
      if (cellAlignments != null) {
        var align = cellAlignments.get(cellIndex);
        if (align != Align.NONE) {
          cellTag.setAttribute("align", align.name().toLowerCase(Locale.ROOT));
        }
      }

      // Note: can also be undefined.
      var cellNodes = cellIndex < node.children().size() ? node.children().get(cellIndex) : null;
      if (cellNodes != null) {
        cellTag.append(compileChildren(context, cellNodes));
      }
      row.append(cellTag);
    }

    return row;
  }

  private <T> List<T> getFilteredChildren(MdAstParent<?> parent, Class<T> childType, Consumer<HTMLNode> errors) {
    var rows = new ArrayList<T>();
    for (var child : parent.children()) {
      if (!childType.isInstance(child)) {
        errors.accept(
            compileError(parent, "Unsupported child-node for " + parent.type() + ": " + child.type()));
        continue;
      }
      rows.add(childType.cast(child));
    }
    return rows;
  }

  private void compileCustomElement(WebPageCompileContext context, MdxJsxFlowElement node, Consumer<HTMLNode> output) {
    String tag = (node.name() != null && !node.name().isEmpty()) ? node.name() : "div";

    // Direct translation of html tags based on the heuristic that lowercase tags are HTML tags
    if (tag.toLowerCase(Locale.ROOT).equals(tag)) {
      output.accept(HTMLNode.tag(tag, compileChildren(context, node)));
    } else {
      compileCustomElement(context, node, node, output);
    }
  }

  private void compileCustomElement(WebPageCompileContext context, MdxJsxTextElement node, Consumer<HTMLNode> output) {
    String tagName = node.name() != null && !node.name().isEmpty() ? node.name() : "span";

    if (tagName.toLowerCase(Locale.ROOT).equals(tagName)) {
      var tag = HTMLNode.tag(tagName).append(compileChildren(context, node));

      for (var attribute : node.attributes()) {
        if (attribute instanceof MdxJsxAttribute jsxAttribute) {
          if (jsxAttribute.hasStringValue()) {
            tag.setAttribute(jsxAttribute.name, jsxAttribute.getStringValue());
          } else if (jsxAttribute.getExpressionValue().isBlank()) {
            tag.setAttribute(jsxAttribute.name, null); // for tags of the style <tag bool-attr />
          } else {
            output.accept(compileError(node, "Unsupported attribute type"));
            return;
          }
        } else {
          output.accept(compileError(node, "Unsupported attribute type"));
          return;
        }
      }

      // Translate some source links automatically
      if (tagName.equals("video")) {
        var src = tag.attribute("src");
        if (src != null) {
          tag.setAttribute("src", context.resolveAssetPath(src));
        }
      }

      output.accept(tag);
    } else {
      compileCustomElement(context, node, node, output);
    }
  }

  private void compileCustomElement(WebPageCompileContext context,
                                    MdxJsxElementFields jsxElement,
                                    MdAstParent<?> node,
                                    Consumer<HTMLNode> output) {
    var customRenderer = customRendererByName.get(jsxElement.name());
    if (customRenderer != null) {
      customRenderer.render(new CustomElementWebRenderingContextImpl(this, context, jsxElement, node), output);
    } else {
      HTMLNode tag = switch (jsxElement.name()) {
        case "BlockImage" -> compileBlockImage(context, jsxElement, node);
        case "CategoryIndex" -> compileCategoryIndex(context, jsxElement, node);
        case "SubPages" -> compileSubPages(context, jsxElement, node);
        case "Column" -> compileColumn(context, jsxElement, node);
        case "ItemLink" -> compileItemLink(context, jsxElement, node);
        case "ItemImage" -> compileItemImage(context, jsxElement, node);
        case "ItemIcon" -> compileItemIcon(context, jsxElement, node);
        case "ItemGrid" -> compileItemGrid(context, jsxElement, node);
        case "Recipe" -> compileRecipe(context, jsxElement, node);
        case "RecipeFor" -> compileRecipeFor(context, jsxElement, node);
        case "RecipesFor" -> compileRecipesFor(context, jsxElement, node);
        case "Row" -> compileRow(context, jsxElement, node);
        case "GameScene" -> compileGameScene(context, jsxElement, node);
        default -> compileError(node, "Unhandled custom element: " + jsxElement.name());
      };
      output.accept(tag);
    }
  }

  private HTMLNode compileBlockImage(WebPageCompileContext context, MdxJsxElementFields jsxElement,
                                     MdAstParent<?> node) {
    // These are compiled during export
    var src2x = MdxAttrs.getString(jsxElement, "src@2", null);
    var src4x = MdxAttrs.getString(jsxElement, "src@4", null);
    var src8x = MdxAttrs.getString(jsxElement, "src@8", null);
    var width = MdxAttrs.getFloat(jsxElement, "width", Float.NaN);
    var height = MdxAttrs.getFloat(jsxElement, "height", Float.NaN);
    if (src2x == null) {
      return compileError(node, "Element is missing src@2");
    }
    if (Float.isNaN(width)) {
      return compileError(node, "Element is missing width");
    }
    if (Float.isNaN(height)) {
      return compileError(node, "Element is missing height");
    }

    var asset2x = context.resolveAssetPath(src2x);
    var asset4x = context.resolveAssetPath(src4x);
    var asset8x = context.resolveAssetPath(src8x);
    return HTMLNode.tag("img")
        .setAttribute("srcset", String.format(Locale.ROOT, "%s, %s 2x, %s 4x", asset2x, asset4x, asset8x))
        .setAttribute("src", asset2x)
        .setStyles(Map.of("width", guiScaledDimension(width), "height", guiScaledDimension(height)));
  }

  private HTMLNode compileItemLink(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
    var tooltipMode = MdxAttrs.getEnum(jsxElement, "tooltip", TooltipMode.ICON);
    var id = MdxAttrs.getString(jsxElement, "id", null);

    // Markdown Formatting can insert whitespace into MDX attributes
    id = id.replaceAll("\\s+", "");

    HTMLFragment innerContent = new HTMLFragment();
    if (!node.children().isEmpty()) {
      innerContent = compileChildren(context, node);
    }

    return createItemLink(context, node, id, tooltipMode, innerContent);
  }

  private HTMLNode createItemLink(WebPageCompileContext context, MdAstParent<?> node, String id,
                                  TooltipMode tooltipMode, @Nullable HTMLFragment innerContent) {

    id = guide.resolveId(id);

    var pageId = guide.getPageUrlForItem(id);
    var itemInfo = guide.tryGetItemInfo(id);
    if (itemInfo == null) {
      return compileError(node, "Missing item " + id);
    }

    if (innerContent == null) {
      innerContent = new HTMLFragment();
      innerContent.append(HTMLNode.text(itemInfo.displayName));
    }

    // Do not render a link if we're already on that page, or there is no link
    HTMLTag content;
    if (pageId == null || context.pageId().equals(pageId)) {
      content = HTMLNode.tag("span").setClassName("item-link")
          .append(innerContent);
    } else {
      content = HTMLNode.tag("a")
          .setAttribute("href", guide.getRelativePagePath(pageId, context.pageId()))
          .append(innerContent);
    }

    return makeItemTooltip(context, itemInfo, tooltipMode, content);
  }

  private HTMLNode compileItemImage(WebPageCompileContext context, MdxJsxElementFields jsxElement,
                                    MdAstParent<?> node) {
    var id = MdxAttrs.getString(jsxElement, "id", null);
    var scale = MdxAttrs.getFloat(jsxElement, "scale", 1.0f);
    var itemInfo = guide.getItemInfo(id);

    return HTMLNode.tag("img")
        .setAttribute("width", Math.round(32 * scale))
        .setAttribute("height", Math.round(32 * scale))
        .setAttribute("src", context.resolveAssetPath(itemInfo.icon))
        .setAttribute("alt", "")
        .setAttribute("aria-description", itemInfo.displayName);
  }

  private HTMLNode compileItemIcon(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
    var id = MdxAttrs.getString(jsxElement, "id", null);
    if (id == null) {
      return compileError(node, "ItemIcon is missing id property");
    }
    var nolink = MdxAttrs.getBoolean(jsxElement, "nolink", false);

    return createItemIcon(context, node, id, nolink);
  }

  HTMLNode createItemIcon(WebPageCompileContext context, MdAstParent<?> node, String id, boolean nolink) {
    var itemInfo = guide.getItemInfo(id);

    var icon = HTMLNode.tag("img")
        .setClassName("item-icon")
        .setAttribute("src", context.resolveAssetPath(itemInfo.icon))
        .setAttribute("alt", "")
        .setAttribute("aria-description", itemInfo.displayName)
        .setAttribute("class", "item-icon");

    if (!nolink) {
      return createItemLink(context, node, id, TooltipMode.TEXT, new HTMLFragment(icon));
    } else {
      return icon;
    }
  }

  private HTMLNode compileItemGrid(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
    return HTMLNode.tag("div", compileChildren(context, node))
        .setClassName("layout-item-grid");
  }

  private HTMLTag compileRecipe(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
    var id = MdxAttrs.getString(jsxElement, "id", null);
    if (id == null) {
      return compileError(node, "Missing id");
    }
    var recipe = guide.getRecipeById(id);
    if (recipe == null) {
      return compileError(node, "Missing recipe: " + id);
    }

    var container = HTMLNode.tag("div").setClassName("recipe-container");
    compileRecipeInner(context, node, recipe, container::append);
    return container;
  }

  private HTMLTag compileRecipeFor(WebPageCompileContext context, MdxJsxElementFields jsxElement,
                                   MdAstParent<?> node) {
    var id = MdxAttrs.getString(jsxElement, "id", null);
    if (id == null) {
      return compileError(node, "Missing id");
    }

    var recipes = context.guide().getRecipesForItem(id);

    if (recipes.isEmpty()) {
      return compileError(node, "No recipes for " + id);
    }

    var container = HTMLNode.tag("div").setClassName("recipe-container");
    compileRecipeInner(context, node, recipes.getFirst(), container::append);
    return container;
  }

  private HTMLTag compileRecipesFor(WebPageCompileContext context, MdxJsxElementFields jsxElement,
                                    MdAstParent<?> node) {
    var id = MdxAttrs.getString(jsxElement, "id", null);
    if (id == null) {
      return compileError(node, "Missing id");
    }

    var recipes = context.guide().getRecipesForItem(id);

    if (recipes.isEmpty()) {
      return compileError(node, "No recipes for " + id);
    }

    var container = HTMLNode.tag("div").setClassName("recipe-container");
    for (var recipe : recipes) {
      compileRecipeInner(context, node, recipe, container::append);
    }
    return container;
  }

  private void compileRecipeInner(WebPageCompileContext context, MdAstParent<?> node, ExportedRecipe recipe, Consumer<HTMLNode> output) {
    var renderer = recipeRenderersByType.get(recipe.type());
    if (renderer == null) {
      output.accept(compileError(node, "Can't handle recipe type " + recipe.type()));
      return;
    }

    var recipeRenderContext = new RecipeWebRenderingContextImpl(this, context, node, recipe, output);
    renderer.render(recipeRenderContext, recipe);
  }

  private HTMLNode compileCategoryIndex(WebPageCompileContext context, MdxJsxElementFields jsxElement,
                                        MdAstParent<?> node) {
    var category = MdxAttrs.getString(jsxElement, "category", null);
    if (category == null) {
      return compileError(node, "Missing category attribute");
    }

    var pageIds = guide.getPagesByCategory(category);
    if (pageIds == null) {
      return compileError(node, "Unknown category: " + category);
    }

    var pages = pageIds.stream()
        .map(id -> Pair.of(id, guide.getRequiredPage(id)))
        .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.getRight().title, "")))
        .toList();

    var listTag = HTMLNode.tag("ul");
    for (var page : pages) {
      var pageLink = HTMLNode.tag("a")
          .setAttribute("href", context.getRelativePagePath(page.getKey()))
          .append(page.getValue().title);
      listTag.append(HTMLNode.tag("li", pageLink));
    }
    return listTag;
  }

  private HTMLNode compileSubPages(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
    record SubPagesAttributes(
        @Nullable String id,
        @DefaultValue("false") boolean icons,
        @DefaultValue("false") boolean alphabetical) {
    }
    var attributes = JsxAttributeMapper.map(jsxElement, SubPagesAttributes.class);

    var id = Objects.requireNonNullElse(attributes.id, context.pageId());
    List<NavigationNodeJson> navNodes;
    if (id.isEmpty()) {
      navNodes = guide.getRootNavigationNodes();
    } else {
      var pageNode = guide.findNavigationNodeForPage(id);
      if (pageNode == null) {
        return compileError(node, "Could not find current page in navigation tree.");
      }
      navNodes = pageNode.children;
    }

    // Filter out anything that does not have a page
    navNodes = navNodes.stream().filter(n -> n.hasPage).toList();

    if (attributes.alphabetical) {
      navNodes = new ArrayList<>(navNodes);
      navNodes.sort(Comparator.comparing(n -> n.title));
    }

    var list = HTMLNode.tag("ul").setClassName("sub-pages");
    for (var n : navNodes) {
      var listItem = HTMLNode.tag("li");

      if (attributes.icons && n.icon != null) {
        var itemInfo = guide.getItemInfo(n.icon);
        listItem.append(HTMLNode.tag("img")
            .setClassName("page-icon")
            .setAttribute("alt", "")
            .setAttribute("src", context.resolveAssetPath(itemInfo.icon)));
      }
      listItem.append(HTMLNode.tag("a")
          .setAttribute("href", context.getRelativePagePath(n.pageId))
          .append(n.title));
    }
    return list;
  }

  private HTMLNode compileColumn(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
    return HTMLNode.tag("div")
        .setClassName("layout-column")
        .append(compileChildren(context, node));
  }

  private HTMLNode compileRow(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
    return HTMLNode.tag("div")
        .setClassName("layout-row")
        .append(compileChildren(context, node));
  }

  private HTMLTag compileGameScene(WebPageCompileContext context, MdxJsxElementFields jsxElement,
                                   MdAstParent<?> node) {
    var errors = new StringBuilder();
    var attributes = JsxAttributeMapper.map(jsxElement, GameSceneAttributes.class);

    record ExportedInWorldAnnotation(String type, float[] minCorner, float[] maxCorner, String color,
                                     float thickness, String contentTemplateId, boolean alwaysOnTop) {
    }
    record ExportedOverlayAnnotation(String type, float[] pos, String color, String contentTemplateId) {
    }
    var inWorldAnnotations = new ArrayList<ExportedInWorldAnnotation>();
    var overlayAnnotations = new ArrayList<ExportedOverlayAnnotation>();

    // Process child elements of GameScene
    for (var child : node.children()) {
      // We're only interested in JSX children
      if (!(child instanceof MdxJsxFlowElement flowElement)) {
        if (child instanceof MdAstText textNode && textNode.value().isBlank()) {
          continue; // Ignore whitespace
        }
        errors.append(compileError(node, "Child node type " + child.type() + " is unsupported here"));
        continue;
      }

      var childTagName = Objects.requireNonNullElse(flowElement.name(), "");
      switch (childTagName) {
        case "ImportStructure", "Block", "RemoveBlocks", "Entity", "IsometricCamera" -> {
          // These tags are exported as part of the scene from the game
        }
        case "BoxAnnotation" -> {
          record BoxAnnotation(
              Vector3f min,
              Vector3f max,
              @DefaultValue("white") String color,
              @DefaultValue(InWorldBoxAnnotation.DEFAULT_THICKNESS + "") float thickness,
              @DefaultValue("false") boolean alwaysOnTop) {
          }
          var boxAttributes = JsxAttributeMapper.map(flowElement, BoxAnnotation.class);
          inWorldAnnotations.add(new ExportedInWorldAnnotation(
              "box",
              new float[]{boxAttributes.min.x, boxAttributes.min.y, boxAttributes.min.z},
              new float[]{boxAttributes.max.x, boxAttributes.max.y, boxAttributes.max.z},
              boxAttributes.color,
              boxAttributes.thickness,
              context.templates().create(compileChildren(context, flowElement)),
              boxAttributes.alwaysOnTop));
        }
        case "LineAnnotation" -> {
          record LineAnnotation(
              Vector3f from,
              Vector3f to,
              @DefaultValue("transparent") String color,
              @DefaultValue(InWorldLineAnnotation.DEFAULT_THICKNESS + "") float thickness,
              @DefaultValue("false") boolean alwaysOnTop) {
          }
          var lineAttributes = JsxAttributeMapper.map(flowElement, LineAnnotation.class);
          inWorldAnnotations.add(new ExportedInWorldAnnotation(
              "line",
              new float[]{lineAttributes.from.x, lineAttributes.from.y, lineAttributes.from.z},
              new float[]{lineAttributes.to.x, lineAttributes.to.y, lineAttributes.to.z},
              lineAttributes.color,
              lineAttributes.thickness,
              context.templates().create(compileChildren(context, flowElement)),
              lineAttributes.alwaysOnTop));
        }
        case "DiamondAnnotation" -> {
          record DiamondAnnotation(Vector3f pos, @DefaultValue("transparent") String color) {
          }
          var diamondAttributes = JsxAttributeMapper.map(flowElement, DiamondAnnotation.class);
          overlayAnnotations.add(new ExportedOverlayAnnotation(
              "overlay",
              new float[]{diamondAttributes.pos.x, diamondAttributes.pos.y, diamondAttributes.pos.z},
              diamondAttributes.color,
              context.templates().create(compileChildren(context, flowElement))));
        }
        default -> errors.append(compileError(node, "Unsupported child tag " + childTagName));
      }
    }

    var placeholderImage = HTMLNode.tag("img")
        .setClassName("game-scene")
        .setStyles(Map.of(
                "width", guiScaledDimension(attributes.width()),
                "height", guiScaledDimension(attributes.height())
        ))
        .setAttribute("src", context.resolveAssetPath(attributes.placeholder()))
        .setAttribute("data-scene-src", context.resolveAssetPath(attributes.src()))
        .setAttribute("data-scene-width", attributes.width())
        .setAttribute("data-scene-height", attributes.height())
        .setAttribute("data-scene-zoom", attributes.zoom())
        .setAttribute("data-scene-interactive", String.valueOf(attributes.interactive()))
        .setAttribute("data-scene-in-world-annotations", new Gson().toJson(inWorldAnnotations))
        .setAttribute("data-scene-overlay-annotations", new Gson().toJson(overlayAnnotations))
        // Compute the relative path to the output folder to fixup asset links
        .setAttribute("data-scene-asset-prefix", context.getUrlPrefixToRoot());

    if (attributes.background() != null) {
      placeholderImage.setAttribute("data-scene-background", attributes.background());
    }

    return placeholderImage;
  }

  enum TooltipMode implements StringRepresentable {
    TEXT,
    ICON;

    @Override
    public String getSerializedName() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  private HTMLTag makeItemTooltip(WebPageCompileContext context,
                                  ItemInfoJson itemInfo,
                                  @Nullable TooltipMode mode,
                                  HTMLTag content) {
    mode = Objects.requireNonNullElse(mode, TooltipMode.ICON);

    HTMLTag tooltipContent;
    if (mode == TooltipMode.ICON) {
      tooltipContent = HTMLNode.tag("img")
          .setClassName("item-icon")
          .setAttribute("src", context.resolveAssetPath(itemInfo.icon))
          .setAttribute("alt", "")
          .setAttribute("aria-description", itemInfo.displayName);
    } else {
      tooltipContent = HTMLNode.tag("span")
          .setClassName("item-name")
          .setAttribute("data-rarity", itemInfo.rarity)
          .append(itemInfo.displayName);
    }

    var templateId = context.templates().create(tooltipContent);
    return HTMLNode.tag("span", content)
        .setClassName("minecraft-tooltip")
        .setAttribute("data-template", templateId);
  }

  /**
   * We use this to collect HTML5 template tags that need to be uniquely identified and added to the body tag before
   * we write the page. It also handles de-duplicating template content if it is used multiple times.
   */
  static class TemplateContainer {
    private int counter = 1;
    private final List<HTMLTag> templates = new ArrayList<>();
    private final Map<String, String> templateContent = new HashMap<>();

    public String create(HTMLFragment content) {
      return create(content.toString());
    }

    public String create(HTMLNode content) {
      return create(content.toString());
    }

    private String create(String htmlContent) {
      var existingId = templateContent.get(htmlContent);
      if (existingId != null) {
        return existingId;
      }

      var id = "tmpl-" + (counter++);
      templates.add(HTMLNode.tag("template")
          .setAttribute("id", id)
          .append(HTMLText.text(htmlContent))); // TODO: Mark as raw HTML
      templateContent.put(htmlContent, id);
      return id;
    }
  }

}
