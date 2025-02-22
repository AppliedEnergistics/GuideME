package guideme.libs.mdast.gfmstrikethrough;

import guideme.libs.mdast.AbstractMdAstTest;
import guideme.libs.mdast.MdAst;
import guideme.libs.mdast.MdastOptions;
import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.micromark.extensions.gfmstrikethrough.GfmStrikethroughSyntax;
import org.junit.jupiter.api.Test;

class GfmStrikethroughMdastExtensionTest extends AbstractMdAstTest {
    private MdAstNode fromMarkdown(String markdown) {
        var options = new MdastOptions();
        options.withMdastExtension(GfmStrikethroughMdastExtension.INSTANCE);
        options.withSyntaxExtension(GfmStrikethroughSyntax.INSTANCE);
        return removePosition(MdAst.fromMarkdown(markdown, options));
    }

    @Test
    void testStrikethroughFromMarkdown() {
        var tree = fromMarkdown("a ~~b~~ c.");

        assertJsonEquals(tree, """
                {
                  "type": "root",
                  "children": [
                    {
                      "type": "paragraph",
                      "children": [
                        {"type": "text", "value": "a "},
                        {"type": "delete", "children": [{"type": "text", "value": "b"}]},
                        {"type": "text", "value": " c."}
                      ]
                    }
                  ]
                }
                """);
    }

    @Test
    void testStrikethroughWithEolsFromMarkdown() {
        var tree = fromMarkdown("a ~~b\nc~~ d.");

        assertJsonEquals(tree, """
                {
                  "type": "root",
                  "children": [
                    {
                      "type": "paragraph",
                      "children": [
                        {"type": "text", "value": "a "},
                        {"type": "delete", "children": [{"type": "text", "value": "b\nc"}]},
                        {"type": "text", "value": " d."}
                      ]
                    }
                  ]
                }
                """);
    }
}
