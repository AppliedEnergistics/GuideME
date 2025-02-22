package guideme.compiler.tags;

import guideme.compiler.IndexingContext;
import guideme.compiler.IndexingSink;
import guideme.compiler.PageCompiler;
import guideme.document.flow.LytFlowLink;
import guideme.document.flow.LytFlowParent;
import guideme.document.interaction.GuideTooltip;
import guideme.document.interaction.TextTooltip;
import guideme.internal.GuidebookText;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import java.util.ArrayList;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a command when clicked.
 */
public class CommandLinkCompiler extends FlowTagCompiler {
    private static final Logger LOG = LoggerFactory.getLogger(CommandLinkCompiler.class);

    @Override
    public Set<String> getTagNames() {
        return Set.of("CommandLink");
    }

    @Override
    protected void compile(PageCompiler compiler, LytFlowParent parent, MdxJsxElementFields el) {
        var command = el.getAttributeString("command", "");
        if (command.isEmpty()) {
            parent.appendError(compiler, "command attribute is required", el);
            return;
        } else if (!command.startsWith("/")) {
            parent.appendError(compiler, "command must start with /", el);
            return;
        }
        var sendCommand = command.substring(1);
        var closeGuide = MdxAttrs.getBoolean(compiler, parent, el, "close", false);

        var title = el.getAttributeString("title", "");
        var link = new LytFlowLink();
        link.setTooltip(buildTooltip(title, command));

        var pageId = compiler.getPageId();
        link.setClickCallback(uiHost -> {
            if (closeGuide) {
                int attempts = 5;
                while (Minecraft.getInstance().screen != null) {
                    Minecraft.getInstance().screen.onClose();
                    if (--attempts <= 0) {
                        break; // Give up at some point...
                    }
                }
            }

            var player = Minecraft.getInstance().player;
            if (player == null) {
                LOG.info("Cannot send command without active player.");
            } else {
                LOG.info("Sending command from page {}: {}", pageId, sendCommand);
                Minecraft.getInstance().player.connection.sendCommand(sendCommand);
            }
        });

        compiler.compileFlowContext(el.children(), link);
        parent.append(link);
    }

    private static GuideTooltip buildTooltip(String title, String command) {
        var tooltipLines = new ArrayList<Component>();
        if (!title.isEmpty()) {
            tooltipLines.add(Component.literal(title));
        }
        MutableComponent commandTooltipLine;
        if (command.length() > 25) {
            commandTooltipLine = Component.literal(command.substring(0, 25) + "...");
        } else {
            commandTooltipLine = Component.literal(command);
        }
        tooltipLines.add(GuidebookText.RunsCommand.text().withStyle(ChatFormatting.DARK_GRAY));
        tooltipLines.add(commandTooltipLine.withStyle(ChatFormatting.DARK_GRAY));

        return new TextTooltip(tooltipLines);
    }

    @Override
    public void index(IndexingContext indexer, MdxJsxElementFields el, IndexingSink sink) {
        var title = el.getAttributeString("title", "");
        if (!title.isBlank()) {
            sink.appendText(el, title);
        }

        indexer.indexContent(el.children(), sink);
    }
}
