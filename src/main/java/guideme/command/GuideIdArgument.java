package guideme.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import guideme.GuideME;
import guideme.guidebook.Guide;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.server.command.ModIdArgument;

/**
 * An argument for commands that identifies a registered GuideME guide.
 */
public class GuideIdArgument implements ArgumentType<ResourceLocation> {
    private static final List<String> EXAMPLES = List.of("ae2:guide");

    public static ModIdArgument argument() {
        return new ModIdArgument();
    }

    public ResourceLocation parse(StringReader reader) throws CommandSyntaxException {
        return ResourceLocation.read(reader);
    }

    public static Guide getGuide(CommandContext<CommandSourceStack> context, String name) {
        var id = (ResourceLocation) context.getArgument(name, ResourceLocation.class);

        return GuideME.getGuideById(id);
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        // TODO: Get registered guides

        return SharedSuggestionProvider.suggest(GuideME.getGuides().stream().map(ResourceLocation::toString), builder);
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
