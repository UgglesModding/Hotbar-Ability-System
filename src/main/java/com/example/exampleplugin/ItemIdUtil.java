package com.example.exampleplugin;

public final class ItemIdUtil {
    private ItemIdUtil() {}

    /**
     * Normalizes:
     * - "Ability_DaggerLeap" -> "Ability_DaggerLeap"
     * - "Items/U_Abilities/Ability_DaggerLeap" -> "Ability_DaggerLeap"
     * - "Items\\U_Abilities\\Ability_DaggerLeap" -> "Ability_DaggerLeap"
     * - "Ability_DaggerLeap.json" -> "Ability_DaggerLeap"
     */
    public static String normalizeItemId(String s) {
        if (s == null) return null;

        s = s.trim();
        if (s.isEmpty()) return null;

        s = s.replace('\\', '/');

        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);

        if (s.endsWith(".json")) s = s.substring(0, s.length() - 5);

        return s;
    }
}
