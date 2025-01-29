package guideme.internal.command;

import com.mojang.brigadier.CommandDispatcher;
import guideme.Guides;
import guideme.internal.GuideMEClient;
import guideme.internal.GuidebookText;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class GuideClientCommand {
    private GuideClientCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var rootCommand = Commands.literal("guidemec");

        rootCommand.then(
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
                                    var guideId = GuideIdArgument.getGuide(context, "guide");
                                    var guide = Guides.getById(guideId);
                                    if (guide == null) {
                                        context.getSource()
                                                .sendFailure(GuidebookText.ItemInvalidGuideId.text(guideId.toString()));
                                        return 1;
                                    }

                                    GuideMEClient.openGuideAtPreviousPage(guide, guide.getStartPage());
                                    return 0;
                                })
                                .then(
                                        Commands.argument("page", PageAnchorArgument.argument())
                                                .executes(context -> {
                                                    var guideId = GuideIdArgument.getGuide(context, "guide");
                                                    var guide = Guides.getById(guideId);
                                                    var anchor = PageAnchorArgument.getPageAnchor(context, "page");
                                                    GuideMEClient.openGuideAtAnchor(guide, anchor);
                                                    return 0;
                                                }))

                        ));

        dispatcher.register(rootCommand);
    }
}
