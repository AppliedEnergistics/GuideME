package guideme.libs.micromark.commonmark;

import guideme.libs.micromark.Assert;
import guideme.libs.micromark.CharUtil;
import guideme.libs.micromark.Construct;
import guideme.libs.micromark.State;
import guideme.libs.micromark.Token;
import guideme.libs.micromark.TokenizeContext;
import guideme.libs.micromark.Tokenizer;
import guideme.libs.micromark.Types;
import guideme.libs.micromark.factory.FactorySpace;
import guideme.libs.micromark.symbol.Codes;
import guideme.libs.micromark.symbol.Constants;

public final class BlockQuote {
    private BlockQuote() {
    }

    public static final Construct blockQuote;

    static {
        blockQuote = new Construct();
        blockQuote.name = "blockQuote";
        blockQuote.tokenize = (context, effects, ok, nok) -> new StateMachine(context, effects, ok, nok)::start;
        blockQuote.continuation = new Construct();
        blockQuote.continuation.tokenize = (context, effects, ok, nok) -> {
            return FactorySpace.create(
                    effects,
                    effects.attempt.hook(blockQuote, ok, nok),
                    Types.linePrefix,
                    context.getParser().constructs.nullDisable.contains("codeIndented")
                            ? Integer.MAX_VALUE
                            : Constants.tabSize);
        };
        blockQuote.exit = BlockQuote::exit;
    }

    static class StateMachine {
        private final TokenizeContext context;
        private final Tokenizer.Effects effects;
        private final State ok;
        private final State nok;

        public StateMachine(TokenizeContext context, Tokenizer.Effects effects, State ok, State nok) {

            this.context = context;
            this.effects = effects;
            this.ok = ok;
            this.nok = nok;
        }

        State start(int code) {
            if (code == Codes.greaterThan) {
                var state = context.getContainerState();

                Assert.check(state != null, "expected `containerState` to be defined in container");

                if (!state.containsKey("open")) {
                    var token = new Token();
                    token._container = true;
                    effects.enter(Types.blockQuote, token);
                    state.put("open", true);
                }

                effects.enter(Types.blockQuotePrefix);
                effects.enter(Types.blockQuoteMarker);
                effects.consume(code);
                effects.exit(Types.blockQuoteMarker);
                return this::after;
            }

            return nok.step(code);
        }

        State after(int code) {
            if (CharUtil.markdownSpace(code)) {
                effects.enter(Types.blockQuotePrefixWhitespace);
                effects.consume(code);
                effects.exit(Types.blockQuotePrefixWhitespace);
                effects.exit(Types.blockQuotePrefix);
                return ok;
            }

            effects.exit(Types.blockQuotePrefix);
            return ok.step(code);
        }
    }

    private static void exit(TokenizeContext context, Tokenizer.Effects effects) {
        effects.exit(Types.blockQuote);
    }

}
