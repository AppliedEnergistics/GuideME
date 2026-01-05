package guideme.internal.web;

import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import guideme.siteexport.WebRenderingContext;
import guideme.siteexport.web.ExportedGuide;
import guideme.siteexport.web.HtmlFragment;
import guideme.siteexport.web.HtmlTag;
import java.util.List;

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
