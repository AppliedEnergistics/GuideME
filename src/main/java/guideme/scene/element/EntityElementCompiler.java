package guideme.scene.element;

import guideme.compiler.PageCompiler;
import guideme.compiler.tags.MdxAttrs;
import guideme.document.LytErrorSink;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.scene.GuidebookScene;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntityElementCompiler implements SceneElementTagCompiler {
    private static final Logger LOG = LoggerFactory.getLogger(EntityElementCompiler.class);

    @Override
    public Set<String> getTagNames() {
        return Set.of("Entity");
    }

    @Override
    public void compile(GuidebookScene scene,
            PageCompiler compiler,
            LytErrorSink errorSink,
            MdxJsxElementFields el) {
        var entityId = MdxAttrs.getString(compiler, errorSink, el, "id", null);
        if (entityId == null) {
            errorSink.appendError(compiler, "Missing attribute 'id'", el);
            return;
        }

        var data = MdxAttrs.getCompoundTag(compiler, errorSink, el, "data", new CompoundTag());
        data.putString("id", entityId);

        var entity = EntityType.loadEntityRecursive(data, scene.getLevel(), EntitySpawnReason.LOAD, Function.identity());
        if (entity == null) {
            errorSink.appendError(compiler, "Failed to load entity '" + entityId, el);
            return;
        }

        var pos = new Vector3f(0.5f, 0, 0.5f);
        MdxAttrs.getFloatPos(compiler, errorSink, el, pos);
        entity.setPos(pos.x, pos.y, pos.z);

        var rotationY = MdxAttrs.getFloat(compiler, errorSink, el, "rotationY", -90);
        var rotationX = MdxAttrs.getFloat(compiler, errorSink, el, "rotationX", 0);
        entity.setYRot(rotationY);
        entity.setXRot(rotationX);
        entity.setOldPosAndRot();
        entity.setYHeadRot(entity.getYRot());
        entity.setYBodyRot(entity.getYRot());

        scene.getLevel().addEntity(entity);
        entity.tick();
    }
}
