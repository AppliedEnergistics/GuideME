package appeng.siteexport.mdastpostprocess;

import appeng.client.guidebook.GuidePage;
import appeng.client.guidebook.compiler.ParsedGuidePage;
import appeng.client.guidebook.document.block.LytNode;
import appeng.client.guidebook.document.block.LytVisitor;
import appeng.siteexport.ResourceExporter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import guideme.libs.mdast.MdAstVisitor;
import guideme.libs.mdast.MdAstYamlFrontmatter;
import guideme.libs.mdast.model.MdAstNode;

/**
 * Post-Processes page content before exporting it.
 */
public final class PageExportPostProcessor {

    public static void postprocess(ResourceExporter exporter,
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
