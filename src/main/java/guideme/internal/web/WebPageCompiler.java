package guideme.internal.web;

import static guideme.internal.web.HtmlUtils.guiScaledDimension;

import com.google.gson.Gson;
import guideme.color.LightDarkMode;
import guideme.color.SymbolicColor;
import guideme.compiler.tags.MdxAttrs;
import guideme.internal.siteexport.model.ExportedPageJson;
import guideme.internal.siteexport.model.ItemInfoJson;
import guideme.internal.siteexport.model.NavigationNodeJson;
import guideme.libs.mdast.MdAst;
import guideme.libs.mdast.MdAstYamlFrontmatter;
import guideme.libs.mdast.MdastOptions;
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
import guideme.siteexport.DefaultValue;
import guideme.siteexport.web.CustomElementWebRenderer;
import guideme.siteexport.web.HtmlFragment;
import guideme.siteexport.web.HtmlNode;
import guideme.siteexport.web.HtmlTag;
import guideme.siteexport.web.RecipeWebRenderer;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import net.minecraft.util.StringRepresentable;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class WebPageCompiler {
    private static final Logger LOG = LoggerFactory.getLogger(WebPageCompiler.class);

    private final ExportedGuideImpl guide;
    private final WebAssetsBundle webAssetsBundle;
    private final StaticSiteGenerator.Options options;
    private final Map<String, RecipeWebRenderer> recipeRenderersByType = new HashMap<>();
    private final Map<String, CustomElementWebRenderer> customRendererByName = new HashMap<>();
    private final WebResourceCopier resourceCopier;
    private final SitePaths paths;
    private final SearchIndex searchIndex = new SearchIndex();

    public WebPageCompiler(ExportedGuideImpl guide, WebAssetsBundle webAssetsBundle,
            StaticSiteGenerator.Options options, WebResourceCopier resourceCopier, SitePaths paths) {
        this.guide = guide;
        this.webAssetsBundle = webAssetsBundle;
        this.options = options;
        this.resourceCopier = resourceCopier;
        this.paths = paths;

        // Built-in renderers can be replaced by mods
        registerRecipeRenderers(List.of(
                new CraftingRecipeRenderer(),
                new SmeltingRecipeRenderer(),
                new SmithingRecipeRenderer()), false);
        registerRecipeRenderers(ServiceLoader.load(RecipeWebRenderer.class, RecipeWebRenderer.class.getClassLoader()),
                true);

        for (var renderer : ServiceLoader.load(CustomElementWebRenderer.class,
                CustomElementWebRenderer.class.getClassLoader())) {
            LOG.info("Using custom element renderer {} for tags {}", renderer.getClass().getName(),
                    renderer.getTagNames());
            for (var tagName : renderer.getTagNames()) {
                var previous = customRendererByName.put(tagName, renderer);
                if (previous != null) {
                    LOG.warn("Duplicate custom element renderer for tag {}: {} was replaced by {}",
                            tagName, previous.getClass().getName(), renderer.getClass().getName());
                }
            }
        }
    }

    private void registerRecipeRenderers(Iterable<RecipeWebRenderer> renderers, boolean fromMods) {
        var builtIn = new HashMap<>(recipeRenderersByType);
        for (var recipeWebRenderer : renderers) {
            if (fromMods) {
                LOG.info("Using recipe renderer {} for recipe types {}", recipeWebRenderer.getClass().getName(),
                        recipeWebRenderer.getSupportedTypes());
            }
            for (var type : recipeWebRenderer.getSupportedTypes()) {
                var previous = recipeRenderersByType.put(type, recipeWebRenderer);
                if (previous != null && builtIn.get(type) != previous) {
                    LOG.warn("Duplicate recipe renderer for type {}: {} was replaced by {}",
                            type, previous.getClass().getName(), recipeWebRenderer.getClass().getName());
                }
            }
        }
    }

    WebResourceCopier getResourceCopier() {
        return resourceCopier;
    }

    public void compile(String pageId) {
        try {
            var page = guide.getRequiredPage(pageId);
            var pageFile = paths.pageFile(pageId);

            var context = new WebPageCompileContext(options, guide, paths, pageId, page,
                    SitePaths.relativePathToRoot(pageFile), new TemplateContainer());
            var compiled = writePage(context, pageFile, options.absoluteUrl(paths.pageUrl(pageId)));
            searchIndex.add(pageId, paths.pageUrl(pageId), compiled.title(), compiled.content());
        } catch (Exception e) {
            LOG.error("Error while compiling web page for {}", pageId, e);
            throw new RuntimeException("Error while compiling web page for " + pageId, e);
        }
    }

    /**
     * Writes a 404.html page, which web hosts serve for any URL that does not exist. It has to use absolute links,
     * since the URL it will be served from is unknown.
     */
    public void compileNotFoundPage() {
        var page = new ExportedPageJson();
        page.title = "Page Not Found";
        page.astRoot = MdAst.fromMarkdown("""
                # Page Not Found

                The page you are looking for does not exist. Use the navigation to find what you are looking for.
                """, new MdastOptions());

        var context = new WebPageCompileContext(options, guide, paths, "", page, options.basePath(),
                new TemplateContainer());
        try {
            writePage(context, "404.html", null);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write 404 page", e);
        }
    }

    SearchIndex getSearchIndex() {
        return searchIndex;
    }

    /**
     * @return The compiled page, without templates.
     */
    private CompiledPage writePage(WebPageCompileContext context, String pageFile, @Nullable String canonicalUrl)
            throws IOException {
        var compiled = compilePage(context);
        // Pages without a level 1 heading fall back to the title from the navigation
        var title = Objects.requireNonNullElse(compiled.title, Objects.requireNonNullElse(context.page().title, ""));

        var content = new HtmlFragment();
        content.append(compiled.content);

        // Append the page templates
        for (var template : context.templates().templates) {
            content.append(template);
        }

        var pageHtml = webAssetsBundle.realizeLayoutTemplate(context,
                new LayoutPlaceholders(title, content, canonicalUrl));

        Files.writeString(context.resolveOutputPath(pageFile), pageHtml, StandardCharsets.UTF_8);

        return new CompiledPage(title, compiled.content);
    }

    // ==================== Compilation Methods ====================

    public record CompiledPage(@Nullable String title, HtmlFragment content) {
    }

    HtmlFragment compileChildren(WebPageCompileContext context, MdAstParent<?> parent) {
        return compileChildren(context, parent.children(), parent);
    }

    HtmlFragment compileChildren(WebPageCompileContext context, List<?> children, MdAstParent<?> parent) {
        var elements = new HtmlFragment();
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

    private HtmlTag compileHeading(WebPageCompileContext context, MdAstHeading node) {
        return HtmlNode.tag("h" + node.depth, compileChildren(context, node));
    }

    private static final Pattern DOUBLE_QUOTE_STRING = Pattern.compile("^\\s*\"([^\"]+)\"\\s*$");
    private static final Pattern SINGLE_QUOTE_STRING = Pattern.compile("^\\s*'([^']+)'\\s*$");

    private HtmlNode compileTextExpression(WebPageCompileContext context, MdAstNode node) {
        // We support simple strings, but not actual JS programs
        // This assumes the node has a 'value' field - we'll need to handle this carefully
        if (node instanceof MdAstLiteral literal) {
            String value = literal.value;

            var m = DOUBLE_QUOTE_STRING.matcher(value);
            if (m.matches()) {
                return HtmlNode.text(m.group(1));
            }

            m = SINGLE_QUOTE_STRING.matcher(value);
            if (m.matches()) {
                return HtmlNode.text(m.group(1));
            }

            return compileError(node, "Unsupported JSX expression: " + value);
        }

        return compileError(node, "Unsupported JSX expression node type");
    }

    private void compileContent(WebPageCompileContext context, MdAstNode node, MdAstParent<?> parent,
            Consumer<HtmlNode> output) {
        String type = node.type();

        switch (type) {
            // We do not support definitions or footnote definitions
            case MdAstDefinition.TYPE, "footnoteDefinition", MdAstYamlFrontmatter.TYPE -> {
                // ignore frontmatter, handled already in ExportedPage
            }
            case MdAstHeading.TYPE -> output.accept(compileHeading(context, (MdAstHeading) node));

            // ====================== Phrasing Content
            case MdAstBreak.TYPE -> output.accept(HtmlNode.tag("br"));
            case MdAstImage.TYPE -> output.accept(compileImage(context, (MdAstImage) node));
            case MdAstStrong.TYPE ->
                output.accept(HtmlNode.tag("strong", compileChildren(context, (MdAstStrong) node)));
            case MdAstLink.TYPE -> output.accept(compileLink(context, (MdAstLink) node));
            case MdAstDelete.TYPE -> output.accept(HtmlNode.tag("del", compileChildren(context, (MdAstDelete) node)));
            case MdAstEmphasis.TYPE ->
                output.accept(HtmlNode.tag("em", compileChildren(context, (MdAstEmphasis) node)));
            case MdAstText.TYPE -> output.accept(HtmlNode.text(((MdAstText) node).value));
            case MdAstInlineCode.TYPE -> {
                String codeValue = ((MdAstInlineCode) node).value.replaceAll("\r?\n|\r", " ");
                output.accept(HtmlNode.tag("code").append(codeValue));
            }

            // ====================== Block Content
            case MdAstThematicBreak.TYPE -> output.accept(HtmlNode.tag("hr"));
            case MdAstParagraph.TYPE ->
                output.accept(HtmlNode.tag("p", compileChildren(context, (MdAstParagraph) node)));
            case MdAstBlockquote.TYPE ->
                output.accept(HtmlNode.tag("blockquote", compileChildren(context, (MdAstBlockquote) node)));
            case MdAstCode.TYPE -> {
                MdAstCode codeNode = (MdAstCode) node;
                var codeElement = HtmlNode.tag("code").append(codeNode.value);
                if (codeNode.lang != null) {
                    codeElement.setClassName("language-" + codeNode.lang);
                }
                output.accept(HtmlNode.tag("pre", codeElement));
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

    HtmlTag compileError(MdAstNode node, String message) {
        LOG.warn("Compilation error at {}: {}", node, message);
        return HtmlNode.tag("span")
                .setStyles(Map.of(
                        "color", "red",
                        "font-weight", "bold"))
                .append("Error: " + message);
    }

    // Placeholder methods - these need to be implemented based on the corresponding TypeScript files
    private HtmlTag compileLink(WebPageCompileContext context, MdAstLink node) {

        var href = node.url;
        var link = HtmlNode.tag("a", compileChildren(context, node));
        if (node.title != null) {
            link.setAttribute("title", node.title);
        }

        // Internal vs. external links
        if (href.indexOf("://") > 0 || href.indexOf("//") == 0) {
            return link.setAttribute("href", href);
        }

        // Split fragment+url
        var urlParts = href.split("#", 2);
        var path = urlParts[0];

        // Determine the page id, account for relative paths
        var pageId = guide.resolveLink(path, context.pageId());

        if (!guide.pageExists(pageId)) {
            return compileError(node, "Page does not exist");
        }

        var url = context.getRelativePagePath(pageId);
        if (urlParts.length > 1) {
            url += "#" + urlParts[1];
        }

        return link.setAttribute("href", url);
    }

    private HtmlTag compileImage(WebPageCompileContext context, MdAstImage node) {
        var img = HtmlNode.tag("img")
                .setAttribute("src", context.resolveAssetPath(node.url))
                .setAttribute("alt", Objects.requireNonNullElse(node.alt, ""));
        if (node.title != null) {
            img.setAttribute("title", node.title);
        }
        return img;
    }

    private HtmlTag compileList(WebPageCompileContext context, MdAstList node) {
        var content = compileChildren(context, node);
        if (node.ordered) {
            var list = HtmlNode.tag("ol", content);
            if (node.start != 1) {
                list.setAttribute("start", node.start);
            }
            return list;
        } else {
            return HtmlNode.tag("ul", content);
        }
    }

    /**
     * Port of the list item handling of mdast-util-to-hast. In tight lists, the paragraphs of list items are unwrapped.
     */
    private HtmlTag compileListItem(WebPageCompileContext context, MdAstListItem node, MdAstParent<?> parent) {
        var content = compileChildren(context, node).nodes();

        var loose = parent instanceof MdAstList list ? isLooseList(list) : node.spread;

        var listItem = HtmlNode.tag("li");
        for (int i = 0; i < content.size(); i++) {
            var child = content.get(i);
            var isParagraph = child.name().equals("p");

            // Add line-breaks before nodes, except if this is a loose, first paragraph.
            if (loose || i != 0 || !isParagraph) {
                listItem.append("\n");
            }

            if (isParagraph && !loose) {
                for (var paragraphChild : child.children()) {
                    listItem.append(paragraphChild);
                }
            } else {
                listItem.append(child);
            }
        }

        // Add a final line-break.
        if (!content.isEmpty() && (loose || !content.getLast().name().equals("p"))) {
            listItem.append("\n");
        }

        return listItem;
    }

    private static boolean isLooseList(MdAstList list) {
        if (list.spread) {
            return true;
        }
        for (var child : list.children()) {
            if (child instanceof MdAstListItem listItem && listItem.spread) {
                return true;
            }
        }
        return false;
    }

    private HtmlTag compileTable(WebPageCompileContext context, GfmTable table) {

        var errors = new HtmlFragment();

        var rows = getFilteredChildren(table, GfmTableRow.class, errors::append);

        var tableTag = HtmlNode.tag("table");

        if (!rows.isEmpty()) {
            // Generate a one-row thead for the first table row
            tableTag.append(
                    HtmlNode.tag(
                            "thead",
                            compileTableRow(context, rows.removeFirst(), table)));
        }

        if (!rows.isEmpty()) {
            var tbody = HtmlNode.tag("tbody");
            for (var row : rows) {
                tbody.append(compileTableRow(context, row, table));
            }
            tableTag.append(tbody);
        }

        return tableTag;
    }

    private HtmlTag compileTableRow(
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
        var row = HtmlNode.tag("tr");

        while (++cellIndex < length) {
            var cellTag = HtmlNode.tag(tagName);
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

    private <T> List<T> getFilteredChildren(MdAstParent<?> parent, Class<T> childType, Consumer<HtmlNode> errors) {
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

    private void compileCustomElement(WebPageCompileContext context, MdxJsxFlowElement node,
            Consumer<HtmlNode> output) {
        String tagName = (node.name() != null && !node.name().isEmpty()) ? node.name() : "div";

        // Direct translation of html tags based on the heuristic that lowercase tags are HTML tags
        if (tagName.toLowerCase(Locale.ROOT).equals(tagName)) {
            compileHtmlTag(context, tagName, node, node, output);
        } else {
            compileCustomElement(context, node, node, output);
        }
    }

    private void compileCustomElement(WebPageCompileContext context, MdxJsxTextElement node,
            Consumer<HtmlNode> output) {
        String tagName = node.name() != null && !node.name().isEmpty() ? node.name() : "span";

        if (tagName.toLowerCase(Locale.ROOT).equals(tagName)) {
            compileHtmlTag(context, tagName, node, node, output);
        } else {
            compileCustomElement(context, node, node, output);
        }
    }

    private void compileHtmlTag(WebPageCompileContext context,
            String tagName,
            MdxJsxElementFields jsxElement,
            MdAstParent<?> node,
            Consumer<HtmlNode> output) {
        var tag = HtmlNode.tag(tagName).append(compileChildren(context, node));

        var hasClass = false;
        for (var attribute : jsxElement.attributes()) {
            if (!(attribute instanceof MdxJsxAttribute jsxAttribute)) {
                output.accept(compileError(node, "Unsupported attribute type"));
                return;
            }

            // JSX uses className, while HTML uses class
            var name = jsxAttribute.name;
            if (name.equals("className") || name.equals("class")) {
                if (hasClass) {
                    output.accept(compileError(node, "Both class and className specified"));
                    return;
                }
                hasClass = true;
                name = "class";
            }

            if (jsxAttribute.hasStringValue()) {
                tag.setAttribute(name, jsxAttribute.getStringValue());
            } else if (!jsxAttribute.hasExpressionValue() || jsxAttribute.getExpressionValue().isBlank()) {
                tag.setAttribute(name, null); // for tags of the style <tag bool-attr />
            } else {
                output.accept(compileError(node, "Unsupported attribute value"));
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
    }

    private void compileCustomElement(WebPageCompileContext context,
            MdxJsxElementFields jsxElement,
            MdAstParent<?> node,
            Consumer<HtmlNode> output) {
        // Errors such as malformed attributes should only affect the element itself, not the whole page
        var elementOutput = new ArrayList<HtmlNode>();
        try {
            compileCustomElementUnsafe(context, jsxElement, node, elementOutput::add);
        } catch (RuntimeException e) {
            LOG.debug("Failed to compile element {}", jsxElement.name(), e);
            output.accept(compileError(node, "Failed to compile " + jsxElement.name() + ": " + e.getMessage()));
            return;
        }
        elementOutput.forEach(output);
    }

    private void compileCustomElementUnsafe(WebPageCompileContext context,
            MdxJsxElementFields jsxElement,
            MdAstParent<?> node,
            Consumer<HtmlNode> output) {
        var customRenderer = customRendererByName.get(jsxElement.name());
        if (customRenderer != null) {
            customRenderer.render(new CustomElementWebRenderingContextImpl(this, context, jsxElement, node), output);
        } else {
            switch (jsxElement.name()) {
                case "BlockImage" -> output.accept(compileBlockImage(context, jsxElement, node));
                case "CategoryIndex" -> output.accept(compileCategoryIndex(context, jsxElement, node));
                case "SubPages" -> output.accept(compileSubPages(context, jsxElement, node));
                case "Column" -> output.accept(compileColumn(context, jsxElement, node));
                case "ItemLink" -> output.accept(compileItemLink(context, jsxElement, node));
                case "ItemImage" -> output.accept(compileItemImage(context, jsxElement, node));
                case "ItemIcon" -> output.accept(compileItemIcon(context, jsxElement, node));
                case "ItemGrid" -> output.accept(compileItemGrid(context, jsxElement, node));
                case "Recipe" -> compileRecipe(context, jsxElement, node, output);
                case "RecipeFor" -> compileRecipeFor(context, jsxElement, node, output);
                case "RecipesFor" -> compileRecipesFor(context, jsxElement, node, output);
                case "Row" -> output.accept(compileRow(context, jsxElement, node));
                case "GameScene" -> compileGameScene(context, jsxElement, node, output);
                case "Color" -> output.accept(compileColor(context, jsxElement, node));
                case "KeyBind" -> output.accept(compileKeyBind(jsxElement, node));
                case "PlayerName" -> output.accept(HtmlNode.tag("span").setClassName("player-name").append("Player"));
                case "CommandLink" -> output.accept(compileCommandLink(context, jsxElement, node));
                case "FloatingImage" -> output.accept(compileFloatingImage(context, jsxElement, node));
                default -> output.accept(compileError(node, "Unhandled custom element: " + jsxElement.name()));
            }
        }
    }

    private HtmlNode compileBlockImage(WebPageCompileContext context, MdxJsxElementFields jsxElement,
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
        var srcset = new StringBuilder(asset2x);
        if (src4x != null) {
            srcset.append(", ").append(context.resolveAssetPath(src4x)).append(" 2x");
        }
        if (src8x != null) {
            srcset.append(", ").append(context.resolveAssetPath(src8x)).append(" 4x");
        }
        return HtmlNode.tag("img")
                .setAttribute("srcset", srcset.toString())
                .setAttribute("src", asset2x)
                .setAttribute("alt", "")
                .setStyles(Map.of("width", guiScaledDimension(width), "height", guiScaledDimension(height)));
    }

    private HtmlNode compileItemLink(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node) {
        var tooltipMode = MdxAttrs.getEnum(jsxElement, "tooltip", TooltipMode.ICON);
        var id = MdxAttrs.getString(jsxElement, "id", null);
        if (id == null) {
            return compileError(node, "ItemLink is missing id property");
        }

        // Markdown Formatting can insert whitespace into MDX attributes
        id = id.replaceAll("\\s+", "");

        // Without explicit content, the item name will be used
        HtmlFragment innerContent = null;
        if (!node.children().isEmpty()) {
            innerContent = compileChildren(context, node);
        }

        return createItemLink(context, node, id, tooltipMode, innerContent);
    }

    HtmlNode createItemLink(WebPageCompileContext context, MdAstParent<?> node, String id,
            TooltipMode tooltipMode, @Nullable HtmlFragment innerContent) {

        id = guide.resolveId(id);

        var pageId = guide.getPageIdForItem(id);
        var itemInfo = guide.tryGetItemInfo(id);
        if (itemInfo == null) {
            return compileError(node, "Missing item " + id);
        }

        if (innerContent == null) {
            innerContent = new HtmlFragment();
            innerContent.append(HtmlNode.text(itemInfo.displayName));
        }

        // Do not render a link if we're already on that page, or there is no link
        HtmlTag content;
        if (pageId == null || context.pageId().equals(pageId)) {
            content = HtmlNode.tag("span").setClassName("item-link")
                    .append(innerContent);
        } else {
            content = HtmlNode.tag("a")
                    .setAttribute("href", context.getRelativePagePath(pageId))
                    .append(innerContent);
        }

        return makeItemTooltip(context, itemInfo, tooltipMode, content);
    }

    private HtmlNode compileItemImage(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node) {
        var id = MdxAttrs.getString(jsxElement, "id", null);
        if (id == null) {
            return compileError(node, "ItemImage is missing id property");
        }
        var scale = MdxAttrs.getFloat(jsxElement, "scale", 1.0f);
        var itemInfo = guide.tryGetItemInfo(id);
        if (itemInfo == null) {
            return compileError(node, "Missing item " + id);
        }

        return HtmlNode.tag("img")
                .setAttribute("width", Math.round(32 * scale))
                .setAttribute("height", Math.round(32 * scale))
                .setAttribute("src", context.resolveAssetPath(itemInfo.icon))
                .setAttribute("alt", "")
                .setAttribute("aria-description", itemInfo.displayName);
    }

    private HtmlNode compileItemIcon(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node) {
        var id = MdxAttrs.getString(jsxElement, "id", null);
        if (id == null) {
            return compileError(node, "ItemIcon is missing id property");
        }
        var nolink = MdxAttrs.getBoolean(jsxElement, "nolink", false);

        return createItemIcon(context, node, id, nolink);
    }

    HtmlNode createItemIcon(WebPageCompileContext context, MdAstParent<?> node, String id, boolean nolink) {
        var itemInfo = guide.tryGetItemInfo(id);
        if (itemInfo == null) {
            return compileError(node, "Missing item " + id);
        }

        var icon = HtmlNode.tag("img")
                .setClassName("item-icon")
                .setAttribute("src", context.resolveAssetPath(itemInfo.icon))
                .setAttribute("alt", "")
                .setAttribute("aria-description", itemInfo.displayName);

        if (!nolink) {
            return createItemLink(context, node, id, TooltipMode.TEXT, new HtmlFragment(icon));
        } else {
            return icon;
        }
    }

    private HtmlNode compileItemGrid(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node) {
        return HtmlNode.tag("div", compileChildren(context, node))
                .setClassName("layout-item-grid");
    }

    private void compileRecipe(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node,
            Consumer<HtmlNode> output) {
        var id = MdxAttrs.getString(jsxElement, "id", null);
        if (id == null) {
            output.accept(compileError(node, "Missing id"));
            return;
        }
        var recipe = guide.getRecipeById(id);
        if (recipe == null) {
            compileMissingRecipe(jsxElement, node, "Missing recipe: " + id, output);
            return;
        }

        // The recipe box itself is already wrapped in a recipe container
        compileRecipeInner(context, node, recipe, output);
    }

    private void compileRecipeFor(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node, Consumer<HtmlNode> output) {
        var id = MdxAttrs.getString(jsxElement, "id", null);
        if (id == null) {
            output.accept(compileError(node, "Missing id"));
            return;
        }

        var recipes = context.guide().getRecipesForItem(id);

        if (recipes.isEmpty()) {
            compileMissingRecipe(jsxElement, node, "No recipes for " + id, output);
            return;
        }

        var container = HtmlNode.tag("div").setClassName("recipe-container");
        compileRecipeInner(context, node, recipes.getFirst(), container::append);
        output.accept(container);
    }

    private void compileRecipesFor(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node, Consumer<HtmlNode> output) {
        var id = MdxAttrs.getString(jsxElement, "id", null);
        if (id == null) {
            output.accept(compileError(node, "Missing id"));
            return;
        }

        var recipes = context.guide().getRecipesForItem(id);

        if (recipes.isEmpty()) {
            compileMissingRecipe(jsxElement, node, "No recipes for " + id, output);
            return;
        }

        var container = HtmlNode.tag("div").setClassName("recipe-container");
        for (var recipe : recipes) {
            compileRecipeInner(context, node, recipe, container::append);
        }
        output.accept(container);
    }

    /**
     * Mirrors the in-game behavior of the fallbackText attribute: when it's missing, an error is shown, when it's
     * empty, nothing is shown.
     */
    private void compileMissingRecipe(MdxJsxElementFields jsxElement, MdAstParent<?> node, String error,
            Consumer<HtmlNode> output) {
        var fallbackText = MdxAttrs.getString(jsxElement, "fallbackText", null);
        if (fallbackText == null) {
            output.accept(compileError(node, error));
        } else if (!fallbackText.isEmpty()) {
            output.accept(HtmlNode.tag("p").append(fallbackText));
        }
    }

    private void compileRecipeInner(WebPageCompileContext context, MdAstParent<?> node, ExportedRecipe recipe,
            Consumer<HtmlNode> output) {
        var renderer = recipeRenderersByType.get(recipe.type());
        if (renderer == null) {
            output.accept(compileError(node, "Can't handle recipe type " + recipe.type()));
            return;
        }

        var recipeRenderContext = new RecipeWebRenderingContextImpl(this, context, node, recipe, output);
        renderer.render(recipeRenderContext, recipe);
    }

    private HtmlNode compileCategoryIndex(WebPageCompileContext context, MdxJsxElementFields jsxElement,
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

        var listTag = HtmlNode.tag("ul");
        for (var page : pages) {
            var pageLink = HtmlNode.tag("a")
                    .setAttribute("href", context.getRelativePagePath(page.getKey()))
                    .append(page.getValue().title);
            listTag.append(HtmlNode.tag("li", pageLink));
        }
        return listTag;
    }

    private HtmlNode compileSubPages(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node) {
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

        var list = HtmlNode.tag("ul").setClassName("sub-pages");
        for (var n : navNodes) {
            var listItem = HtmlNode.tag("li");

            if (attributes.icons && n.icon != null) {
                var itemInfo = guide.tryGetItemInfo(n.icon);
                if (itemInfo != null) {
                    listItem.append(HtmlNode.tag("img")
                            .setClassName("page-icon")
                            .setAttribute("alt", "")
                            .setAttribute("src", context.resolveAssetPath(itemInfo.icon)));
                }
            }
            listItem.append(HtmlNode.tag("a")
                    .setAttribute("href", context.getRelativePagePath(n.pageId))
                    .append(n.title));
            list.append(listItem);
        }
        return list;
    }

    private HtmlNode compileColor(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
        var id = MdxAttrs.getString(jsxElement, "id", null);
        String cssColor;
        if (id != null) {
            // Custom symbolic colors from mods are not available for the website
            SymbolicColor symbolicColor;
            try {
                symbolicColor = SymbolicColor.valueOf(id.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return compileError(node, "Cannot resolve symbolic color");
            }
            // The website uses a dark theme
            cssColor = HtmlUtils.toCssColor(symbolicColor.resolve(LightDarkMode.DARK_MODE));
        } else {
            var color = MdxAttrs.getString(jsxElement, "color", null);
            if (color == null) {
                return compileError(node, "Must either specify 'id' or 'color' attribute");
            }
            cssColor = HtmlUtils.toCssColor(color);
            if (cssColor == null) {
                return compileError(node, "Malformed color value");
            }
        }

        return HtmlNode.tag("span", compileChildren(context, node))
                .setStyles(Map.of("color", cssColor));
    }

    private HtmlNode compileKeyBind(MdxJsxElementFields jsxElement, MdAstParent<?> node) {
        var id = MdxAttrs.getString(jsxElement, "id", null);
        if (id == null) {
            return compileError(node, "Attribute id is required.");
        }
        // The key name is resolved during export. Fall back to the id for older exports.
        var keyName = MdxAttrs.getString(jsxElement, "keyName", id);
        return HtmlNode.tag("kbd").append(keyName);
    }

    private HtmlNode compileCommandLink(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node) {
        var command = MdxAttrs.getString(jsxElement, "command", "");
        if (!command.startsWith("/")) {
            return compileError(node, "command must start with /");
        }
        var title = MdxAttrs.getString(jsxElement, "title", "");

        // Commands can't be run from the website, so just show which command would be run
        var tooltip = title.isEmpty() ? command : title + "\n" + command;
        return HtmlNode.tag("span", compileChildren(context, node))
                .setClassName("command-link")
                .setAttribute("title", tooltip);
    }

    private HtmlNode compileFloatingImage(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node) {
        // The src is rewritten during export
        var src = MdxAttrs.getString(jsxElement, "src", null);
        if (src == null) {
            return compileError(node, "src is required");
        }
        var align = MdxAttrs.getString(jsxElement, "align", "left");
        if (!align.equals("left") && !align.equals("right")) {
            return compileError(node, "Invalid align. Must be left or right.");
        }
        var title = MdxAttrs.getString(jsxElement, "title", null);

        var img = HtmlNode.tag("img")
                .setClassName("floating-image-" + align)
                .setAttribute("src", context.resolveAssetPath(src))
                .setAttribute("alt", Objects.requireNonNullElse(title, ""));
        if (title != null) {
            img.setAttribute("title", title);
        }
        return img;
    }

    private HtmlNode compileColumn(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
        return HtmlNode.tag("div")
                .setClassName("layout-column")
                .append(compileChildren(context, node));
    }

    private HtmlNode compileRow(WebPageCompileContext context, MdxJsxElementFields jsxElement, MdAstParent<?> node) {
        return HtmlNode.tag("div")
                .setClassName("layout-row")
                .append(compileChildren(context, node));
    }

    // These have to match the types in web/src/model-viewer/modelViewer.ts
    private record ExportedBoxAnnotation(String type, float[] minCorner, float[] maxCorner, String color,
            float thickness, String contentTemplateId, boolean alwaysOnTop) {
    }

    private record ExportedLineAnnotation(String type, float[] from, float[] to, String color,
            float thickness, String contentTemplateId, boolean alwaysOnTop) {
    }

    private record ExportedOverlayAnnotation(String type, float[] position, String color, String contentTemplateId) {
    }

    private void compileGameScene(WebPageCompileContext context, MdxJsxElementFields jsxElement,
            MdAstParent<?> node, Consumer<HtmlNode> output) {
        var errors = new ArrayList<HtmlNode>();
        var attributes = JsxAttributeMapper.map(jsxElement, GameSceneAttributes.class);

        var inWorldAnnotations = new ArrayList<Record>();
        var overlayAnnotations = new ArrayList<ExportedOverlayAnnotation>();

        // Process child elements of GameScene
        for (var child : node.children()) {
            // Like the in-game compiler, we're only interested in JSX children
            if (!(child instanceof MdxJsxFlowElement flowElement)) {
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
                    inWorldAnnotations.add(new ExportedBoxAnnotation(
                            "box",
                            toArray(boxAttributes.min),
                            toArray(boxAttributes.max),
                            boxAttributes.color,
                            boxAttributes.thickness,
                            createAnnotationContent(context, flowElement),
                            boxAttributes.alwaysOnTop));
                }
                case "BlockAnnotation" -> {
                    record BlockAnnotation(
                            Vector3f pos,
                            @DefaultValue("white") String color,
                            @DefaultValue("false") boolean alwaysOnTop) {
                    }
                    var blockAttributes = JsxAttributeMapper.map(flowElement, BlockAnnotation.class);
                    // Same as InWorldBoxAnnotation.forBlock
                    inWorldAnnotations.add(new ExportedBoxAnnotation(
                            "box",
                            toArray(blockAttributes.pos),
                            toArray(new Vector3f(blockAttributes.pos).add(1, 1, 1)),
                            blockAttributes.color,
                            InWorldBoxAnnotation.DEFAULT_THICKNESS,
                            createAnnotationContent(context, flowElement),
                            blockAttributes.alwaysOnTop));
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
                    inWorldAnnotations.add(new ExportedLineAnnotation(
                            "line",
                            toArray(lineAttributes.from),
                            toArray(lineAttributes.to),
                            lineAttributes.color,
                            lineAttributes.thickness,
                            createAnnotationContent(context, flowElement),
                            lineAttributes.alwaysOnTop));
                }
                case "DiamondAnnotation" -> {
                    record DiamondAnnotation(Vector3f pos, @DefaultValue("transparent") String color) {
                    }
                    var diamondAttributes = JsxAttributeMapper.map(flowElement, DiamondAnnotation.class);
                    overlayAnnotations.add(new ExportedOverlayAnnotation(
                            "overlay",
                            toArray(diamondAttributes.pos),
                            diamondAttributes.color,
                            createAnnotationContent(context, flowElement)));
                }
                default -> errors.add(compileError(node, "Unsupported child tag " + childTagName));
            }
        }

        var placeholderImage = HtmlNode.tag("img")
                .setClassName("game-scene")
                .setStyles(Map.of(
                        "width", guiScaledDimension(attributes.width()),
                        "height", guiScaledDimension(attributes.height())))
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

        output.accept(placeholderImage);
        errors.forEach(output);
    }

    private static float[] toArray(Vector3f vector) {
        return new float[] { vector.x, vector.y, vector.z };
    }

    /**
     * Annotations show their children as tooltip content, which is only included once per page as a template.
     */
    @Nullable
    private String createAnnotationContent(WebPageCompileContext context, MdxJsxFlowElement annotationElement) {
        var content = compileChildren(context, annotationElement);
        if (content.isEmpty()) {
            return null;
        }
        return context.templates().create(content);
    }

    enum TooltipMode implements StringRepresentable {
        TEXT,
        ICON;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private HtmlTag makeItemTooltip(WebPageCompileContext context,
            ItemInfoJson itemInfo,
            @Nullable TooltipMode mode,
            HtmlTag content) {
        mode = Objects.requireNonNullElse(mode, TooltipMode.ICON);

        HtmlTag tooltipContent;
        if (mode == TooltipMode.ICON) {
            tooltipContent = HtmlNode.tag("img")
                    .setClassName("item-icon")
                    .setAttribute("src", context.resolveAssetPath(itemInfo.icon))
                    .setAttribute("alt", "")
                    .setAttribute("aria-description", itemInfo.displayName);
        } else {
            tooltipContent = HtmlNode.tag("span")
                    .setClassName("item-name")
                    .setAttribute("data-rarity", itemInfo.rarity)
                    .append(itemInfo.displayName);
        }

        var templateId = context.templates().create(tooltipContent);
        return HtmlNode.tag("span", content)
                .setClassName("minecraft-tooltip")
                .setAttribute("data-template", templateId);
    }

    /**
     * We use this to collect HTML5 template tags that need to be uniquely identified and added to the body tag before
     * we write the page. It also handles de-duplicating template content if it is used multiple times.
     */
    static class TemplateContainer {
        private int counter = 1;
        private final List<HtmlTag> templates = new ArrayList<>();
        private final Map<String, String> templateContent = new HashMap<>();

        public String create(HtmlNode content) {
            return create(new HtmlFragment(content));
        }

        public String create(HtmlFragment content) {
            // De-duplicate based on the resulting HTML
            var htmlContent = content.outerHtml();
            var existingId = templateContent.get(htmlContent);
            if (existingId != null) {
                return existingId;
            }

            var id = "tmpl-" + (counter++);
            templates.add(HtmlNode.tag("template", content).setAttribute("id", id));
            templateContent.put(htmlContent, id);
            return id;
        }
    }

}
