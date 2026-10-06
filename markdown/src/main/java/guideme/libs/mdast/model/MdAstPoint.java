package guideme.libs.mdast.model;

import guideme.libs.unist.UnistPoint;

/**
 * A point in the source file read from JSON.
 */
record MdAstPoint(int line, int column, int offset) implements UnistPoint {
}
