// WeaponRegistry.java
package com.example.exampleplugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WeaponRegistry {

    private final Gson gson = new GsonBuilder().create();
    private final Map<String, WeaponItemData> byItemId = new HashMap<>();

    // Your fixed index path (matches your setup)
    public static final String WEAPON_INDEX_PATH =
            "Server/Item/Items/Weapons/CAO_Weapons/index.json";

    public void loadAllFromResources() {
        byItemId.clear();

        Set<String> visitedIndexes = new HashSet<>();
        loadIndexRecursive(WEAPON_INDEX_PATH, visitedIndexes);

        System.out.println("[WeaponRegistry] Loaded weapon defs=" + byItemId.size());
    }

    private void loadIndexRecursive(String indexPath, Set<String> visitedIndexes) {
        String normalized = normalizePath(indexPath);

        if (!visitedIndexes.add(normalized)) {
            // prevents infinite loops (your index currently includes itself)
            return;
        }

        WeaponIndex idx = readJson(normalized, WeaponIndex.class);
        if (idx == null) {
            System.out.println("[WeaponRegistry] Missing/invalid: " + normalized);
            return;
        }

        // New format (your project)
        if (idx.Includes != null) {
            for (String p : idx.Includes) {
                if (p == null || p.isBlank()) continue;

                String np = normalizePath(p);

                // If it looks like another index, recurse; otherwise load weapon def
                if (np.endsWith("/index.json") || np.endsWith("index.json")) {
                    loadIndexRecursive(np, visitedIndexes);
                } else {
                    loadWeaponDef(np);
                }
            }
        }

        // Old format (supported too)
        if (idx.Weapons != null) {
            for (String p : idx.Weapons) {
                if (p == null || p.isBlank()) continue;
                loadWeaponDef(normalizePath(p));
            }
        }
    }


    private void loadWeaponDef(String resourcePath) {
        WeaponItemData data = readJson(resourcePath, WeaponItemData.class);
        if (data == null || data.ItemId == null || data.ItemId.isBlank()) {
            System.out.println("[WeaponRegistry] Invalid weapon json: " + resourcePath);
            return;
        }

        byItemId.put(data.ItemId, data);
        System.out.println("[WeaponRegistry] Loaded: " + data.ItemId + " from " + resourcePath);
    }

    /**
     * Returns the AbilitySlots list for a weapon itemId.
     * Supports Parent fallback: if the weapon has no AbilitySlots, we follow Parent until we find some.
     */
    public List<WeaponAbilitySlot> getAbilitySlots(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;

        Set<String> visited = new HashSet<>();
        String cur = itemId;

        while (cur != null && !cur.isBlank()) {
            if (!visited.add(cur)) {
                System.out.println("[WeaponRegistry] Parent loop detected for " + itemId);
                return null;
            }

            WeaponItemData def = byItemId.get(cur);
            if (def == null) return null;

            if (def.AbilitySlots != null && !def.AbilitySlots.isEmpty()) {
                return def.AbilitySlots;
            }

            cur = def.Parent;
        }

        return null;
    }

    /**
     * Convenience: just the Key values (for older code paths).
     */
    public List<String> resolveAbilityKeys(String itemId) {
        List<WeaponAbilitySlot> slots = getAbilitySlots(itemId);
        if (slots == null) return null;

        ArrayList<String> out = new ArrayList<>();
        for (WeaponAbilitySlot s : slots) {
            if (s == null || s.Key == null || s.Key.isBlank()) continue;
            out.add(s.Key.trim());
        }
        return out;
    }

    private String normalizePath(String path) {
        String p = path.trim().replace('\\', '/');
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    private <T> T readJson(String resourcePath, Class<T> clazz) {
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            System.out.println("[WeaponRegistry] Resource not found: " + resourcePath);
            return null;
        }

        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, clazz);
        } catch (Exception e) {
            System.out.println("[WeaponRegistry] JSON parse error in " + resourcePath);
            e.printStackTrace();
            return null;
        }
    }
}
