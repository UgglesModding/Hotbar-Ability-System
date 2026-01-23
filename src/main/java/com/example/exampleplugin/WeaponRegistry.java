package com.example.exampleplugin;

import com.google.gson.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WeaponRegistry {

    // change if your root index path is different
    private static final String WEAPON_INDEX_PATH = "Server/Item/Items/Weapons/CAO_Weapons/index.json";

    private final Map<String, WeaponDefinition> byItemId = new HashMap<>();
    private final Gson gson = new GsonBuilder().create();

    public void loadAllFromResources() {
        byItemId.clear();

        Set<String> visitedIndexes = new HashSet<>();
        loadIndexRecursive(WEAPON_INDEX_PATH, visitedIndexes);

        System.out.println("[WeaponRegistry] Loaded weapon defs=" + byItemId.size());
    }

    public List<WeaponAbilitySlot> getAbilitySlots(String itemId) {
        WeaponDefinition def = byItemId.get(itemId);
        if (def == null) return null;
        return def.AbilitySlots;
    }

    public String getAbilityBarPath(String itemId) {
        WeaponDefinition def = byItemId.get(itemId);
        if (def == null) return null;
        return def.AbilityBar;
    }

    private void loadIndexRecursive(String indexPath, Set<String> visitedIndexes) {
        String normalized = normalizePath(indexPath);
        if (!visitedIndexes.add(normalized)) return;

        JsonObject obj = readJsonObject(normalized);
        if (obj == null) {
            System.out.println("[WeaponRegistry] Missing/invalid index: " + normalized);
            return;
        }

        WeaponIndex idx = gson.fromJson(obj, WeaponIndex.class);
        if (idx == null) return;

        if (idx.Includes != null) {
            for (String p : idx.Includes) {
                if (p == null || p.isBlank()) continue;
                String np = normalizePath(p);

                if (np.endsWith("index.json")) {
                    loadIndexRecursive(np, visitedIndexes);
                } else {
                    loadWeaponDef(np);
                }
            }
        }

        if (idx.Weapons != null) {
            for (String p : idx.Weapons) {
                if (p == null || p.isBlank()) continue;
                loadWeaponDef(normalizePath(p));
            }
        }
    }

    private void loadWeaponDef(String weaponPath) {
        String normalized = normalizePath(weaponPath);

        JsonObject obj = readJsonObject(normalized);
        if (obj == null) {
            System.out.println("[WeaponRegistry] Missing/invalid weapon json: " + normalized);
            return;
        }

        // Manual parse so we can accept Plugin as boolean OR string
        WeaponDefinition def = new WeaponDefinition();
        def.ItemId = getString(obj, "ItemId");
        def.AbilityBar = getString(obj, "AbilityBar");

        JsonArray slotsArr = obj.getAsJsonArray("AbilitySlots");
        List<WeaponAbilitySlot> slots = new ArrayList<>();
        if (slotsArr != null) {
            for (JsonElement el : slotsArr) {
                if (!el.isJsonObject()) continue;
                JsonObject sObj = el.getAsJsonObject();

                WeaponAbilitySlot slot = new WeaponAbilitySlot();
                slot.Key = getString(sObj, "Key");
                slot.RootInteraction = getString(sObj, "RootInteraction");
                slot.ID = getString(sObj, "ID"); // capitalized key
                slot.Plugin = getBooleanLenient(sObj, "Plugin");
                slot.MaxUses = getInt(sObj, "MaxUses", 0);
                slot.PowerMultiplier = getFloat(sObj, "PowerMultiplier", 1.0f);
                slot.Icon = getString(sObj, "Icon");

                slots.add(slot);
            }
        }

        def.AbilitySlots = slots;

        if (def.ItemId == null || def.ItemId.isBlank()) {
            System.out.println("[WeaponRegistry] Weapon missing ItemId: " + normalized);
            return;
        }

        byItemId.put(def.ItemId, def);
        System.out.println("[WeaponRegistry] Registered: " + def.ItemId + " slots=" + slots.size());
    }

    private JsonObject readJsonObject(String resourcePath) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) return null;

        try (InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) return null;
            return el.getAsJsonObject();
        } catch (Throwable t) {
            System.out.println("[WeaponRegistry] Failed to read " + resourcePath + " : " + t.getMessage());
            return null;
        }
    }

    private static String normalizePath(String p) {
        String s = p.replace("\\", "/").trim();
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull()) return null;
        try { return e.getAsString(); } catch (Throwable ignored) { return null; }
    }

    private static int getInt(JsonObject obj, String key, int def) {
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull()) return def;
        try { return e.getAsInt(); } catch (Throwable ignored) { return def; }
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull()) return def;
        try { return e.getAsFloat(); } catch (Throwable ignored) { return def; }
    }

    // Accepts: true/false OR "True"/"False"/"true"/"false"
    private static boolean getBooleanLenient(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull()) return false;

        try {
            if (e.isJsonPrimitive()) {
                JsonPrimitive p = e.getAsJsonPrimitive();
                if (p.isBoolean()) return p.getAsBoolean();
                if (p.isString()) return Boolean.parseBoolean(p.getAsString().trim().toLowerCase());
                if (p.isNumber()) return p.getAsInt() != 0;
            }
        } catch (Throwable ignored) {}

        return false;
    }
}
