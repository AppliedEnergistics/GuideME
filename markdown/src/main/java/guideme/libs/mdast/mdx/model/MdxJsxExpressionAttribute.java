package guideme.libs.mdast.mdx.model;

import guideme.libs.mdast.model.MdAstLiteral;

public class MdxJsxExpressionAttribute extends MdAstLiteral implements MdxJsxAttributeNode {
    public static final String TYPE = "mdxJsxExpressionAttribute";

    public MdxJsxExpressionAttribute() {
        super(TYPE);
    }
}
