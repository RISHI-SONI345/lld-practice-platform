package com.lldpractice.lld_practice_platform.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Matches camelCase words of a method name against a capability's name hints. */
public final class CapabilityMatcher {
    private CapabilityMatcher() {
    }

    /** assignSpot -> [assign, spot]. unpark -> [unpark]. */
    public static List<String> words(String name) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_' || c == '$') {
                flush(out, cur);
            } else if (Character.isUpperCase(c) && cur.length() > 0
                    && (Character.isLowerCase(cur.charAt(cur.length() - 1))
                    || (i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1))))) {
                flush(out, cur);
                cur.append(c);
            } else {
                cur.append(c);
            }
        }
        flush(out, cur);
        return out;
    }

    private static void flush(List<String> out, StringBuilder cur) {
        if (cur.length() > 0) {
            out.add(cur.toString().toLowerCase(Locale.ROOT));
            cur.setLength(0);
        }
    }

    /** A method satisfies a capability when one of its words equals or starts with a hint. */
    public static boolean matches(String methodName, List<String> hints) {
        List<String> words = words(methodName);
        for (String hint : hints) {
            String h = hint.toLowerCase(Locale.ROOT);
            for (String w : words) {
                if (w.equals(h) || w.startsWith(h)) return true;
            }
        }
        return false;
    }
}
