package guideme.libs.mdast.mdx.model;

import java.io.IOException;

import com.google.gson.stream.JsonWriter;

import guideme.libs.unist.UnistNode;

/**
 * Potential attributes of {@link MdxJsxElementFields}
 */
public interface MdxJsxAttributeNode extends UnistNode {
    void toJson(JsonWriter writer) throws IOException;
}
