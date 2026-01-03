package guideme.libs.mdast.mdx.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonWriter;
import guideme.libs.mdast.model.MdAstNode;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

public class MdxJsxAttribute extends MdAstNode implements MdxJsxAttributeNode {
    public static final String TYPE = "mdxJsxAttribute";
    public String name = "";
    @Nullable
    private Object value;

    public MdxJsxAttribute() {
        super(TYPE);
    }

    public MdxJsxAttribute(String name, @Nullable Object value) {
        this();
        this.name = name;
        this.value = value;
    }

    @Override
    public void toText(StringBuilder buffer) {
    }

    public void setExpression(String expression) {
        var node = new MdxJsxAttributeValueExpression();
        node.value = expression;
        this.value = node;
    }

    public boolean hasExpressionValue() {
        return value instanceof MdxJsxAttributeValueExpression;
    }

    public boolean hasStringValue() {
        return value instanceof String;
    }

    public String getStringValue() {
        return (String) value;
    }

    public String getExpressionValue() {
        return ((MdxJsxAttributeValueExpression) value).value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    protected void writeJson(JsonWriter writer) throws IOException {
        super.writeJson(writer);
        writer.name("name").value(name);
        writer.name("value");
        if (value == null) {
            writer.nullValue();
        } else if (value instanceof String string) {
            writer.value(string);
        } else if (value instanceof MdxJsxAttributeValueExpression expression) {
            expression.toJson(writer);
        } else {
            throw new IllegalStateException("Invalid attribute value type: " + value);
        }
    }

    @Override
    protected void readJson(JsonObject jsonObject) throws IOException {
        super.readJson(jsonObject);
        name = readJsonString(jsonObject, "name");
        var value = jsonObject.get("value");
        if (value.isJsonNull()) {
            this.value = null;
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            this.value = value.getAsString();
        } else if (value.isJsonObject()) {
            this.value = MdxJsxAttributeValueExpression.fromJson(value.getAsJsonObject());
        } else {
            throw new JsonSyntaxException("Unexpected value for expression value");
        }
    }
}
