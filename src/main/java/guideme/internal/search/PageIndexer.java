
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
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
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
import guideme.libs.mdast.model.MdAstRoot;
import guideme.libs.mdast.model.MdAstStrong;
import guideme.libs.mdast.model.MdAstText;
import guideme.libs.mdast.model.MdAstThematicBreak;
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
        indexContent(root.children(), sink);
    }

    @Override
    public void indexContent(MdAstAnyContent content, IndexingSink sink) {
        if (content instanceof MdAstThematicBreak) {
            sink.appendBreak();
        } else if (content instanceof MdAstList astList) {
            indexList(astList, sink);
        } else if (content instanceof MdAstCode astCode) {
            sink.appendText(astCode, astCode.value);
        } else if (content instanceof MdAstHeading astHeading) {
            indexContent(astHeading.children(), sink);
        } else if (content instanceof MdAstParagraph astParagraph) {
            indexContent(astParagraph.children(), sink);
        } else if (content instanceof MdAstYamlFrontmatter) {
            // This is handled by compile directly
        } else if (content instanceof GfmTable astTable) {
            indexTable(astTable, sink);
        } else if (content instanceof MdAstText astText) {
            sink.appendText(astText, astText.value);
        } else if (content instanceof MdAstInlineCode astCode) {
            sink.appendText(astCode, astCode.value);
        } else if (content instanceof MdAstStrong astStrong) {
            indexContent(astStrong.children(), sink);
        } else if (content instanceof MdAstEmphasis astEmphasis) {
            indexContent(astEmphasis.children(), sink);
        } else if (content instanceof MdAstBreak) {
            sink.appendBreak();
        } else if (content instanceof MdAstLink astLink) {
            indexLink(astLink, sink);
        } else if (content instanceof MdAstImage astImage) {
            indexImage(astImage, sink);
        } else if (content instanceof MdxJsxElementFields el) {
            var compiler = tagCompilers.get(el.name());
            if (compiler == null) {
                LOG.warn("Unhandled custom MDX element in guide search indexing: {}", el.name());
            } else {
                compiler.index(this, el, sink);
            }
        } else {
            LOG.warn("Unhandled node type in guide search indexing: {}", content.type());
        }
        sink.appendBreak();
    }

    private void indexList(MdAstList astList, IndexingSink sink) {
        for (var listContent : astList.children()) {
            if (listContent instanceof MdAstListItem astListItem) {
                indexContent(astListItem.children(), sink);
            } else {
                LOG.warn("Cannot handle list content: {}", listContent);
            }
        }
    }

    private void indexTable(GfmTable astTable, IndexingSink sink) {
        for (var astRow : astTable.children()) {
            var astCells = astRow.children();
            for (var astCell : astCells) {
                indexContent(astCell.children(), sink);
            }
        }
    }

    private void indexLink(MdAstLink astLink, IndexingSink sink) {
        if (astLink.title != null && !astLink.title.isEmpty()) {
            sink.appendText(astLink, astLink.title);
        }
        indexContent(astLink.children(), sink);
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
