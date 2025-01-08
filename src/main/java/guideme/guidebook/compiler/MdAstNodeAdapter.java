package appeng.client.guidebook.compiler;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import guideme.libs.mdast.model.MdAstNode;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

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
        throw new UnsupportedEncodingException();
    }
}
