package guideme.compiler.tags;

import guideme.PageAnchor;
import guideme.compiler.IndexingContext;
import guideme.compiler.IndexingSink;
import guideme.compiler.LinkParser;
import guideme.compiler.PageCompiler;
import guideme.document.flow.LytFlowAnchor;
import guideme.document.flow.LytFlowLink;
import guideme.document.flow.LytFlowParent;
import guideme.document.interaction.TextTooltip;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.net.URI;
import java.util.Set;

public class ATagCompiler extends FlowTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("a");
    }

    @Override
    protected void compile(PageCompiler compiler, LytFlowParent parent, MdxJsxElementFields el) {
        var anchorName = el.getAttributeString("name", "");
        if (!anchorName.isEmpty()) {
            parent.append(new LytFlowAnchor(anchorName));
        }

        // The link only materializes if it has a HREF attribute, otherwise we compile the children directly
        var href = el.getAttributeString("href", "");
        var title = el.getAttributeString("title", "");
        if (!href.isEmpty() || !title.isEmpty()) {
            var link = new LytFlowLink();
            if (!title.isEmpty()) {
                link.setTooltip(new TextTooltip(title));
            }
            if (!href.isEmpty()) {
                LinkParser.parseLink(compiler, href, new LinkParser.Visitor() {
                    @Override
                    public void handlePage(PageAnchor page) {
                        link.setPageLink(page);
                    }

                    @Override
                    public void handleExternal(URI uri) {
                        link.setExternalUrl(uri);
                    }

                    @Override
                    public void handleError(String error) {
                        parent.appendError(compiler, error, el);
                    }
                });
            }
            compiler.compileFlowContext(el.children(), link);
            parent.append(link);
        } else {
            compiler.compileFlowContext(el.children(), parent);
        }
    }

    @Override
    public void index(IndexingContext indexer, MdxJsxElementFields el, IndexingSink sink) {
        var title = el.getAttributeString("title", "");
        if (!title.isBlank()) {
            sink.appendText(el, title);
        }

        indexer.indexContent(el.children(), sink);
    }
}
