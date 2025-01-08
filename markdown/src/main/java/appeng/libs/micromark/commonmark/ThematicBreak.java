package guideme.libs.micromark.commonmark;

import guideme.libs.micromark.Assert;
import guideme.libs.micromark.CharUtil;
import guideme.libs.micromark.Construct;
import guideme.libs.micromark.State;
import guideme.libs.micromark.TokenizeContext;
import guideme.libs.micromark.Tokenizer;
import guideme.libs.micromark.Types;
import guideme.libs.micromark.factory.FactorySpace;
import guideme.libs.micromark.symbol.Codes;
import guideme.libs.micromark.symbol.Constants;

public final class ThematicBreak {
    private ThematicBreak() {
    }

    public static final Construct thematicBreak;

    static {
        thematicBreak = new Construct();
        thematicBreak.name = "thematicBreak";
        thematicBreak.tokenize = (context, effects, ok, nok) -> new StateMachine(context, effects, ok, nok)::start;
    }

    private static class StateMachine {
        private final TokenizeContext context;
        private final Tokenizer.Effects effects;
        private final State ok;
        private final State nok;
        private int size;
        int marker;

        public StateMachine(TokenizeContext context, Tokenizer.Effects effects, State ok, State nok) {

            this.context = context;
            this.effects = effects;
            this.ok = ok;
            this.nok = nok;
        }

        private State start(int code) {
            Assert.check(
                    code == Codes.asterisk ||
                            code == Codes.dash ||
                            code == Codes.underscore,
                    "expected `*`, `-`, or `_`");

            effects.enter(Types.thematicBreak);
            marker = code;
            return atBreak(code);
        }

        private State atBreak(int code) {
            if (code == marker) {
                effects.enter(Types.thematicBreakSequence);
                return sequence(code);
            }

            if (CharUtil.markdownSpace(code)) {
                return FactorySpace.create(effects, this::atBreak, Types.whitespace).step(code);
            }

            if (size < Constants.thematicBreakMarkerCountMin ||
                    (code != Codes.eof && !CharUtil.markdownLineEnding(code))) {
                return nok.step(code);
            }

            effects.exit(Types.thematicBreak);
            return ok.step(code);
        }

        private State sequence(int code) {
            if (code == marker) {
                effects.consume(code);
                size++;
                return this::sequence;
            }

            effects.exit(Types.thematicBreakSequence);
            return atBreak(code);
        }
    }
}
