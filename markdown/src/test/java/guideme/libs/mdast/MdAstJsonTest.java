package guideme.libs.mdast;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import guideme.libs.mdast.gfm.GfmTableMdastExtension;
import guideme.libs.mdast.gfm.model.GfmTable;
import guideme.libs.mdast.gfm.model.GfmTableCell;
import guideme.libs.mdast.gfm.model.GfmTableRow;
import guideme.libs.mdast.gfmstrikethrough.GfmStrikethroughMdastExtension;
import guideme.libs.mdast.gfmstrikethrough.MdAstDelete;
import guideme.libs.mdast.mdx.MdxMdastExtension;
import guideme.libs.mdast.mdx.model.MdxJsxAttribute;
import guideme.libs.mdast.mdx.model.MdxJsxAttributeValueExpression;
import guideme.libs.mdast.mdx.model.MdxJsxExpressionAttribute;
import guideme.libs.mdast.mdx.model.MdxJsxFlowElement;
import guideme.libs.mdast.mdx.model.MdxJsxTextElement;
import guideme.libs.mdast.model.MdAstBlockquote;
import guideme.libs.mdast.model.MdAstBreak;
import guideme.libs.mdast.model.MdAstCode;
import guideme.libs.mdast.model.MdAstDefinition;
import guideme.libs.mdast.model.MdAstEmphasis;
import guideme.libs.mdast.model.MdAstHTML;
import guideme.libs.mdast.model.MdAstHeading;
import guideme.libs.mdast.model.MdAstImage;
import guideme.libs.mdast.model.MdAstImageReference;
import guideme.libs.mdast.model.MdAstInlineCode;
import guideme.libs.mdast.model.MdAstLink;
import guideme.libs.mdast.model.MdAstLinkReference;
import guideme.libs.mdast.model.MdAstList;
import guideme.libs.mdast.model.MdAstListItem;
import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParagraph;
import guideme.libs.mdast.model.MdAstRoot;
import guideme.libs.mdast.model.MdAstStrong;
import guideme.libs.mdast.model.MdAstText;
import guideme.libs.mdast.model.MdAstThematicBreak;
import guideme.libs.mdx.MdxSyntax;
import guideme.libs.micromark.extensions.YamlFrontmatterSyntax;
import guideme.libs.micromark.extensions.gfm.GfmTableSyntax;
import guideme.libs.micromark.extensions.gfmstrikethrough.GfmStrikethroughSyntax;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that every node type survives a round trip through JSON without losing information.
 */
class MdAstJsonTest {
    /**
     * Uses all node types that can be parsed with MDX.
     */
    static final String MDX_DOCUMENT = """
            ---
            title: Test
            ---

            # Heading *emphasis* **strong** ~~deleted~~ `code`

            A [link](https://example.com "title") and ![image](image.png "title") with\\
            a [reference][ref] and ![image reference][ref].

            [ref]: https://example.com "Title"

            > Quote

            3. Ordered
            4. List

            - Loose

              List

            ```java meta
            code
            ```

            ***

            | Left | Center | Right | None |
            |:-----|:------:|------:|------|
            | a    | b      | c     | d    |

            <Element attr="value" expr={1 + 2} {...spread} flag>
              Inline <Inline attr="value">text</Inline>
            </Element>
            """;

    /**
     * MDX replaces HTML with JSX, so HTML needs a document without it.
     */
    static final String HTML_DOCUMENT = """
            <div>html</div>
            """;

    private static final Set<String> ALL_TYPES = Set.of(MdAstRoot.TYPE, MdAstImage.TYPE, MdAstParagraph.TYPE,
            MdAstList.TYPE, MdAstHeading.TYPE, MdxJsxTextElement.TYPE, MdxJsxFlowElement.TYPE, MdAstLink.TYPE,
            MdAstBlockquote.TYPE, MdAstStrong.TYPE, MdAstListItem.TYPE, MdAstDelete.TYPE, MdAstEmphasis.TYPE,
            MdAstLinkReference.TYPE, GfmTable.TYPE, GfmTableCell.TYPE, GfmTableRow.TYPE, MdAstYamlFrontmatter.TYPE,
            MdAstDefinition.TYPE, MdAstText.TYPE, MdxJsxExpressionAttribute.TYPE, MdAstInlineCode.TYPE,
            MdxJsxAttributeValueExpression.TYPE, MdAstCode.TYPE, MdAstHTML.TYPE, MdAstImageReference.TYPE,
            MdAstBreak.TYPE, MdxJsxAttribute.TYPE, MdAstThematicBreak.TYPE);

    private static final Pattern TYPE_PROPERTY = Pattern.compile("\"type\":\"(\\w+)\"");

    static MdAstNode parseMdx() {
        return MdAst.fromMarkdown(MDX_DOCUMENT, new MdastOptions()
                .withSyntaxExtension(MdxSyntax.INSTANCE)
                .withSyntaxExtension(YamlFrontmatterSyntax.INSTANCE)
                .withSyntaxExtension(GfmTableSyntax.INSTANCE)
                .withSyntaxExtension(GfmStrikethroughSyntax.INSTANCE)
                .withMdastExtension(MdxMdastExtension.INSTANCE)
                .withMdastExtension(YamlFrontmatterExtension.INSTANCE)
                .withMdastExtension(GfmTableMdastExtension.INSTANCE)
                .withMdastExtension(GfmStrikethroughMdastExtension.INSTANCE));
    }

    static MdAstNode parseHtml() {
        return MdAst.fromMarkdown(HTML_DOCUMENT, new MdastOptions());
    }

    static Stream<MdAstNode> documents() {
        return Stream.of(parseMdx(), parseHtml());
    }

    @Test
    void testDocumentsContainAllNodeTypes() throws IOException {
        var types = new TreeSet<String>();
        for (var document : documents().toList()) {
            var matcher = TYPE_PROPERTY.matcher(toJson(document));
            while (matcher.find()) {
                types.add(matcher.group(1));
            }
        }

        assertThat(types).containsExactlyInAnyOrderElementsOf(ALL_TYPES);
    }

    @ParameterizedTest
    @MethodSource("documents")
    void testRoundTripKeepsAllFields(MdAstNode document) throws IOException {
        var json = toJson(document);

        var deserialized = MdAstNode.fromJson(JsonParser.parseString(json).getAsJsonObject());

        assertThat(toJson(deserialized)).isEqualTo(json);
        // Compares the fields, which also catches fields that aren't written to JSON at all. The data of nodes is
        // never serialized.
        assertThat(deserialized)
                .usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes("(.*\\.)?data")
                .isEqualTo(document);
    }

    /**
     * Older exports wrote optional properties that weren't set as null.
     */
    @Test
    void testNullPropertiesAreReadAsMissing() throws IOException {
        var link = (MdAstLink) MdAstNode.fromJson(JsonParser.parseString("""
                {"type":"link","url":"https://example.com","title":null,"children":[]}""").getAsJsonObject());
        assertThat(link.url).isEqualTo("https://example.com");
        assertThat(link.title).isNull();

        var code = (MdAstCode) MdAstNode.fromJson(JsonParser.parseString("""
                {"type":"code","value":"code","lang":null,"meta":null}""").getAsJsonObject());
        assertThat(code.value).isEqualTo("code");
        assertThat(code.lang).isNull();
        assertThat(code.meta).isNull();
    }

    static String toJson(MdAstNode node) throws IOException {
        var writer = new StringWriter();
        node.toJson(new JsonWriter(writer));
        return writer.toString();
    }
}
