package guideme.internal.screen;

import guideme.Guide;
import guideme.PageAnchor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalInMemoryHistory implements GuideScreenHistory {
    private static final int HISTORY_SIZE = 100;
    private static final Logger LOG = LoggerFactory.getLogger(GlobalInMemoryHistory.class);

    private static final Map<ResourceLocation, GuideScreenHistory> PER_GUIDE_HISTORY = new HashMap<>();

    private final ResourceLocation guideId;
    private final List<PageAnchor> history = new ArrayList<>();
    private int historyPosition;

    private GlobalInMemoryHistory(ResourceLocation guideId) {
        this.guideId = guideId;
    }

    public static GuideScreenHistory get(Guide guide) {
        return PER_GUIDE_HISTORY.computeIfAbsent(guide.getId(), GlobalInMemoryHistory::new);
    }

    @Override
    public PageAnchor get(int index) {
        return null;
    }

    @Override
    public void push(PageAnchor anchor) {
        LOG.debug("Pushing {} to history of {}", anchor, guideId);

        // If we're on the same page, replace the anchor
        if (historyPosition < history.size() && history.get(historyPosition).pageId().equals(anchor.pageId())) {
            LOG.debug("Replacing {} with {}", history.get(historyPosition), anchor);
            history.set(historyPosition, anchor);
            return; // Don't duplicate entries
        }

        // Remove anything from the history after the current page when we navigate to a new one
        if (historyPosition + 1 < history.size()) {
            var followingEntries = history.subList(historyPosition + 1, history.size());
            LOG.debug("Cutting tail from history: {}", followingEntries);
            followingEntries.clear();
        }
        // Clamp history length
        if (history.size() >= HISTORY_SIZE) {
            var prunedEntries = history.subList(0, history.size() - HISTORY_SIZE);
            LOG.debug("Pruning from history: {}", prunedEntries);
            prunedEntries.clear();
        }
        // Append to history
        historyPosition = history.size();
        history.add(anchor);
    }

    public Optional<PageAnchor> current() {
        if (historyPosition < history.size()) {
            return Optional.of(history.get(historyPosition));
        }
        return Optional.empty();
    }

    @Override
    public Optional<PageAnchor> forward() {
        var page = peekForward();
        if (page.isPresent()) {
            ++historyPosition;
            LOG.debug("Going forward in history of {}. Position: {}/{}", guideId, historyPosition + 1, history.size());
        }
        return page;
    }

    @Override
    public Optional<PageAnchor> back() {
        var page = peekBack();
        if (page.isPresent()) {
            --historyPosition;
            LOG.debug("Going back in history of {}. Position: {}/{}", guideId, historyPosition + 1, history.size());
        }
        return page;
    }

    @Override
    public Optional<PageAnchor> peekForward() {
        if (historyPosition + 1 < history.size()) {
            return Optional.of(history.get(historyPosition + 1));
        }
        return Optional.empty();
    }

    @Override
    public Optional<PageAnchor> peekBack() {
        if (historyPosition > 0) {
            return Optional.of(history.get(historyPosition - 1));
        }
        return Optional.empty();
    }
}
