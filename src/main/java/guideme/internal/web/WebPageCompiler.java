package guideme.internal.web;

import static guideme.internal.web.HtmlUtils.createHtmlElement;
import static guideme.internal.web.HtmlUtils.escapeAttribute;
import static guideme.internal.web.HtmlUtils.escapeHtml;
import static guideme.internal.web.HtmlUtils.guiScaledDimension;

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
import guideme.scene.annotation.InWorldBoxAnnotation;
import guideme.scene.annotation.InWorldLineAnnotation;
import guideme.siteexport.RecipeWebRenderer;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.util.StringRepresentable;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class WebPageCompiler {
    private static final Logger LOG = LoggerFactory.getLogger(WebPageCompiler.class);

    private final ExportedGuide guide;
    private final WebAssetsBundle webAssetsBundle;
    private final StaticSiteGenerator.Options options;
    private final Map<String, RecipeWebRenderer> recipeRenderersByType = new HashMap<>();

    public WebPageCompiler(ExportedGuide guide, WebAssetsBundle webAssetsBundle, StaticSiteGenerator.Options options) {
        this.guide = guide;
        this.webAssetsBundle = webAssetsBundle;
        this.options = options;

        registerRecipeRenderers(List.of(
                new CraftingRecipeRenderer(),
                new SmeltingRecipeRenderer(),
                new SmithingRecipeRenderer()));
        registerRecipeRenderers(ServiceLoader.load(RecipeWebRenderer.class));
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
                            compiled.content));

            // Append the page templates
            pageHtml = pageHtml.replace("</body>", String.join("", context.templates().templates) + "</body>");

            Files.writeString(pagePath, pageHtml, StandardCharsets.UTF_8);

        } catch (Exception e) {
            LOG.error("Error while compiling web page for {}", pageId, e);
            throw new RuntimeException("Error while compiling web page for " + pageId, e);
        }
    }

    // ==================== Compilation Methods ====================

    public record CompiledPage(@Nullable String title, String content) {
    }

    private String compileChildren(WebPageCompileContext context, MdAstParent<?> parent) {
        return compileChildren(context, parent.children(), parent);
    }

    private String compileChildren(WebPageCompileContext context, List<?> children, MdAstParent<?> parent) {
        List<String> elements = new ArrayList<>();
        for (Object child : children) {
            if (child instanceof MdAstNode node) {
                String compiled = compileContent(context, node, parent);
                if (compiled != null && !compiled.isEmpty()) {
                    elements.add(compiled);
                }
            }
        }

        if (elements.isEmpty()) {
            return "";
        } else if (elements.size() == 1) {
            return elements.get(0);
        } else {
            return String.join("", elements);
        }
    }

    private void assertNodeType(MdAstNode node, String expectedType) {
        if (!node.type().equals(expectedType)) {
            throw new IllegalStateException(
                    "Expected root node to have type '" + expectedType + "', but got: " + node.type());
        }
    }

    private String compileHeading(WebPageCompileContext context, MdAstHeading node) {
        String tag = "h" + node.depth;
        String content = compileChildren(context, node);
        return createElement(tag, null, content);
    }

    private final Pattern DOUBLE_QUOTE_STRING = Pattern.compile("^\\s*\"([^\"]+)\"\\s*$");
    private final Pattern SINGLE_QUOTE_STRING = Pattern.compile("^\\s*'([^']+)'\\s*$");

    private String compileTextExpression(WebPageCompileContext context, MdAstNode node) {
        // We support simple strings, but not actual JS programs
        // This assumes the node has a 'value' field - we'll need to handle this carefully
        if (node instanceof MdAstLiteral literal) {
            String value = literal.value;

            var m = DOUBLE_QUOTE_STRING.matcher(value);
            if (m.matches()) {
                return escapeHtml(m.group(1));
            }

            m = SINGLE_QUOTE_STRING.matcher(value);
            if (m.matches()) {
                return escapeHtml(m.group(1));
            }

            return compileError(node, "Unsupported JSX expression: " + value);
        }

        return compileError(node, "Unsupported JSX expression node type");
    }

    private String compileContent(WebPageCompileContext context, MdAstNode node, MdAstParent<?> parent) {
        String type = node.type();

        switch (type) {
            // We do not support definitions or footnote definitions
            case MdAstDefinition.TYPE:
            case "footnoteDefinition":
            case MdAstYamlFrontmatter.TYPE:
                // ignore frontmatter, handled already in ExportedPage
                return null;

            case MdAstHeading.TYPE:
                return compileHeading(context, (MdAstHeading) node);

            ////////////////////////// Phrasing Content
            case MdAstBreak.TYPE:
                return "<br/>";

            case MdAstImage.TYPE:
                return compileImage(context, (MdAstImage) node);

            case MdAstStrong.TYPE:
                return createElement("strong", null, compileChildren(context, (MdAstStrong) node));

            case MdAstLink.TYPE:
                return compileLink(context, (MdAstLink) node);

            case MdAstDelete.TYPE:
                return createElement("del", null, compileChildren(context, (MdAstDelete) node));

            case MdAstEmphasis.TYPE:
                return createElement("em", null, compileChildren(context, (MdAstEmphasis) node));

            case MdAstText.TYPE:
                return escapeHtml(((MdAstText) node).value);

            case MdAstInlineCode.TYPE:
                String codeValue = ((MdAstInlineCode) node).value.replaceAll("\r?\n|\r", " ");
                return createElement("code", null, escapeHtml(codeValue));

            ////////////////////////// Block Content
            case MdAstThematicBreak.TYPE:
                return "<hr/>";

            case MdAstParagraph.TYPE:
                return createElement("p", null, compileChildren(context, (MdAstParagraph) node));

            case MdAstBlockquote.TYPE:
                return createElement("blockquote", null, compileChildren(context, (MdAstBlockquote) node));

            case MdAstCode.TYPE:
                MdAstCode codeNode = (MdAstCode) node;
                String className = codeNode.lang != null ? "language-" + codeNode.lang : null;
                String codeElement = createElement("code", className, escapeHtml(codeNode.value));
                return createElement("pre", null, codeElement);

            case MdAstList.TYPE:
                return compileList(context, (MdAstList) node);

            case MdAstListItem.TYPE:
                return compileListItem(context, (MdAstListItem) node, parent);

            case GfmTable.TYPE:
                return compileTable(context, (GfmTable) node);

            // Expressions like "{' '}"
            case "mdxFlowExpression":
            case "mdxTextExpression":
                return compileTextExpression(context, node);

            // Text- and Block-Level JSX or HTML Element
            case MdxJsxFlowElement.TYPE:
                return compileCustomElement(context, (MdxJsxFlowElement) node);

            case MdxJsxTextElement.TYPE:
                return compileCustomElement(context, (MdxJsxTextElement) node);

            default:
                return compileError(node, "Unhandled node type");
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
                    title = compileHeading(context, heading);

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

        String content = compileChildren(context, tempRoot);

        return new CompiledPage(title, content);
    }

    // ==================== Helper Methods ====================

    private String createElement(String tag, @Nullable String className, String content) {
        var attributes = new HashMap<String, Object>();
        if (className != null) {
            attributes.put("class", className);
        }
        return createHtmlElement(tag, attributes, content);
    }

    String compileError(MdAstNode node, String message) {
        LOG.warn("Compilation error at {}: {}", node, message);
        return "<span style=\"color: red; font-weight: bold;\">Error: " + escapeHtml(message) + "</span>";
    }

    // Placeholder methods - these need to be implemented based on the corresponding TypeScript files
    private String compileLink(WebPageCompileContext context, MdAstLink node) {

        var href = node.url;
        var title = Objects.requireNonNullElse(node.title, "");
        var content = compileChildren(context, node);

        // Internal vs. external links
        if (href.indexOf("://") > 0 || href.indexOf("//") == 0) {
            return "<a href=\"" + escapeAttribute(href) + " title=\"" + escapeAttribute(title) + "\"\">" + content
                    + "</a>";
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

        return "<a href=\"" + escapeAttribute(url) + "\" title=\"" + escapeAttribute(title) + "\">" + content + "</a>";
    }

    private String compileImage(WebPageCompileContext context, MdAstImage node) {
        // TODO: Implement based on image.tsx
        String src = node.url;
        String alt = node.alt != null ? node.alt : "";
        return "<img src=\"" + escapeAttribute(context.resolveAssetPath(src)) + "\" alt=\"" + escapeAttribute(alt)
                + "\"/>";
    }

    private String compileList(WebPageCompileContext context, MdAstList node) {
        // TODO: Implement based on list.tsx
        String tag = node.ordered ? "ol" : "ul";
        String content = compileChildren(context, node);
        return createElement(tag, null, content);
    }

    private String compileListItem(WebPageCompileContext context, MdAstListItem node, MdAstParent<?> parent) {
        // TODO: Implement based on listItem.tsx
        String content = compileChildren(context, node);
        return createElement("li", null, content);
    }

    private String compileTable(WebPageCompileContext context, GfmTable table) {

        var errors = new StringBuilder();

        var rows = getFilteredChildren(table, GfmTableRow.class, errors);
        var tableContent = new StringBuilder();
        if (!rows.isEmpty()) {
            // Generate a one-row thead for the first table row
            tableContent.append(
                    createHtmlElement(
                            "thead",
                            Map.of(),
                            compileTableRow(context, rows.removeFirst(), table)));
        }

        if (!rows.isEmpty()) {
            tableContent.append(
                    createHtmlElement(
                            "tbody",
                            Map.of(),
                            rows.stream().map(row -> compileTableRow(context, row, table))
                                    .collect(Collectors.joining("\n"))));
        }

        return createHtmlElement("table", Map.of(), tableContent.toString());
    }

    private String compileTableRow(
            WebPageCompileContext context,
            GfmTableRow node,
            GfmTable parent) {
        var siblings = parent != null ? parent.children() : null;

        // Generate a body row when without parent.
        var rowIndex = siblings != null ? siblings.indexOf(node) : 1;
        var tagName = rowIndex == 0 ? "th" : "td";
        var align = parent != null && parent.type().equals("table") ? parent.align : null;
        var length = align != null ? align.size() : node.children().size();
        var cellIndex = -1;
        var cells = new StringBuilder();

        while (++cellIndex < length) {
            // Note: can also be undefined.
            var cell = cellIndex < node.children().size() ? node.children().get(cellIndex) : null;
            var properties = new HashMap<String, Object>();
            if (align != null) {
                properties.put("align", align.get(cellIndex));
            }

            var cellContent = cell != null ? compileChildren(context, cell) : "";
            cells.append(createHtmlElement(tagName, properties, cellContent));
        }

        return createHtmlElement("tr", Map.of(), cells.toString());
    }

    private <T> List<T> getFilteredChildren(MdAstParent<?> parent, Class<T> childType, StringBuilder errors) {
        var rows = new ArrayList<T>();
        for (var child : parent.children()) {
            if (!childType.isInstance(child)) {
                errors.append(
                        compileError(parent, "Unsupported child-node for " + parent.type() + ": " + child.type()));
                continue;
            }
            rows.add(childType.cast(child));
        }
        return rows;
    }

    private String compileCustomElement(WebPageCompileContext context, MdxJsxFlowElement node) {
        String tag = node.name() != null && !node.name().isEmpty() ? node.name() : "div";

        if (tag.toLowerCase(Locale.ROOT).equals(tag)) {
            String content = compileChildren(context, node);
            return createElement(tag, null, content);
        }

        return compileCustomElement(context, node, node);
    }

    private String compileCustomElement(WebPageCompileContext context, MdxJsxTextElement node) {
        String tag = node.name() != null && !node.name().isEmpty() ? node.name() : "span";

        if (tag.toLowerCase(Locale.ROOT).equals(tag)) {
            String content = compileChildren(context, node);

            var attributes = new HashMap<String, Object>();
            for (var attribute : node.attributes()) {
                if (attribute instanceof MdxJsxAttribute jsxAttribute) {
                    if (jsxAttribute.hasStringValue()) {
                        attributes.put(jsxAttribute.name, jsxAttribute.getStringValue());
                    } else if (jsxAttribute.getExpressionValue().isBlank()) {
                        attributes.put(jsxAttribute.name, true); // for tags of the style <tag bool-attr />
                    } else {
                        return compileError(node, "Unsupported attribute type");
                    }
                } else {
                    return compileError(node, "Unsupported attribute type");
                }
            }

            // Translate some source links automatically
            if (tag.equals("video")) {
                var src = attributes.get("src");
                if (src != null) {
                    attributes.put("src", context.resolveAssetPath(src.toString()));
                }
            }

            return createHtmlElement(tag, attributes, content);
        }

        return compileCustomElement(context, node, node);
    }

    private String compileCustomElement(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node) {
        return switch (jsxElement.name()) {
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
    }

    private String compileBlockImage(WebPageCompileContext context, MdxJsxElementFields jsxElement,
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
        return createHtmlElement("img", Map.of(
                "srcset", String.format(Locale.ROOT, "%s, %s 2x, %s 4x", asset2x, asset4x, asset8x),
                "src", asset2x,
                "style", "width: " + guiScaledDimension(width) + "; height: " + guiScaledDimension(height)));
    }

    private String compileItemLink(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
        var tooltipMode = MdxAttrs.getEnum(jsxElement, "tooltip", TooltipMode.ICON);
        var id = MdxAttrs.getString(jsxElement, "id", null);

        // Markdown Formatting can insert whitespace into MDX attributes
        id = id.replaceAll("\\s+", "");

        String innerContent = null;
        if (!node.children().isEmpty()) {
            innerContent = compileChildren(context, node);
        }

        return createItemLink(context, node, id, tooltipMode, innerContent);
    }

    private String createItemLink(WebPageCompileContext context, MdAstParent<?> node, String id,
            TooltipMode tooltipMode, @Nullable String innerContent) {

        id = guide.resolveId(id);

        var pageId = guide.getPageUrlForItem(id);
        var itemInfo = guide.tryGetItemInfo(id);
        if (itemInfo == null) {
            return compileError(node, "Missing item " + id);
        }

        if (innerContent == null) {
            innerContent = itemInfo.displayName;
        }

        // Do not render a link if we're already on that page, or there is no link
        String content;
        if (pageId == null || context.pageId().equals(pageId)) {
            content = createHtmlElement("span", Map.of("class", "item-link"), innerContent);
        } else {
            content = createHtmlElement("a", Map.of("href", guide.getRelativePagePath(pageId, context.pageId())),
                    innerContent);
        }

        return makeItemTooltip(context, itemInfo, tooltipMode, content);
    }

    private String compileItemImage(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node) {
        var id = MdxAttrs.getString(jsxElement, "id", null);
        var scale = MdxAttrs.getFloat(jsxElement, "scale", 1.0f);
        var itemInfo = guide.getItemInfo(id);

        return createHtmlElement("img", Map.of(
                "width", Math.round(32 * scale),
                "height", Math.round(32 * scale),
                "src", context.resolveAssetPath(itemInfo.icon),
                "alt", "",
                "aria-description", itemInfo.displayName));
    }

    private String compileItemIcon(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
        var id = MdxAttrs.getString(jsxElement, "id", null);
        if (id == null) {
            return compileError(node, "ItemIcon is missing id property");
        }
        var nolink = MdxAttrs.getBoolean(jsxElement, "nolink", false);

        return createItemIcon(context, node, id, nolink);
    }

    String createItemIcon(WebPageCompileContext context, MdAstParent<?> node, String id, boolean nolink) {
        var itemInfo = guide.getItemInfo(id);

        var icon = createHtmlElement("img", Map.of(
                "src", context.resolveAssetPath(itemInfo.icon),
                "alt", "",
                "aria-description", itemInfo.displayName,
                "class", "item-icon"));

        if (!nolink) {
            return createItemLink(context, node, id, TooltipMode.TEXT, icon);
        } else {
            return icon;
        }
    }

    private String compileItemGrid(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
        return createHtmlElement("div", Map.of(
                "class", "layout-item-grid"), compileChildren(context, node));
    }

    private String compileRecipe(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
        var id = MdxAttrs.getString(jsxElement, "id", null);
        if (id == null) {
            return compileError(node, "Missing id");
        }
        var recipe = guide.getRecipeById(id);
        if (recipe == null) {
            return compileError(node, "Missing recipe: " + id);
        }

        return compileRecipe(context, node, recipe);
    }

    private String compileRecipeFor(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node) {
        var id = MdxAttrs.getString(jsxElement, "id", null);
        if (id == null) {
            return compileError(node, "Missing id");
        }

        var recipes = context.guide().getRecipesForItem(id);

        if (recipes.isEmpty()) {
            return compileError(node, "No recipes for " + id);
        }

        return createHtmlElement("div", Map.of("class", "recipe-container"),
                compileRecipe(context, node, recipes.getFirst()));
    }

    private String compileRecipesFor(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node) {
        var id = MdxAttrs.getString(jsxElement, "id", null);
        if (id == null) {
            return compileError(node, "Missing id");
        }

        var recipes = context.guide().getRecipesForItem(id);

        if (recipes.isEmpty()) {
            return compileError(node, "No recipes for " + id);
        }

        return createHtmlElement("div", Map.of("class", "recipe-container"), recipes.stream().map(recipe -> {
            return compileRecipe(context, node, recipe);
        }).collect(Collectors.joining("\n")));
    }

    private String compileRecipe(WebPageCompileContext context, MdAstParent<?> node, ExportedRecipe recipe) {
        var renderer = recipeRenderersByType.get(recipe.type());
        if (renderer == null) {
            return compileError(node, "Can't handle recipe type " + recipe.type());
        }

        var recipeRenderContext = new RecipeWebRenderingContextImpl(this, context, node, recipe);
        renderer.render(recipeRenderContext, recipe);
        return recipeRenderContext.getResult();
    }

    private String compileCategoryIndex(WebPageCompileContext context, MdxJsxElementFields jsxElement,
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

        return createHtmlElement("ul", Map.of(), pages.stream().map(
                page -> createHtmlElement("li", Map.of(), createHtmlElement("a",
                        Map.of("href", context.getRelativePagePath(page.getKey())), escapeHtml(page.getValue().title))))
                .collect(Collectors.joining("\n")));
    }

    private String compileSubPages(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
        record SubPagesAttributes(
                @Nullable String id,
                @DefaultValue("false") boolean icons,
                @DefaultValue("false") boolean alphabetical) {
        }
        var attributes = JsxAttributeMapper.map(jsxElement, SubPagesAttributes.class);

        var id = Objects.requireNonNullElse(attributes.id, context.pageId());
        List<NavigationNodeJson> navNodes;
        if ("".equals(id)) {
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

        return createHtmlElement("ul", Map.of("class", "sub-pages"), navNodes.stream().map(
                n -> {
                    var linkText = new StringBuilder();
                    if (attributes.icons && n.icon != null) {
                        var itemInfo = guide.getItemInfo(n.icon);
                        linkText.append(createHtmlElement("img", Map.of(
                                "alt", "",
                                "src", context.resolveAssetPath(itemInfo.icon),
                                "class", "page-icon")));
                    }
                    linkText.append(createHtmlElement("a", Map.of("href", context.getRelativePagePath(n.pageId)),
                            escapeHtml(n.title)));
                    return createHtmlElement("li", Map.of(), linkText.toString());
                }).collect(Collectors.joining("\n")));
    }

    private String compileColumn(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
        return createHtmlElement("div", Map.of(
                "class", "layout-column"), compileChildren(context, node));
    }

    private String compileRow(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
        return createHtmlElement("div", Map.of(
                "class", "layout-row"), compileChildren(context, node));
    }

    private String compileGameScene(WebPageCompileContext context, MdxJsxElementFields jsxElement,
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
                // These tags are exported as part of the scene from the game
                case "ImportStructure":
                case "Block":
                    break;
                case "BoxAnnotation":
                    record BoxAnnotationAttributes(
                            Vector3f min,
                            Vector3f max,
                            @DefaultValue("white") String color,
                            @DefaultValue(InWorldBoxAnnotation.DEFAULT_THICKNESS + "") float thickness,
                            @DefaultValue("false") boolean alwaysOnTop) {
                    }
                    var boxAttributes = JsxAttributeMapper.map(flowElement, BoxAnnotationAttributes.class);
                    inWorldAnnotations.add(new ExportedInWorldAnnotation(
                            "box",
                            new float[] { boxAttributes.min.x, boxAttributes.min.y, boxAttributes.min.z },
                            new float[] { boxAttributes.max.x, boxAttributes.max.y, boxAttributes.max.z },
                            boxAttributes.color,
                            boxAttributes.thickness,
                            context.templates().create(compileChildren(context, flowElement)),
                            boxAttributes.alwaysOnTop));
                    break;
                case "LineAnnotation":
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
                            new float[] { lineAttributes.from.x, lineAttributes.from.y, lineAttributes.from.z },
                            new float[] { lineAttributes.to.x, lineAttributes.to.y, lineAttributes.to.z },
                            lineAttributes.color,
                            lineAttributes.thickness,
                            context.templates().create(compileChildren(context, flowElement)),
                            lineAttributes.alwaysOnTop));
                    break;
                case "DiamondAnnotation":
                    record DiamondAnnotation(Vector3f pos, @DefaultValue("transparent") String color) {
                    }
                    var diamondAttributes = JsxAttributeMapper.map(flowElement, DiamondAnnotation.class);
                    overlayAnnotations.add(new ExportedOverlayAnnotation(
                            "overlay",
                            new float[] { diamondAttributes.pos.x, diamondAttributes.pos.y, diamondAttributes.pos.z },
                            diamondAttributes.color,
                            context.templates().create(compileChildren(context, flowElement))));
                    break;
                case "IsometricCamera":
                    // Already saved as part of the scene
                    break;
                default:
                    errors.append(compileError(node, "Unsupported child tag " + childTagName));
                    break;
            }
        }

        Map<String, Object> attrs = new HashMap<>(Map.of(
                "class", "game-scene",
                "style",
                "width: " + guiScaledDimension(attributes.width()) + "; height: "
                        + guiScaledDimension(attributes.height()),
                "src", context.resolveAssetPath(attributes.placeholder()),
                "data-scene-src", context.resolveAssetPath(attributes.src()),
                "data-scene-width", attributes.width(),
                "data-scene-height", attributes.height(),
                "data-scene-zoom", attributes.zoom(),
                "data-scene-interactive", attributes.interactive(),
                "data-scene-in-world-annotations", new Gson().toJson(inWorldAnnotations),
                "data-scene-overlay-annotations", new Gson().toJson(overlayAnnotations)));

        // Compute the relative path to the output folder to fixup asset links
        attrs.put("data-scene-asset-prefix", context.getUrlPrefixToRoot());
        if (attributes.background() != null) {
            attrs.put("data-scene-background", attributes.background());
        }
        return errors + "\n" + createHtmlElement("img", attrs, null);
    }

