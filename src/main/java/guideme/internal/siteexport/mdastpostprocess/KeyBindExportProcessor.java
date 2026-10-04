package guideme.internal.siteexport.mdastpostprocess;

import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstNode;
import guideme.siteexport.NodeSelector;
import guideme.siteexport.PageExportContext;
import guideme.siteexport.PageExportProcessor;
import net.minecraft.client.Minecraft;

/**
 * Key mappings are not available when the website is generated, so we resolve the name of the default key for KeyBind
 * tags during export.
 */
public final class KeyBindExportProcessor implements PageExportProcessor {
    public static final String KEY_NAME_ATTRIBUTE = "keyName";

    private static final NodeSelector SELECTOR = NodeSelector.element("KeyBind");

    @Override
    public NodeSelector getSelector() {
        return SELECTOR;
    }

    @Override
    public void process(PageExportContext context, MdAstNode node) {
        var element = (MdxJsxElementFields) node;
        var id = element.getAttributeString("id", null);
        if (id == null) {
            return;
        }

        for (var keyMapping : Minecraft.getInstance().options.keyMappings) {
            if (id.equals(keyMapping.getName())) {
                // Use the default key, since the website should not reflect the bindings of whoever exported it
                context.setAttribute(element, KEY_NAME_ATTRIBUTE,
                        keyMapping.getDefaultKey().getDisplayName().getString());
                return;
            }
        }
    }
}
