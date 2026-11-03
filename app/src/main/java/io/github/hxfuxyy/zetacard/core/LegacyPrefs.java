package io.github.hxfuxyy.zetacard.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyPrefs {
    private static final Pattern IDS_SET = Pattern.compile("<set\\s+name=\"ids\"\\s*>(.*?)</set>", Pattern.DOTALL);
    private static final Pattern SET_ITEM = Pattern.compile("<string>([^<]*)</string>");
    private static final Pattern NAME = Pattern.compile("<string\\s+name=\"name\\.([0-9a-f]{64})\"\\s*>([^<]*)</string>");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);");

    private LegacyPrefs() { }

    public static List<CardRegistry.Entry> parse(String xml) {
        if (xml == null || xml.isEmpty()) return Collections.emptyList();
        Map<String, String> names = new LinkedHashMap<>();
        Matcher name = NAME.matcher(xml);
        while (name.find()) names.put(name.group(1), CardLabels.forUserInput(decode(name.group(2))));
        Map<String, CardRegistry.Entry> entries = new LinkedHashMap<>();
        Matcher set = IDS_SET.matcher(xml);
        if (set.find()) {
            Matcher item = SET_ITEM.matcher(set.group(1));
            while (item.find()) add(entries, decode(item.group(1)).trim(), names);
        }
        for (String id : names.keySet()) add(entries, id, names);
        return new ArrayList<>(entries.values());
    }

    private static void add(Map<String, CardRegistry.Entry> entries, String id, Map<String, String> names) {
        if (!CardIds.isValid(id) || entries.containsKey(id)) return;
        String label = names.get(id);
        entries.put(id, new CardRegistry.Entry(id, label == null || label.isEmpty() ? CardLabels.DEFAULT : label, ""));
    }

    private static String codePoint(String entity) {
        try {
            int code = entity.startsWith("#x") ? Integer.parseInt(entity.substring(2), 16) : Integer.parseInt(entity.substring(1));
            return Character.isValidCodePoint(code) ? new String(Character.toChars(code)) : "";
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    static String decode(String value) {
        Matcher matcher = ENTITY.matcher(value);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String entity = matcher.group(1);
            String replacement;
            switch (entity) {
                case "amp": replacement = "&"; break;
                case "lt": replacement = "<"; break;
                case "gt": replacement = ">"; break;
                case "quot": replacement = "\""; break;
                case "apos": replacement = "'"; break;
                default:
                    replacement = codePoint(entity);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
