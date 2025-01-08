package guideme.libs.mdast;

import guideme.libs.micromark.Token;
import guideme.libs.micromark.extensions.YamlFrontmatterSyntax;

public class YamlFrontmatterExtension {

    public static final MdastExtension INSTANCE = MdastExtension.builder()
            .enter(YamlFrontmatterSyntax.TYPE, YamlFrontmatterExtension::open)
            .exit(YamlFrontmatterSyntax.TYPE, YamlFrontmatterExtension::close)
            .exit(YamlFrontmatterSyntax.VALUE_TYPE, YamlFrontmatterExtension::value)
            .build();

    private static void open(MdastContext context, Token token) {
        context.enter(new MdAstYamlFrontmatter(), token);
        context.buffer();
    }

    private static void close(MdastContext context, Token token) {
        var data = context.resume();
        var node = (MdAstYamlFrontmatter) context.exit(token);
        // Remove the initial and final eol.
        node.value = data.replaceAll("^(\\r?\\n|\\r)|(\\r?\\n|\\r)\\z", "");
    }

    private static void value(MdastContext context, Token token) {
        context.getExtension().enter.get("data").handle(context, token);
        context.getExtension().exit.get("data").handle(context, token);
    }

}
