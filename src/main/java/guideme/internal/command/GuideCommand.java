package guideme.internal.command;

import com.mojang.brigadier.CommandDispatcher;
import guideme.GuidesCommon;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;

public final class GuideCommand {
    private GuideCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("guideme")
                .then(Commands.literal("open")
                        .then(
                                Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("guide", GuideIdArgument.argument())
                                                .executes(context -> {
                                                    var guideId = GuideIdArgument.getGuide(context, "guide");

                                                    GuidesCommon.openGuide(context.getSource().getPlayer(), guideId);
                                                    return 0;
                                                })
                                                .then(
                                                        Commands.argument("page", PageAnchorArgument.argument())
                                                                .executes(context -> {
                                                                    var guideId = GuideIdArgument.getGuide(context,
                                                                            "guide");
                                                                    var anchor = PageAnchorArgument
                                                                            .getPageAnchor(context, "page");
                                                                    GuidesCommon.openGuide(
                                                                            context.getSource().getPlayer(), guideId,
                                                                            anchor);
                                                                    return 0;
                                                                }))

                                        ))));
    }
}
