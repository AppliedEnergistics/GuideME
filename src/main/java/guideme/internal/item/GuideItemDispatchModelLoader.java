package guideme.internal.item;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import guideme.internal.GuideME;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

public class GuideItemDispatchModelLoader implements IGeometryLoader<GuideItemDispatchUnbakedModel> {
    public static final ResourceLocation ID = GuideME.makeId("guide_item_dispatcher");

    @Override
    public GuideItemDispatchUnbakedModel read(JsonObject jsonObject,
            JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return new GuideItemDispatchUnbakedModel();
    }
}