//
//  const props = getAttributes(node) as ModelViewerProps;
//  const errors: ReactNode[] = [];
//  const extraProps: Partial<ModelViewerProps> = {};

//
//  const result = (
//    <GameScene
//      {...props}
//      {...extraProps}
//      assetBaseUrl={context.guide.baseUrl}
//      inWorldAnnotations={inWorldAnnotations}
//      overlayAnnotations={overlayAnnotations}
//    />
//  );
//  if (errors.length > 0) {
//    return React.createElement(React.Fragment, null, ...errors, result);
//  }
//  return result;
//}

    enum TooltipMode implements StringRepresentable {
        TEXT,
        ICON;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private String makeItemTooltip(WebPageCompileContext context, ItemInfoJson itemInfo, @Nullable TooltipMode mode,
            String content) {
        mode = Objects.requireNonNullElse(mode, TooltipMode.ICON);

        String tooltipContent;
        if (mode == TooltipMode.ICON) {
            tooltipContent = createHtmlElement("img", Map.of(
                    "src", context.resolveAssetPath(itemInfo.icon),
                    "alt", "",
                    "aria-description", itemInfo.displayName,
                    "class", "item-icon"), null);
        } else {
            tooltipContent = createHtmlElement("span", Map.of("class", "item-name", "data-rarity", itemInfo.rarity),
                    itemInfo.displayName);
        }

        var templateId = context.templates().create(tooltipContent);
        return createHtmlElement("span", Map.of(
                "class", "minecraft-tooltip",
                "data-template", templateId), content);
    }

    /**
     * We use this to collect HTML5 template tags that need to be uniquely identified and added to the body tag before
     * we write the page. It also handles de-duplicating template content if it is used multiple times.
     */
    static class TemplateContainer {
        private int counter = 1;
        private final List<String> templates = new ArrayList<>();
        private final Map<String, String> templateContent = new HashMap<>();

        public String create(String content) {
            var existingId = templateContent.get(content);
            if (existingId != null) {
                return existingId;
            }

            var id = "tmpl-" + (counter++);
            templates.add(createHtmlElement("template", Map.of("id", id), content));
            templateContent.put(content, id);
            return id;
        }
    }

}
