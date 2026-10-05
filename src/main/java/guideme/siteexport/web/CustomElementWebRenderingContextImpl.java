package guideme.siteexport.web;

import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstParent;

class CustomElementWebRenderingContextImpl extends WebRenderingContextImpl implements CustomElementWebRenderingContext {
    private final MdxJsxElementFields fields;
    private final MdAstParent<?> node;

    CustomElementWebRenderingContextImpl(WebPageCompiler webPageCompiler,
            WebPageCompileContext context,
            MdxJsxElementFields fields,
            MdAstParent<?> node) {
        super(context, webPageCompiler, node);
        this.fields = fields;
        this.node = node;
    }

    @Override
    public HtmlFragment compileChildren() {
        return compileChildren(node);
    }

    @Override
    public String tagName() {
        return fields.name();
    }

    @Override
    public MdxJsxElementFields element() {
        return fields;
    }

    @Override
    public <T extends Record> T map(Class<T> modelClass) {
        return JsxAttributeMapper.map(fields, modelClass);
    }
}
