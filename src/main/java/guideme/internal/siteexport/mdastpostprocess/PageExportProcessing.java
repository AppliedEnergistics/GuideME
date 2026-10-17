package guideme.internal.siteexport.mdastpostprocess;

import guideme.compiler.IdUtils;
import guideme.document.block.LytNode;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionCollection;
import guideme.extensions.ExtensionPoint;
import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import guideme.siteexport.PageExportContext;
import guideme.siteexport.PageExportProcessor;
import guideme.siteexport.ResourceExporter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Runs the {@link PageExportProcessor} extensions over the Markdown AST of a page.
 * <p>
 * The export processes a copy of the page, since the original is still used by the in-game guide.
 */
final class PageExportProcessing implements PageExportContext {
    /**
     * Protects against processors that keep replacing nodes with new nodes they match again.
     */
    private static final int MAX_REPLACEMENTS = 10000;

    private final ResourceExporter exporter;
    private final Identifier pageId;
    private final ExtensionCollection extensions;
    private final List<PageExportProcessor> processors;
    private final Function<MdAstNode, List<LytNode>> layoutNodes;

    private final Map<MdAstNode, MdAstParent<?>> parents = new IdentityHashMap<>();
    private int replacements;

    PageExportProcessing(ResourceExporter exporter, Identifier pageId, ExtensionCollection extensions,
            Function<MdAstNode, List<LytNode>> layoutNodes) {
        this.exporter = exporter;
        this.pageId = pageId;
        this.extensions = extensions;
        this.processors = extensions.get(PageExportProcessor.EXTENSION_POINT);
        this.layoutNodes = layoutNodes;
    }

    public void process(MdAstParent<?> root) {
        if (!processors.isEmpty()) {
            processChildren(root);
        }
    }

    private void processChildren(MdAstParent<?> parent) {
        var children = parent.children();
        int i = 0;
        while (i < children.size()) {
            var child = (MdAstNode) children.get(i);
            parents.put(child, parent);

            for (var processor : processors) {
                if (processor.getSelector().matches(child)) {
                    processor.process(this, child);
                    if (isReplaced(children, i, child)) {
                        break;
                    }
                }
            }

            if (isReplaced(children, i, child)) {
                // Process whatever took its place
                continue;
            }

            if (child instanceof MdAstParent<?> childParent) {
                processChildren(childParent);
            }
            i++;
        }
    }

    private static boolean isReplaced(List<?> children, int index, MdAstNode node) {
        return index >= children.size() || children.get(index) != node;
    }

    @Override
    public ResourceExporter exporter() {
        return exporter;
    }

    @Override
    public Identifier pageId() {
        return pageId;
    }

    @Override
    public Identifier resolveId(String id) {
        return IdUtils.resolveId(id, pageId.getNamespace());
    }

    @Override
    public <T extends Extension> List<T> getExtensions(ExtensionPoint<T> extensionPoint) {
        return extensions.get(extensionPoint);
    }

    @Override
    public @Nullable MdAstNode getParent(MdAstNode node) {
        return parents.get(node);
    }

    @Override
    public List<LytNode> getLayoutNodes(MdAstNode node) {
        return layoutNodes.apply(node);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void replace(MdAstNode node, List<? extends MdAstNode> replacement) {
        var parent = parents.get(node);
        if (parent == null) {
            throw new IllegalArgumentException("Node " + node + " is not part of the page being exported");
        }
        for (var replacementNode : replacement) {
            if (!parent.canContain(replacementNode)) {
                throw new IllegalArgumentException("Cannot replace " + node.type() + " with " + replacementNode.type()
                        + " since its parent " + parent.type() + " cannot contain it");
            }
        }
        if (++replacements > MAX_REPLACEMENTS) {
            throw new IllegalStateException("Too many replacements on page " + pageId
                    + ". Do processors keep replacing nodes with nodes they match again?");
        }

        var children = (List) parent.children();
        int index = -1;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == node) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            throw new IllegalArgumentException("Node " + node + " was already removed from its parent");
        }

        children.remove(index);
        children.addAll(index, new ArrayList<>(replacement));
        parents.remove(node);
    }

    @Override
    public void unwrap(MdAstNode node) {
        if (!(node instanceof MdAstParent<?> parentNode)) {
            throw new IllegalArgumentException("Node " + node.type() + " has no children to unwrap");
        }
        var children = new ArrayList<MdAstNode>();
        for (var child : parentNode.children()) {
            children.add((MdAstNode) child);
        }
        replace(node, children);
    }

    @Override
    public void remove(MdAstNode node) {
        replace(node, List.of());
    }
}
