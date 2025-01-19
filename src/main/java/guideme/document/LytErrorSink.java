package guideme.document;

import guideme.compiler.PageCompiler;
import guideme.libs.unist.UnistNode;

public interface LytErrorSink {
    void appendError(PageCompiler compiler, String text, UnistNode node);
}
