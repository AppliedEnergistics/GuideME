package guideme.internal.siteexport.mdastpostprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guideme.compiler.PageCompiler;
import guideme.document.block.LytNode;
import guideme.document.block.LytParagraph;
import guideme.extensions.ExtensionCollection;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import guideme.libs.mdast.model.MdAstRoot;
import guideme.libs.mdast.model.MdAstText;
import guideme.siteexport.NodeSelector;
import guideme.siteexport.PageExportContext;
import guideme.siteexport.PageExportProcessor;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class PageExportProcessingTest {
    private static final Identifier PAGE_ID = Identifier.fromNamespaceAndPath("testmod", "page.md");

    private final List<Runnable> cleanupCallbacks = new ArrayList<>();

    private Function<MdAstNode, List<LytNode>> layoutNodes = node -> List.of();

    @Test
    void testSymbolicColorIsResolved() throws Exception {
        var root = parse("<Color id=\"link\">text</Color>");
        var original = toJson(root);

        process(root, new ColorExportProcessor());

        var paragraph = (MdAstParent<?>) root.children().getFirst();
        var color = (MdxJsxElementFields) paragraph.children().getFirst();
        assertThat(color.getAttributeString("color", null)).isEqualTo("#FF00D5FF");
        assertThat(color.hasAttribute("id")).isFalse();

        undo();
        assertThat(toJson(root)).isEqualTo(original);
    }

    @Test
    void testUnknownSymbolicColorIsKept() throws Exception {
        var root = parse("<Color id=\"unknown\">text</Color>");
        var original = toJson(root);

        process(root, new ColorExportProcessor());

        assertThat(toJson(root)).isEqualTo(original);
    }

    @Test
    void testUnwrapProcessesTheUnwrappedContent() throws Exception {
        var root = parse("""
                Before

                <OptionalSection show={true}>
                  Inside <Color id="link">text</Color>
                </OptionalSection>

                <OptionalSection show={false}>
                  Removed
                </OptionalSection>

                After""");
        var original = toJson(root);

        process(root, optionalSection(), new ColorExportProcessor());

        var children = root.children();
        assertThat(children).hasSize(3);
        assertThat(textOf(children.get(0))).isEqualTo("Before");
        assertThat(textOf(children.get(1))).isEqualTo("Inside text");
        assertThat(textOf(children.get(2))).isEqualTo("After");
        // The color inside of the unwrapped section was processed as well
        assertThat(toJson(root)).contains("#FF00D5FF");

        undo();
        assertThat(toJson(root)).isEqualTo(original);
    }

    @Test
    void testParentAndLayoutNodesAreAvailable() throws Exception {
        var root = parse("""
                <Outer>
                  <Inner />
                </Outer>""");
        var outer = (MdAstNode) root.children().getFirst();
        var layoutNode = new LytParagraph();
        layoutNodes = node -> node == outer ? List.of(layoutNode) : List.of();

        var seen = new ArrayList<Object>();
        process(root, processor("Inner", (context, node) -> {
            var parent = context.getParent(node);
            seen.add(parent);
            seen.addAll(context.getLayoutNodes(parent));
        }));

        assertThat(seen).containsExactly(outer, layoutNode);
    }

    @Test
    void testInvalidReplacementIsRejected() throws Exception {
        var root = parse("Some <Inline>text</Inline>");

        // A paragraph cannot be placed inside of another paragraph
        var processor = processor("Inline",
                (context, node) -> context.replace(node, List.of((MdAstNode) root.children().getFirst())));

        assertThatThrownBy(() -> process(root, processor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot contain");
    }

    /**
     * Behaves like an element that only shows its content depending on a condition known in-game.
     */
    private static PageExportProcessor optionalSection() {
        return processor("OptionalSection", (context, node) -> {
            var element = (MdxJsxElementFields) node;
            if (element.getAttribute("show").getExpressionValue().equals("true")) {
                context.unwrap(node);
            } else {
                context.remove(node);
            }
        });
    }

    private static PageExportProcessor processor(String tagName, BiConsumer<PageExportContext, MdAstNode> action) {
        return new PageExportProcessor() {
            @Override
            public NodeSelector getSelector() {
                return NodeSelector.element(tagName);
            }

            @Override
            public void process(PageExportContext context, MdAstNode node) {
                action.accept(context, node);
            }
        };
    }

    private void process(MdAstRoot root, PageExportProcessor... processors) {
        var extensions = ExtensionCollection.builder();
        for (var processor : processors) {
            extensions.add(PageExportProcessor.EXTENSION_POINT, processor);
        }
        new PageExportProcessing(null, PAGE_ID, extensions.build(), cleanupCallbacks::add, layoutNodes)
                .process(root);
    }

    private void undo() {
        cleanupCallbacks.forEach(Runnable::run);
    }

    private static MdAstRoot parse(String markdown) {
        return PageCompiler.parse("testmod", PAGE_ID, markdown).getAstRoot();
    }

    private static String textOf(Object node) {
        var text = new StringBuilder();
        ((MdAstNode) node).visit(new guideme.libs.mdast.MdAstVisitor() {
            @Override
            public Result beforeNode(MdAstNode node) {
                if (node instanceof MdAstText textNode) {
                    text.append(textNode.value);
                }
                return Result.CONTINUE;
            }
        });
        return text.toString().trim();
    }

    private static String toJson(MdAstNode node) throws IOException {
        var writer = new StringWriter();
        var jsonWriter = new com.google.gson.stream.JsonWriter(writer);
        node.toJson(jsonWriter);
        return writer.toString();
    }
}
