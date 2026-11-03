package io.github.hxfuxyy.zetacard.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class CardRegistry {
    private CardRegistry() { }

    public static final class Entry {
        public final String id;
        public final String label;
        public final String stockUrl;

        public Entry(String id, String label, String stockUrl) {
            this.id = id;
            this.label = label == null ? "" : label;
            this.stockUrl = stockUrl == null ? "" : stockUrl;
        }
    }

    public static List<Entry> parse(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<Entry> entries = new ArrayList<>();
        for (String line : text.split("\n")) {
            String[] parts = line.split("\t", -1);
            String id = parts[0].trim();
            if (!CardIds.isValid(id)) continue;
            String label = parts.length > 1 ? CardLabels.forImport(parts[1]) : CardLabels.DEFAULT;
            String stockUrl = parts.length > 2 ? parts[2].trim() : "";
            entries.add(new Entry(id, label, stockUrl));
        }
        return entries;
    }

    public static String format(Collection<Entry> entries) {
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            if (!CardIds.isValid(entry.id)) continue;
            out.append(entry.id).append('\t')
                    .append(field(entry.label)).append('\t')
                    .append(field(entry.stockUrl)).append('\n');
        }
        return out.toString();
    }

    private static String field(String value) {
        return CardLabels.stripControl(value);
    }
}
