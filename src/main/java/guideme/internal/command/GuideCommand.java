package guideme.internal.command;

import com.mojang.brigadier.CommandDispatcher;
import guideme.Guides;
import guideme.GuidesCommon;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class GuideCommand {
    private GuideCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var rootCommand = Commands.literal("guideme");
        rootCommand
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

                                        )));

        rootCommand
                .requires(p -> p.hasPermission(2))
                .then(Commands.literal("give")
                        .then(
                                Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("guide", GuideIdArgument.argument())
                                                .executes(context -> giveGuide(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        GuideIdArgument.getGuide(context, "guide"))))));

        dispatcher.register(rootCommand);
    }

    private static int giveGuide(CommandSourceStack source, Collection<ServerPlayer> targets,
            ResourceLocation guideId) {
        var guideItem = Guides.createGuideItem(guideId);
        for (var target : targets) {
            ItemHandlerHelper.giveItemToPlayer(target, guideItem.copy());
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.give.success.single", 1, guideItem.getDisplayName(),
                            targets.iterator().next().getDisplayName()),
                    true);
        } else {
            source.sendSuccess(
                    () -> Component.translatable("commands.give.success.single", 1, guideItem.getDisplayName(),
                            targets.size()),
                    true);
        }

        return targets.size();
    }
}
