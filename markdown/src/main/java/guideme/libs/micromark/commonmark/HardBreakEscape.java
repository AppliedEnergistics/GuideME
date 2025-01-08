package guideme.libs.micromark.commonmark;

import guideme.libs.micromark.Assert;
import guideme.libs.micromark.CharUtil;
import guideme.libs.micromark.Construct;
import guideme.libs.micromark.State;
import guideme.libs.micromark.TokenizeContext;
import guideme.libs.micromark.Tokenizer;
import guideme.libs.micromark.Types;
import guideme.libs.micromark.symbol.Codes;

public final class HardBreakEscape {
    private HardBreakEscape() {
    }

    public static final Construct hardBreakEscape;

    static {
        hardBreakEscape = new Construct();
        hardBreakEscape.name = "hardBreakEscape";
        hardBreakEscape.tokenize = (context, effects, ok, nok) -> new StateMachine(context, effects, ok, nok)::start;
    }

    private static class StateMachine {
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

        /**
         * Start of a hard break (escape).
         *
         * <pre>
         * > | a\
         *      ^
         *   | b
         * </pre>
         */
        private State start(int code) {
            Assert.check(code == Codes.backslash, "expected `\\`");
            effects.enter(Types.hardBreakEscape);
            effects.consume(code);
            return this::open;
        }

        /**
         * At the end of a hard break (escape), after `\`.
         *
         * <pre>
         * > | a\
         *       ^
         *   | b
         * </pre>
         */
        private State open(int code) {
            if (CharUtil.markdownLineEnding(code)) {
                effects.exit(Types.hardBreakEscape);
                return ok.step(code);
            }

            return nok.step(code);
        }
    }
}
