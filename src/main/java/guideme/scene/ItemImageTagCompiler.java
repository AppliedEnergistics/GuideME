package guideme.scene;

import guideme.compiler.PageCompiler;
import guideme.compiler.tags.BlockTagCompiler;
import guideme.compiler.tags.MdxAttrs;
import guideme.document.block.LytBlockContainer;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.Set;

public class ItemImageTagCompiler extends BlockTagCompiler {

    public static final String TAG_NAME = "ItemImage";

    @Override
    public Set<String> getTagNames() {
        return Set.of(TAG_NAME);
    }

    @Override
    protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
        var stack = MdxAttrs.getRequiredItemStack(compiler, parent, el);
        var scale = MdxAttrs.getFloat(compiler, parent, el, "scale", 1.0f);

        if (stack != null) {
            var itemImage = new LytItemImage();
            itemImage.setItem(stack);
            itemImage.setScale(scale);
            parent.append(itemImage);
        }
    }
}
