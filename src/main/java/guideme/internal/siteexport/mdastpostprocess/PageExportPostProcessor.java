package guideme.internal.siteexport.mdastpostprocess;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import guideme.GuidePage;
import guideme.compiler.ParsedGuidePage;
import guideme.document.block.LytNode;
import guideme.document.block.LytVisitor;
import guideme.extensions.ExtensionCollection;
import guideme.libs.mdast.MdAstVisitor;
import guideme.libs.mdast.MdAstYamlFrontmatter;
import guideme.libs.mdast.model.MdAstNode;
import guideme.siteexport.ResourceExporter;
import java.util.List;

/**
 * Post-Processes page content before exporting it.
 */
public final class PageExportPostProcessor {

    /**
     * Modifies the page for the export. The page must be a copy of the page used by the in-game guide.
     */
    public static void postprocess(ResourceExporter exporter,
            ExtensionCollection extensions,
            ParsedGuidePage page,
            GuidePage compiledPage) {

        // Create a mapping from source node -> compiled node to
        // allow AST postprocessors to attach more exported
        // info to the AST nodes.
        Multimap<MdAstNode, LytNode> nodeMapping = ArrayListMultimap.create();
        compiledPage.document().visit(new LytVisitor() {
            @Override
            public Result beforeNode(LytNode node) {
                if (node.getSourceNode() != null) {
                    nodeMapping.put(node.getSourceNode(), node);
                }
                return Result.CONTINUE;
            }
        }, true /* scenes may be nested within tooltips of scenes */);

        var astRoot = page.getAstRoot();

        // Strip unnecessary frontmatter nodes.
        astRoot.removeChildren(mdAstNode -> mdAstNode instanceof MdAstYamlFrontmatter, true);

        // Let elements modify themselves first, so that the content they unwrap is exported as well
        new PageExportProcessing(exporter, page.getId(), extensions,
                node -> List.copyOf(nodeMapping.get(node))).process(astRoot);

        astRoot.visit(new SceneExportVisitor(exporter, nodeMapping));
        astRoot.visit(new ImageExportVisitor(exporter));

        astRoot.visit(new RemovePositionVisitor());
    }

    // Strips all line information, since this is no longer useful
    static class RemovePositionVisitor implements MdAstVisitor {
        @Override
        public Result beforeNode(MdAstNode node) {
            node.position = null;
            return Result.CONTINUE;
        }
    }

}
