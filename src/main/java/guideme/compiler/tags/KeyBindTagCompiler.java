package guideme.compiler.tags;

import guideme.compiler.PageCompiler;
import guideme.document.flow.LytFlowParent;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * This tag compiles to the current binding for a key binding.
 */
public class KeyBindTagCompiler extends FlowTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("KeyBind");
    }

    @Override
    protected void compile(PageCompiler compiler, LytFlowParent parent, MdxJsxElementFields el) {
        var id = el.getAttributeString("id", null);
        if (id == null) {
            parent.appendError(compiler, "Attribute id is required.", el);
            return;
        }

        var mapping = findMapping(id);
        if (mapping == null) {
            parent.appendError(compiler, "No key mapping with this id was found.", el);
            return;
        }

        parent.appendComponent(mapping.getTranslatedKeyMessage());
    }

    private static KeyMapping findMapping(String id) {
        // Find the mapping by id
        var keyMappings = Minecraft.getInstance().options.keyMappings;
        for (var keyMapping : keyMappings) {
            if (id.equals(keyMapping.getName())) {
                return keyMapping;
            }
        }
        return null;
    }
}
