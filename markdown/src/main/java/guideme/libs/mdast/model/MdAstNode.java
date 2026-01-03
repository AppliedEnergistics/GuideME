package guideme.libs.mdast.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonWriter;
import guideme.libs.mdast.MdAstVisitor;
import guideme.libs.mdast.MdAstYamlFrontmatter;
import guideme.libs.mdast.gfm.model.GfmTable;
import guideme.libs.mdast.gfm.model.GfmTableCell;
import guideme.libs.mdast.gfm.model.GfmTableRow;
import guideme.libs.mdast.gfmstrikethrough.MdAstDelete;
import guideme.libs.mdast.mdx.model.MdxJsxAttribute;
import guideme.libs.mdast.mdx.model.MdxJsxAttributeValueExpression;
import guideme.libs.mdast.mdx.model.MdxJsxExpressionAttribute;
import guideme.libs.mdast.mdx.model.MdxJsxFlowElement;
import guideme.libs.mdast.mdx.model.MdxJsxTextElement;
import guideme.libs.unist.UnistNode;
import guideme.libs.unist.UnistPosition;
import java.io.IOException;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

public abstract class MdAstNode implements UnistNode {
    private final String type;
    public Object data;
    public MdAstPosition position;

    public MdAstNode(String type) {
        this.type = type;
    }

    @Override
    public final String type() {
        return type;
    }

    @Override
    public @Nullable Object data() {
        return data;
    }

    @Override
    public @Nullable UnistPosition position() {
        return position;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public abstract void toText(StringBuilder buffer);

    public final String toText() {
        var builder = new StringBuilder();
        toText(builder);
        return builder.toString();
    }

    public final void toJson(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("type").value(type());
        writeJson(writer);
        if (position != null) {
            writer.name("position");
            position.writeJson(writer);
        }
        writer.endObject();
    }

    protected void writeJson(JsonWriter writer) throws IOException {
    }

    public static MdAstNode fromJson(JsonObject jsonObject) throws IOException {
        var typeEl = jsonObject.get("type");
        if (typeEl == null || !typeEl.isJsonPrimitive()) {
            throw new JsonSyntaxException("Missing required 'type' property.");
        }

        var type = typeEl.getAsJsonPrimitive().getAsString();
        var node = switch (type) {
            case MdAstRoot.TYPE -> new MdAstRoot();
            case MdAstImage.TYPE -> new MdAstImage();
            case MdAstParagraph.TYPE -> new MdAstParagraph();
            case MdAstList.TYPE -> new MdAstList();
            case MdAstHeading.TYPE -> new MdAstHeading();
            case MdxJsxTextElement.TYPE -> new MdxJsxTextElement();
            case MdxJsxFlowElement.TYPE -> new MdxJsxFlowElement();
            case MdAstLink.TYPE -> new MdAstLink();
            case MdAstBlockquote.TYPE -> new MdAstBlockquote();
            case MdAstStrong.TYPE -> new MdAstStrong();
            case MdAstListItem.TYPE -> new MdAstListItem();
            case MdAstDelete.TYPE -> new MdAstDelete();
            case MdAstEmphasis.TYPE -> new MdAstEmphasis();
            case MdAstLinkReference.TYPE -> new MdAstLinkReference();
            case GfmTable.TYPE -> new GfmTable();
            case GfmTableCell.TYPE -> new GfmTableCell();
            case GfmTableRow.TYPE -> new GfmTableRow();
            case MdAstYamlFrontmatter.TYPE -> new MdAstYamlFrontmatter();
            case MdAstDefinition.TYPE -> new MdAstDefinition();
            case MdAstText.TYPE -> new MdAstText();
            case MdxJsxExpressionAttribute.TYPE -> new MdxJsxExpressionAttribute();
            case MdAstInlineCode.TYPE -> new MdAstInlineCode();
            case MdxJsxAttributeValueExpression.TYPE -> new MdxJsxAttributeValueExpression();
            case MdAstCode.TYPE -> new MdAstCode();
            case MdAstHTML.TYPE -> new MdAstHTML();
            case MdAstImageReference.TYPE -> new MdAstImageReference();
            case MdAstBreak.TYPE -> new MdAstBreak();
            case MdxJsxAttribute.TYPE -> new MdxJsxAttribute();
            case MdAstThematicBreak.TYPE -> new MdAstThematicBreak();
            default -> throw new JsonSyntaxException("Unknown MdAst node type: " + type);
        };

        node.readJson(jsonObject);

        return node;
    }

    protected void readJson(JsonObject jsonObject) throws IOException {
    }

    public final MdAstVisitor.Result visit(MdAstVisitor visitor) {
        var result = visitor.beforeNode(this);
        if (result == MdAstVisitor.Result.STOP) {
            return result;
        }
        if (result != MdAstVisitor.Result.SKIP_CHILDREN) {
            if (visitChildren(visitor) == MdAstVisitor.Result.STOP) {
                return MdAstVisitor.Result.STOP;
            }
        }
        return visitor.afterNode(this);
    }

    protected MdAstVisitor.Result visitChildren(MdAstVisitor visitor) {
        return MdAstVisitor.Result.CONTINUE;
    }

    /**
     * Remove children matching the given predicate.
     */
    public void removeChildren(Predicate<MdAstNode> node, boolean recursive) {
    }

    protected String readJsonString(JsonObject object, String name, String defaultValue) {
        var member = object.getAsJsonPrimitive(name);
        if (member == null) {
            return defaultValue;
        }
        return member.getAsString();
    }

    protected String readJsonString(JsonObject object, String name) {
        var member = object.getAsJsonPrimitive(name);
        if (member == null || !member.isString()) {
            throw new JsonSyntaxException("Missing property " + name);
        }
        return member.getAsString();
    }

    protected int readJsonInt(JsonObject object, String name) {
        var member = object.getAsJsonPrimitive(name);
        if (member == null || !member.isNumber()) {
            throw new JsonSyntaxException("Missing property " + name);
        }
        return member.getAsInt();
    }

    protected boolean readJsonBoolean(JsonObject object, String name) {
        var member = object.getAsJsonPrimitive(name);
        if (member == null || !member.isBoolean()) {
            throw new JsonSyntaxException("Missing property " + name);
        }
        return member.getAsBoolean();
    }

    @Override
    public String toString() {
        return type;
    }
}
