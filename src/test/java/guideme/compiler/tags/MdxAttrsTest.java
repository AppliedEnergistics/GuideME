package guideme.compiler.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import guideme.compiler.PageCompiler;
import guideme.document.LytErrorSink;
import guideme.internal.GuideME;
import guideme.libs.mdast.mdx.model.MdxJsxAttribute;
import guideme.libs.mdast.mdx.model.MdxJsxAttributeNode;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.mdx.model.MdxJsxTextElement;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
class MdxAttrsTest {
    @Mock
    PageCompiler compiler;

    @Mock
    LytErrorSink errorSink;

    private MdxJsxElementFields makeEl(String... keyValuePairs) {
        assert keyValuePairs.length % 2 == 0;

        var attrMap = new HashMap<String, String>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            var key = keyValuePairs[i];
            var value = keyValuePairs[i + 1];
            attrMap.put(key, value);
        }
        return new MdxJsxTextElement("irrelevant", attrMap.entrySet().stream()
                .<MdxJsxAttributeNode>map(entry -> {
                    var attr = new MdxJsxAttribute();
                    attr.name = entry.getKey();
                    attr.setValue(entry.getValue());
                    return attr;
                })
                .toList());
    }

    @Test
    void testVector3() {
        var el = makeEl(
                "from",
                "");
        MdxAttrs.getVector3(compiler, errorSink, el, "from", null);
    }

    @Test
    void testBooleanAttrsWithExpression() throws Exception {
        var parsedPage = PageCompiler.parse("ignored", "en_us", GuideME.makeId("test"),
                new ByteArrayInputStream("<Tag attr={true} />".getBytes()));
        var firstTag = parsedPage.getAstRoot().children().getFirst();
        assertTrue(MdxAttrs.getBoolean((MdxJsxElementFields) firstTag, "attr", false));
    }

    @Test
    void testBooleanAttrsWithoutValue() throws Exception {
        var parsedPage = PageCompiler.parse("ignored", "en_us", GuideME.makeId("test"),
                new ByteArrayInputStream("<Tag attr />".getBytes()));
        var firstTag = parsedPage.getAstRoot().children().getFirst();
        assertTrue(MdxAttrs.getBoolean((MdxJsxElementFields) firstTag, "attr", false));
    }

    @Test
    void testBooleanAttrsWithNullExpressionValue() throws Exception {
        var parsedPage = PageCompiler.parse("ignored", "en_us", GuideME.makeId("test"),
                new ByteArrayInputStream("<Tag attr={null} />".getBytes()));
        var firstTag = parsedPage.getAstRoot().children().getFirst();
        var e = assertThrows(Exception.class, () -> MdxAttrs.getBoolean((MdxJsxElementFields) firstTag, "attr", false));
        assertThat(e).hasMessage("attr should be {true} or {false}");
    }
}
