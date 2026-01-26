package com.abilities.abilitiesplugin;

public final class ItemIdUtil {
    private ItemIdUtil() {}


    public static String normalizeItemId(String s) {
        if (s == null) return null;

        s = s.trim();
        if (s.isEmpty()) return null;

        s = s.replace('\\', '/');

        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);

        if (s.endsWith(".json")) s = s.substring(0, s.length() - 5);

        int colon = s.lastIndexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);

        return s;
    }

}
