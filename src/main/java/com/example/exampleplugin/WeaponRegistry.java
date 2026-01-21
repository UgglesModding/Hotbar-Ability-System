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

        WeaponIndex idx = readJson(WEAPON_INDEX_PATH, WeaponIndex.class);
        if (idx == null || idx.Weapons == null) {
            System.out.println("[WeaponRegistry] Missing/invalid: " + WEAPON_INDEX_PATH);
            return;
        }

        for (String p : idx.Weapons) {
            if (p == null || p.isBlank()) continue;
            loadWeaponDef(normalizePath(p));
        }

        System.out.println("[WeaponRegistry] Loaded weapon defs=" + byItemId.size());
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

    public List<String> resolveAbilityKeys(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;

        // Parent fallback support (optional but useful)
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
                ArrayList<String> out = new ArrayList<>();
                for (WeaponAbilitySlot s : def.AbilitySlots) {
                    if (s == null || s.Key == null || s.Key.isBlank()) continue;
                    out.add(s.Key.trim());
                }
                return out;
            }

            cur = def.Parent;
        }

        return null;
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
