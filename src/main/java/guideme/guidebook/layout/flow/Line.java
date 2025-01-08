package guideme.guidebook.layout.flow;

import guideme.guidebook.document.LytRect;
import java.util.Objects;
import java.util.stream.Stream;

record Line(LytRect bounds, LineElement firstElement) {
    Stream<LineElement> elements() {
        return Stream.iterate(firstElement, Objects::nonNull, el -> el.next);
    }
}
