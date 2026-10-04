package guideme.internal.web;

import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import guideme.siteexport.WebRenderingContext;
import guideme.siteexport.web.ExportedGuide;
import guideme.siteexport.web.HtmlFragment;
import guideme.siteexport.web.HtmlNode;
import guideme.siteexport.web.HtmlTag;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

class WebRenderingContextImpl implements WebRenderingContext {
    private final WebPageCompileContext context;
    private final WebPageCompiler compiler;
    private final MdAstParent<?> node;

    public WebRenderingContextImpl(WebPageCompileContext context, WebPageCompiler compiler, MdAstParent<?> node) {
        this.context = context;
        this.compiler = compiler;
        this.node = node;
    }

    @Override
    public ExportedGuide guide() {
        return context.guide();
    }

    @Override
    public String getAssetUrl(String assetPath) {
        return context.resolveAssetPath(assetPath);
    }

    @Override
    public String getAssetUrl(Identifier resource) {
        return context.resolveAssetPath(compiler.getResourceCopier().copy(resource));
    }

    @Override
    public String getPageUrl(String pageId) {
        if (!context.guide().pageExists(pageId)) {
            throw new IllegalArgumentException("Page does not exist: " + pageId);
        }
        return context.getRelativePagePath(pageId);
    }

    @Override
    public HtmlNode itemIcon(String itemId, boolean link) {
        return compiler.createItemIcon(context, node, itemId, !link);
    }

    @Override
    public HtmlNode fluidIcon(String fluidId) {
        var fluidInfo = context.guide().tryGetFluidInfo(fluidId);
        if (fluidInfo == null) {
            return compileError("Missing fluid " + fluidId);
        }
        return HtmlNode.tag("img")
                .setClassName("fluid-icon")
                .setAttribute("src", context.resolveAssetPath(fluidInfo.icon()))
                .setAttribute("alt", "")
                .setAttribute("aria-description", fluidInfo.displayName());
    }

    @Override
    public HtmlNode itemLink(String itemId, @Nullable HtmlFragment content) {
        return compiler.createItemLink(context, node, itemId, WebPageCompiler.TooltipMode.ICON, content);
    }

    @Override
    public HtmlTag compileError(String message) {
        return compiler.compileError(node, message);
    }

    @Override
    public HtmlFragment compileChildren(MdAstParent<?> parentNode) {
        return compiler.compileChildren(context, parentNode);
    }

    @Override
    public HtmlFragment compile(MdAstNode node, MdAstParent<?> parentNode) {
        return compiler.compileChildren(context, List.of(node), parentNode);
    }
}
