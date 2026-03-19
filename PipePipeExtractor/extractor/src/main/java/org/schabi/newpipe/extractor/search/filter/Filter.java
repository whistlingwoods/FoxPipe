package org.schabi.newpipe.extractor.search.filter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class Filter implements Iterable<String> {

    public static final int ITEM_IDENTIFIER_UNKNOWN = -1;
    private final FilterGroup[] sortGroups;
    private int size = 0;

    private Filter(final FilterGroup[] sortGroups) {
        this.sortGroups = sortGroups;
    }

    public FilterGroup[] getFilterGroups() {
        return sortGroups;
    }

    @Override
    public Iterator<String> iterator() {
        final List<String> names = new ArrayList<>();
        if (sortGroups != null) {
            for (final FilterGroup group : sortGroups) {
                if (group == null || group.filterItems == null) {
                    continue;
                }
                for (final FilterItem item : group.filterItems) {
                    if (item != null) {
                        names.add(item.getName());
                    }
                }
            }
        }
        return names.iterator();
    }

    public static class Builder {
        final Filter filter;

        public Builder(final FilterGroup[] sortGroups) {
            filter = new Filter(sortGroups);
        }

        public Builder setNoOfFilters(final int noOfFilters) {
            filter.size = noOfFilters;
            return this;
        }

        public Filter build() {
            return filter;
        }
    }
}
