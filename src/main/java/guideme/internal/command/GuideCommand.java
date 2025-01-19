package guideme.internal.command;

import com.mojang.brigadier.CommandDispatcher;
import guideme.internal.GuideME;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;

public final class GuideCommand {
    private GuideCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("guideme").then(
                Commands.argument("guide", GuideIdArgument.argument())
                        .then(Commands.literal("export")
                                .executes(context -> {
                                    var guide = GuideIdArgument.getGuide(context, "guide");
                                    // Do we have a registered site exporter for the guide?
                                    // TODO
                                    return 0;
                                }))
                        .then(Commands.literal("open")
                                .executes(context -> {
                                    var guide = GuideIdArgument.getGuide(context, "guide");

                                    GuideME.openGuideAtPreviousPage(guide, ResourceLocation.fromNamespaceAndPath(
                                            guide.getDefaultNamespace(),
                                            "index.md"));

                                    return 0;
                                })
                                .then(
                                        Commands.argument("page", PageAnchorArgument.argument())
                                                .executes(context -> {
                                                    var guide = GuideIdArgument.getGuide(context, "guide");
                                                    var anchor = PageAnchorArgument.getPageAnchor(context, "page");
                                                    GuideME.openGuideAtAnchor(guide, anchor);
                                                    return 0;
                                                }))

                        )));
    }
}
