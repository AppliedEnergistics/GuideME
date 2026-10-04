package guideme.internal.siteexport.mdastpostprocess;

import guideme.libs.mdast.MdAstVisitor;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstNode;
import guideme.siteexport.ResourceExporter;
import net.minecraft.client.Minecraft;

/**
 * Key mappings are not available when the website is generated, so we resolve the name of the default key for KeyBind
 * tags during export.
 */
class KeyBindExportVisitor implements MdAstVisitor {
    public static final String KEY_NAME_ATTRIBUTE = "keyName";

    private final ResourceExporter exporter;

    public KeyBindExportVisitor(ResourceExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public Result beforeNode(MdAstNode node) {
        if (node instanceof MdxJsxElementFields fields && "KeyBind".equals(fields.name())) {
            var id = fields.getAttributeString("id", null);
            if (id != null) {
                for (var keyMapping : Minecraft.getInstance().options.keyMappings) {
                    if (id.equals(keyMapping.getName())) {
                        // Use the default key, since the website should not reflect the bindings of whoever exported it
                        fields.setAttribute(KEY_NAME_ATTRIBUTE,
                                keyMapping.getDefaultKey().getDisplayName().getString());
                        exporter.addCleanupCallback(() -> fields.removeAttribute(KEY_NAME_ATTRIBUTE));
                        break;
                    }
                }
            }
        }
        return Result.CONTINUE;
    }
}
