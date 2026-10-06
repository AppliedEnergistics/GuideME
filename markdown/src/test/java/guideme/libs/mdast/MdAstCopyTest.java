package guideme.libs.mdast;

import static org.assertj.core.api.Assertions.assertThat;

import guideme.libs.mdast.model.MdAstNode;
import guideme.libs.mdast.model.MdAstParent;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MdAstCopyTest {
    @Test
    void testCopyIsEqualButIndependent() throws IOException {
        var root = MdAstJsonTest.parseMdx();
        var originalJson = MdAstJsonTest.toJson(root);

        var copy = root.deepCopy();

        assertThat(MdAstJsonTest.toJson(copy)).isEqualTo(originalJson);

        // The copy shares no nodes with the original
        var originalNodes = allNodes(root);
        assertThat(allNodes(copy)).noneMatch(originalNodes::contains);

        // Modifying the copy doesn't affect the original
        ((MdAstParent<?>) copy).children().clear();
        assertThat(MdAstJsonTest.toJson(root)).isEqualTo(originalJson);
    }

    private static Set<MdAstNode> allNodes(MdAstNode root) {
        var nodes = Collections.newSetFromMap(new IdentityHashMap<MdAstNode, Boolean>());
        root.visit(new MdAstVisitor() {
            @Override
            public Result beforeNode(MdAstNode node) {
                nodes.add(node);
                return Result.CONTINUE;
            }
        });
        return nodes;
    }
}
