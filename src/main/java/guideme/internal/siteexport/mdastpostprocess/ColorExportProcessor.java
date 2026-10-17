package guideme.internal.siteexport.mdastpostprocess;

import guideme.color.LightDarkMode;
import guideme.color.SymbolicColorResolver;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstNode;
import guideme.siteexport.NodeSelector;
import guideme.siteexport.PageExportContext;
import guideme.siteexport.PageExportProcessor;
import java.util.Locale;

/**
 * Symbolic colors can be defined by mods through {@link SymbolicColorResolver} extensions, which are not available when
 * the website is generated. Replace them with the resolved color.
 */
public final class ColorExportProcessor implements PageExportProcessor {
    private static final NodeSelector SELECTOR = NodeSelector.element("Color");

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

        var color = SymbolicColorResolver.resolve(id, context::resolveId,
                context.getExtensions(SymbolicColorResolver.EXTENSION_POINT));
        if (color == null) {
            return; // Leave it to the website to show the error
        }

        // The website uses a dark theme
        var argb = color.resolve(LightDarkMode.DARK_MODE);
        element.removeAttribute("id");
        element.setAttribute("color", String.format(Locale.ROOT, "#%08X", argb));
    }
}
