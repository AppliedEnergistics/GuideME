package guideme.internal.siteexport.mdastpostprocess;

import guideme.compiler.IdUtils;
import guideme.document.block.LytNode;
import guideme.extensions.Extension;
import guideme.extensions.ExtensionCollection;
import guideme.extensions.ExtensionPoint;
import guideme.libs.mdast.mdx.model.MdxJsxAttribute;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import guideme.siteexport.PageExportContext;
import guideme.siteexport.PageExportProcessor;
import guideme.siteexport.ResourceExporter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Runs the {@link PageExportProcessor} extensions over the Markdown AST of a page.
 * <p>
 * The AST is shared with the in-game guide, so all modifications are undone after the export. To do so, the original
 * children and attributes of every node that is modified are recorded before its first modification.
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
    private final Consumer<Runnable> cleanupCallbacks;
    private final Function<MdAstNode, List<LytNode>> layoutNodes;

    private final Map<MdAstNode, MdAstParent<?>> parents = new IdentityHashMap<>();
    private final Set<Object> recordedForUndo = Collections.newSetFromMap(new IdentityHashMap<>());
    private int replacements;

    PageExportProcessing(ResourceExporter exporter, Identifier pageId, ExtensionCollection extensions,
            Consumer<Runnable> cleanupCallbacks, Function<MdAstNode, List<LytNode>> layoutNodes) {
        this.layoutNodes = layoutNodes;
        this.exporter = exporter;
        this.pageId = pageId;
        this.extensions = extensions;
        this.processors = extensions.get(PageExportProcessor.EXTENSION_POINT);
        this.cleanupCallbacks = cleanupCallbacks;
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
    public void setAttribute(MdxJsxElementFields element, String name, String value) {
        recordAttributes(element);
        // Don't modify the existing attribute, since we only record the attribute list for undoing changes
        element.removeAttribute(name);
        element.attributes().add(new MdxJsxAttribute(name, value));
    }

    @Override
    public void removeAttribute(MdxJsxElementFields element, String name) {
        recordAttributes(element);
        element.removeAttribute(name);
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

        recordChildren(parent);
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

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void recordChildren(MdAstParent<?> parent) {
        if (recordedForUndo.add(parent)) {
            var children = (List) parent.children();
            var original = new ArrayList<>(children);
            cleanupCallbacks.accept(() -> {
                children.clear();
                children.addAll(original);
            });
        }
    }

    private void recordAttributes(MdxJsxElementFields element) {
        if (recordedForUndo.add(element.attributes())) {
            var attributes = element.attributes();
            var original = new ArrayList<>(attributes);
            cleanupCallbacks.accept(() -> {
                attributes.clear();
                attributes.addAll(original);
            });
        }
    }
}
