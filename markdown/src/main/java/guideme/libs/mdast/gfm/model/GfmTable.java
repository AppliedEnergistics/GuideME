package guideme.libs.mdast.gfm.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonWriter;
import guideme.libs.mdast.model.MdAstFlowContent;
import guideme.libs.mdast.model.MdAstParent;
import guideme.libs.micromark.extensions.gfm.Align;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class GfmTable extends MdAstParent<GfmTableRow> implements MdAstFlowContent {
    public static final String TYPE = "table";

    @Nullable
    public List<Align> align = null;

    public GfmTable() {
        super(TYPE);
    }

    @Override
    protected Class<GfmTableRow> childClass() {
        return GfmTableRow.class;
    }

    @Override
    protected void writeJson(JsonWriter writer) throws IOException {
        if (align != null) {
            writer.name("align").beginArray();
            for (var value : align) {
                switch (value) {
                    case LEFT -> writer.value("left");
                    case CENTER -> writer.value("center");
                    case RIGHT -> writer.value("right");
                    case NONE -> writer.nullValue();
                }
            }
            writer.endArray();
        }

        super.writeJson(writer);
    }

    @Override
    protected void readJson(JsonObject jsonObject) throws IOException {
        var align = jsonObject.getAsJsonArray("align");
        if (align != null) {
            this.align = new ArrayList<>();
            for (var alignEl : align) {
                if (alignEl.isJsonNull()) {
                    this.align.add(Align.NONE);
                } else
                    switch (alignEl.getAsString()) {
                        case "left" -> this.align.add(Align.LEFT);
                        case "center" -> this.align.add(Align.CENTER);
                        case "right" -> this.align.add(Align.RIGHT);
                        default -> throw new JsonSyntaxException("Unknown table align: " + alignEl);
                    }
            }
        }

        super.readJson(jsonObject);
    }
}
