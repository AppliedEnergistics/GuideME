package guideme.libs.mdast.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonWriter;
import guideme.libs.unist.UnistPoint;
import guideme.libs.unist.UnistPosition;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

public class MdAstPosition implements UnistPosition {
    public UnistPoint start;
    public UnistPoint end;
    int @Nullable [] indent; // number >= 1

    public MdAstPosition() {
    }

    public MdAstPosition(UnistPoint start, UnistPoint end) {
        this.start = start;
        this.end = end;
    }

    public static String stringify(UnistPoint point) {
        return point.line() + ":" + point.column();
    }

    public static String stringify(UnistPosition position) {
        return stringify(position.start(), position.end());
    }

    public static String stringify(UnistPoint start, UnistPoint end) {
        var result = new StringBuilder();
        if (start != null) {
            result.append(start.line()).append(":").append(start.column());
        }
        if (end != null) {
            result.append("-").append(end.line()).append(":").append(end.column());
        }
        return result.toString();
    }

    @Override
    public UnistPoint start() {
        return start;
    }

    @Override
    public UnistPoint end() {
        return end;
    }

    @Override
    public int @Nullable [] indent() {
        return indent;
    }

    public MdAstPosition withStart(UnistPoint point) {
        this.start = point;
        return this;
    }

    public MdAstPosition withEnd(UnistPoint point) {
        this.end = point;
        return this;
    }

    public void writeJson(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("start");
        start.writeJson(writer);
        writer.name("end");
        end.writeJson(writer);
        writer.endObject();
    }

    public static MdAstPosition fromJson(JsonObject jsonObject) {
        return new MdAstPosition(pointFromJson(jsonObject.getAsJsonObject("start")),
                pointFromJson(jsonObject.getAsJsonObject("end")));
    }

    private static UnistPoint pointFromJson(JsonObject jsonObject) {
        if (jsonObject == null) {
            throw new JsonSyntaxException("Missing start or end of position");
        }
        return new MdAstPoint(jsonObject.get("line").getAsInt(), jsonObject.get("column").getAsInt(),
                jsonObject.get("offset").getAsInt());
    }

    @Override
    public String toString() {
        return stringify(start, end);
    }
}
