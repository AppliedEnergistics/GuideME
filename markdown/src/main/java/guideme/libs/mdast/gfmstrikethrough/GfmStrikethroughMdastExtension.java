package guideme.libs.mdast.gfmstrikethrough;

import guideme.libs.mdast.MdastContext;
import guideme.libs.mdast.MdastExtension;
import guideme.libs.micromark.Token;

public final class GfmStrikethroughMdastExtension {
    public static final MdastExtension INSTANCE = MdastExtension.builder()
            .canContainEol("delete")
            .enter("strikethrough", GfmStrikethroughMdastExtension::enterStrikethrough)
            .exit("strikethrough", GfmStrikethroughMdastExtension::exitStrikethrough)
            .build();

    private GfmStrikethroughMdastExtension() {
    }

    private static void enterStrikethrough(MdastContext context, Token token) {
        context.enter(new MdAstDelete(), token);
    }

    private static void exitStrikethrough(MdastContext context, Token token) {
        context.exit(token);
    }
}
