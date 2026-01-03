package guideme.libs.mdast.mdx.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonWriter;
import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import guideme.libs.mdast.model.MdAstPhrasingContent;
import guideme.libs.mdast.model.MdAstStaticPhrasingContent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class MdxJsxTextElement extends MdAstParent<MdAstPhrasingContent>
        implements MdxJsxElementFields, MdAstStaticPhrasingContent {
    public static final String TYPE = "mdxJsxTextElement";
    private String name;
    private final List<MdxJsxAttributeNode> attributes;

    public MdxJsxTextElement() {
        this("", new ArrayList<>());
    }

    public MdxJsxTextElement(String name, List<MdxJsxAttributeNode> attributes) {
        super(TYPE);
        this.name = name;
        this.attributes = attributes;
    }

    @Override
    public @Nullable String name() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public List<MdxJsxAttributeNode> attributes() {
        return attributes;
    }

    @Override
    protected Class<MdAstPhrasingContent> childClass() {
        return MdAstPhrasingContent.class;
    }

    @Override
    protected void writeJson(JsonWriter writer) throws IOException {
        super.writeJson(writer);
        writer.name("name").value(name);
        writer.name("attributes");
        writer.beginArray();
        for (var attribute : attributes) {
            attribute.toJson(writer);
        }
        writer.endArray();
    }

    @Override
    protected void readJson(JsonObject jsonObject) throws IOException {
        super.readJson(jsonObject);
        this.name = readJsonString(jsonObject, "name");
        this.attributes.clear();
        for (var attributeEl : jsonObject.getAsJsonArray("attributes")) {
            var mdxNode = MdAstNode.fromJson(attributeEl.getAsJsonObject());
            if (mdxNode instanceof MdxJsxAttributeNode attributeNode) {
                this.attributes.add(attributeNode);
            } else {
                throw new JsonSyntaxException("Unexpected attribute node " + mdxNode.type());
            }
        }
    }
}
