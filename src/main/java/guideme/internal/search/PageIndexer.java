
package guideme.internal.search;

import guideme.PageCollection;
import guideme.compiler.IndexingContext;
import guideme.compiler.IndexingSink;
import guideme.compiler.TagCompiler;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionCollection;
import guideme.extensions.ExtensionPoint;
import guideme.libs.mdast.MdAstYamlFrontmatter;
import guideme.libs.mdast.gfm.model.GfmTable;
import guideme.libs.mdast.mdx.model.MdxJsxFlowElement;
import guideme.libs.mdast.mdx.model.MdxJsxTextElement;
import guideme.libs.mdast.model.MdAstAnyContent;
import guideme.libs.mdast.model.MdAstBreak;
import guideme.libs.mdast.model.MdAstCode;
import guideme.libs.mdast.model.MdAstEmphasis;
import guideme.libs.mdast.model.MdAstHeading;
import guideme.libs.mdast.model.MdAstImage;
import guideme.libs.mdast.model.MdAstInlineCode;
import guideme.libs.mdast.model.MdAstLink;
import guideme.libs.mdast.model.MdAstList;
import guideme.libs.mdast.model.MdAstListItem;
import guideme.libs.mdast.model.MdAstParagraph;
import guideme.libs.mdast.model.MdAstParent;
import guideme.libs.mdast.model.MdAstPhrasingContent;
import guideme.libs.mdast.model.MdAstRoot;
import guideme.libs.mdast.model.MdAstStrong;
import guideme.libs.mdast.model.MdAstText;
import guideme.libs.mdast.model.MdAstThematicBreak;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PageIndexer implements IndexingContext {
    private static final Logger LOG = LoggerFactory.getLogger(PageIndexer.class);

    private final PageCollection pages;
    private final ExtensionCollection extensions;
    private final ResourceLocation pageId;

    private final Map<String, TagCompiler> tagCompilers = new HashMap<>();

    public PageIndexer(PageCollection pages, ExtensionCollection extensions, ResourceLocation pageId) {
        this.pages = pages;
        this.extensions = extensions;
        this.pageId = pageId;

        // Index available tag-compilers
        for (var tagCompiler : extensions.get(TagCompiler.EXTENSION_POINT)) {
            for (String tagName : tagCompiler.getTagNames()) {
                tagCompilers.put(tagName, tagCompiler);
            }
        }
    }

    @Override
    public ExtensionCollection getExtensions() {
        return extensions;
    }

    @Override
    public <T extends Extension> List<T> getExtensions(ExtensionPoint<T> extensionPoint) {
        return extensions.get(extensionPoint);
    }

    public void index(MdAstRoot root, IndexingSink sink) {
        indexBlockContext(root, sink);
    }

    public void indexBlockContext(MdAstParent<?> markdownParent, IndexingSink sink) {
        indexBlockContext(markdownParent.children(), sink);
    }

    public void indexBlockContext(List<? extends MdAstAnyContent> children, IndexingSink sink) {
        for (var child : children) {
            if (child instanceof MdAstThematicBreak) {
                sink.appendBreak();
            } else if (child instanceof MdAstList astList) {
                indexList(astList, sink);
            } else if (child instanceof MdAstCode astCode) {
                sink.appendText(astCode, astCode.value);
            } else if (child instanceof MdAstHeading astHeading) {
                indexFlowChildren(astHeading, sink);
            } else if (child instanceof MdAstParagraph astParagraph) {
                indexFlowChildren(astParagraph, sink);
            } else if (child instanceof MdAstYamlFrontmatter) {
                // This is handled by compile directly
            } else if (child instanceof GfmTable astTable) {
                indexTable(astTable, sink);
            } else if (child instanceof MdxJsxFlowElement el) {
                var compiler = tagCompilers.get(el.name());
                if (compiler == null) {
                    LOG.warn("Unhandled MDX element in block context: {}", child);
                } else {
                    compiler.indexBlockContext(this, el, sink);
                }
            } else if (child instanceof MdAstPhrasingContent phrasingContent) {
                indexFlowContext(sink, phrasingContent);
            } else {
                LOG.warn("Unhandled node type in guide search indexing: {}", child);
            }
            sink.appendBreak();
        }
    }

    private void indexList(MdAstList astList, IndexingSink sink) {
        for (var listContent : astList.children()) {
            if (listContent instanceof MdAstListItem astListItem) {
                indexBlockContext(astListItem, sink);
            } else {
                LOG.warn("Cannot handle list content: {}", listContent);
            }
        }
    }

    private void indexTable(GfmTable astTable, IndexingSink sink) {
        for (var astRow : astTable.children()) {
            var astCells = astRow.children();
            for (var astCell : astCells) {
                indexBlockContext(astCell, sink);
            }
        }
    }

    public void indexFlowChildren(MdAstParent<?> markdownParent, IndexingSink sink) {
        indexFlowContext(markdownParent.children(), sink);
    }

    public void indexFlowContext(Collection<? extends MdAstAnyContent> children, IndexingSink sink) {
        for (var child : children) {
            indexFlowContext(sink, child);
        }
    }

    private void indexFlowContext(IndexingSink sink, MdAstAnyContent content) {
        if (content instanceof MdAstText astText) {
            sink.appendText(astText.position, astText.value);
        } else if (content instanceof MdAstInlineCode astCode) {
            sink.appendText(astCode.position, astCode.value);
        } else if (content instanceof MdAstStrong astStrong) {
            indexFlowChildren(astStrong, sink);
        } else if (content instanceof MdAstEmphasis astEmphasis) {
            indexFlowChildren(astEmphasis, sink);
        } else if (content instanceof MdAstBreak) {
            sink.appendBreak();
        } else if (content instanceof MdAstLink astLink) {
            indexLink(astLink, sink);
        } else if (content instanceof MdAstImage astImage) {
            indexImage(astImage, sink);
        } else if (content instanceof MdxJsxTextElement el) {
            var compiler = tagCompilers.get(el.name());
            if (compiler == null) {
                LOG.warn("Unhandled MDX element in flow context: {}", content);
            } else {
                compiler.indexFlowContext(this, el, sink);
            }
        } else {
            LOG.warn("Unhandled Markdown node in flow context: {}", content);
        }
    }

    private void indexLink(MdAstLink astLink, IndexingSink sink) {
        if (astLink.title != null && !astLink.title.isEmpty()) {
            sink.appendText(astLink, astLink.title);
        }
        indexFlowContext(astLink.children(), sink);
    }

    private void indexImage(MdAstImage astImage, IndexingSink sink) {
        if (astImage.title != null && !astImage.title.isEmpty()) {
            sink.appendText(astImage, astImage.title);
        }
        if (astImage.alt != null && !astImage.alt.isEmpty()) {
            sink.appendText(astImage, astImage.alt);
        }
    }

    /**
     * Get the current page id.
     */
    @Override
    public ResourceLocation getPageId() {
        return pageId;
    }

    @Override
    public PageCollection getPageCollection() {
        return pages;
    }
}
