package guideme.compiler.tags;

import guideme.compiler.PageCompiler;
import guideme.document.flow.LytFlowParent;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.Set;
import net.minecraft.client.Minecraft;

/**
 * This tag compiles to the name of current players game profile.
 */
public class PlayerNameTagCompiler extends FlowTagCompiler {
    @Override
    public Set<String> getTagNames() {
        return Set.of("PlayerName");
    }

    @Override
    protected void compile(PageCompiler compiler, LytFlowParent parent, MdxJsxElementFields el) {

        var playerName = Minecraft.getInstance().getUser().getName();
        parent.appendText(playerName);
    }
}
