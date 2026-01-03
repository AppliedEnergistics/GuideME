package guideme.compiler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import guideme.libs.mdast.model.MdAstNode;
import java.io.IOException;

/**
 * Serializes the MdAst nodes to JSON.
 */
public class MdAstNodeAdapter extends TypeAdapter<MdAstNode> {
    @Override
    public void write(JsonWriter out, MdAstNode value) throws IOException {
        value.toJson(out);
    }

    @Override
    public MdAstNode read(JsonReader in) throws IOException {
        JsonObject nodeObject = new Gson().fromJson(in, JsonObject.class);

        return MdAstNode.fromJson(nodeObject);
    }
}
