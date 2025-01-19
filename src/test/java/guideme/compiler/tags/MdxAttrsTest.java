package guideme.compiler.tags;

import guideme.compiler.PageCompiler;
import guideme.document.LytErrorSink;
import guideme.libs.mdast.mdx.model.MdxJsxAttribute;
import guideme.libs.mdast.mdx.model.MdxJsxAttributeNode;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.mdx.model.MdxJsxTextElement;
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
}
