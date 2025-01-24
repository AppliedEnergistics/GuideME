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
                .requires(p -> p.hasPermission(2))
                .then(Commands.literal("open")
                        .then(
                                Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("guide", GuideIdArgument.argument())
                                                .executes(context -> {
                                                    var guideId = GuideIdArgument.getGuide(context, "guide");

                                                    for (var target : EntityArgument.getPlayers(context, "targets")) {
                                                        GuidesCommon.openGuide(target, guideId);
                                                    }
                                                    return 0;
                                                })
                                                .then(
                                                        Commands.argument("page", PageAnchorArgument.argument())
                                                                .executes(context -> {
                                                                    var guideId = GuideIdArgument.getGuide(context,
                                                                            "guide");
                                                                    var anchor = PageAnchorArgument
                                                                            .getPageAnchor(context, "page");

                                                                    for (var target : EntityArgument.getPlayers(context,
                                                                            "targets")) {
                                                                        GuidesCommon.openGuide(target, guideId, anchor);
                                                                    }
                                                                    return 0;
                                                                }))

                                        ))));
    }
}
