package guideme.guidebook.scene;

import guideme.guidebook.compiler.PageCompiler;
import guideme.guidebook.compiler.tags.BlockTagCompiler;
import guideme.guidebook.compiler.tags.MdxAttrs;
import guideme.guidebook.document.block.LytBlockContainer;
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
        var item = MdxAttrs.getRequiredItem(compiler, parent, el, "id");
        var scale = MdxAttrs.getFloat(compiler, parent, el, "scale", 1.0f);

        if (item != null) {
            var itemImage = new LytItemImage();
            itemImage.setItem(item.getDefaultInstance());
            itemImage.setScale(scale);
            parent.append(itemImage);
        }
    }
}
