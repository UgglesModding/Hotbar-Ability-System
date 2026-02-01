package com.abilities.abilitiesplugin;

import com.google.gson.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WeaponRegistry {

    // Your base index (your plugin resources)
    private static final String WEAPON_INDEX_PATH = "Server/Item/Items/Weapons/HCA_Weapons/index.json";

    // Your base overrides (your plugin resources)
    private static final String OVERRIDES_PATH = "Server/Item/Items/Weapons/HCA_Weapons/overrides.json";

    private final Map<String, WeaponDefinition> byItemId = new HashMap<>();
    private final Map<String, String> overrideUseDefinition = new HashMap<>(); // shorthand map: item -> useDef
    private final Map<String, OverridePatch> overrides = new HashMap<>();      // full patch map: item -> patch

    private final Gson gson = new GsonBuilder().create();

    public void loadAllFromResources() {
        byItemId.clear();
        overrideUseDefinition.clear();
        overrides.clear();

        Set<String> visitedIndexes = new HashSet<>();
        loadIndexRecursive(WEAPON_INDEX_PATH, visitedIndexes);

        loadOverrides(OVERRIDES_PATH);

        System.out.println("[WeaponRegistry] Loaded defs=" + byItemId.size()
                + " overrideMap=" + overrideUseDefinition.size()
                + " overridePatches=" + overrides.size());
    }

    // ==========================================
    // Lookups (normalized)
    // ==========================================

    public WeaponDefinition getDefinition(String itemId) {
        if (itemId == null) return null;
        String n = ItemIdUtil.normalizeItemId(itemId);
        return byItemId.get(n);
    }

    public List<WeaponAbilitySlot> getAbilitySlots(String itemId) {
        WeaponDefinition d = getResolvedDefinition(itemId);
        if (d == null) return null;
        return d.AbilitySlots;
    }

    public String getAbilityBarPath(String itemId) {
        WeaponDefinition d = getResolvedDefinition(itemId);
        if (d == null) return null;
        return d.AbilityBar;
    }

    private WeaponDefinition getResolvedDefinition(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        String n = ItemIdUtil.normalizeItemId(itemId);

        // If we already have a resolved/real definition cached, use it.
        WeaponDefinition direct = byItemId.get(n);
        if (direct != null) return direct;

        // If we have an override patch for this item, resolve it now.
        if (ensureRegistered(n)) {
            return byItemId.get(n);
        }

        // If we have shorthand override map: item -> useDef, resolve via base definition
        String useDef = overrideUseDefinition.get(n);
        if (useDef != null && !useDef.isBlank()) {
            WeaponDefinition base = byItemId.get(useDef);
            if (base != null) return base;
        }

        return null;
    }

    // ==========================================
    // Override resolution (runtime)
    // ==========================================

    /**
     * If an itemId has an OverridePatch, we clone its UseDefinition, apply patches, and register it into byItemId.
     * Returns true if it is registered after this call.
     */
    public boolean ensureRegistered(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;

        String nItem = ItemIdUtil.normalizeItemId(itemId);

        if (byItemId.containsKey(nItem)) return true;

        OverridePatch patch = overrides.get(nItem);
        if (patch == null) return false;

        String useDef = (patch.useDefinition != null) ? ItemIdUtil.normalizeItemId(patch.useDefinition) : null;
        if (useDef == null || useDef.isBlank()) return false;

        WeaponDefinition base = byItemId.get(useDef);
        if (base == null) {
            System.out.println("[WeaponRegistry] Override refers to missing UseDefinition=" + useDef + " for ItemId=" + nItem);
            return false;
        }

        WeaponDefinition resolved = cloneDefinitionForItem(base, nItem);

        // Apply patch fields
        if (patch.abilityBar != null && !patch.abilityBar.isBlank()) {
            resolved.AbilityBar = patch.abilityBar;
        }

        if (patch.slotOverrides != null && !patch.slotOverrides.isEmpty()) {
            applySlotOverrides(resolved, patch.slotOverrides);
        }

        byItemId.put(nItem, resolved);
        System.out.println("[WeaponRegistry] Override-registered: " + nItem + " => " + useDef);
        return true;
    }

    // ==========================================
    // Contributions (other mods)
    // ==========================================

    /**
     * Contributors can send their index file paths (inside their own jar).
     * We read using THEIR classloader, and register weapon defs into our registry.
     */
    public boolean registerIndexFromContributor(
            ClassLoader contributorLoader,
            String indexPath,
            Set<String> visited,
            String sourceTag
    ) {
        if (contributorLoader == null) return false;
        if (indexPath == null || indexPath.isBlank()) return false;
        if (visited == null) return false;

        String normalized = normalizePath(indexPath);
        String visitKey = sourceTag + "::" + normalized;
        if (!visited.add(visitKey)) return true;

        JsonObject obj = readJsonObjectFromLoader(contributorLoader, normalized);
        if (obj == null) {
            System.out.println("[WeaponRegistry] (Contributor) Missing/invalid index: " + normalized + " from " + sourceTag);
            return false;
        }

        WeaponIndex idx = gson.fromJson(obj, WeaponIndex.class);
        if (idx == null) return false;

        if (idx.Includes != null) {
            for (String p : idx.Includes) {
                if (p == null || p.isBlank()) continue;
                String np = normalizePath(p);

                if (np.endsWith("index.json")) {
                    registerIndexFromContributor(contributorLoader, np, visited, sourceTag);
                } else {
                    registerWeaponDefFromContributor(contributorLoader, np, sourceTag);
                }
            }
        }

        if (idx.Weapons != null) {
            for (String p : idx.Weapons) {
                if (p == null || p.isBlank()) continue;
                registerWeaponDefFromContributor(contributorLoader, normalizePath(p), sourceTag);
            }
        }

        return true;
    }

    private void registerWeaponDefFromContributor(ClassLoader contributorLoader, String weaponPath, String sourceTag) {
        String normalized = normalizePath(weaponPath);

        JsonObject obj = readJsonObjectFromLoader(contributorLoader, normalized);
        if (obj == null) {
            System.out.println("[WeaponRegistry] (Contributor) Missing/invalid weapon json: " + normalized + " from " + sourceTag);
            return;
        }

        WeaponDefinition def = parseWeaponDefinition(obj);
        if (def == null || def.ItemId == null || def.ItemId.isBlank()) {
            System.out.println("[WeaponRegistry] (Contributor) Weapon missing ItemId: " + normalized + " from " + sourceTag);
            return;
        }

        String key = ItemIdUtil.normalizeItemId(def.ItemId);
        def.ItemId = key; // normalize stored id
        byItemId.put(key, def);

        System.out.println("[WeaponRegistry] (Contributor) Registered: " + key + " slots=" +
                ((def.AbilitySlots == null) ? 0 : def.AbilitySlots.size()) + " from " + sourceTag);
    }

    /**
     * Contributors can also merge shorthand override map: item -> useDef
     */
    public void registerOverrideMap(Map<String, String> overridesMap, String sourceTag) {
        if (overridesMap == null || overridesMap.isEmpty()) return;

        for (var e : overridesMap.entrySet()) {
            String itemId = e.getKey();
            String useDef = e.getValue();
            if (itemId == null || itemId.isBlank()) continue;
            if (useDef == null || useDef.isBlank()) continue;

            String nItem = ItemIdUtil.normalizeItemId(itemId);
            String nDef = ItemIdUtil.normalizeItemId(useDef);

            overrideUseDefinition.put(nItem, nDef);
        }

        System.out.println("[WeaponRegistry] Override map merged (" + overridesMap.size() + ") from " + sourceTag);
    }

    // ==========================================
    // Internal loading (our own jar)
    // ==========================================

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

        WeaponDefinition def = parseWeaponDefinition(obj);
        if (def == null || def.ItemId == null || def.ItemId.isBlank()) {
            System.out.println("[WeaponRegistry] Weapon missing ItemId: " + normalized);
            return;
        }

        String key = ItemIdUtil.normalizeItemId(def.ItemId);
        def.ItemId = key;
        byItemId.put(key, def);
    }

    private WeaponDefinition parseWeaponDefinition(JsonObject obj) {
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
                slot.ID = getString(sObj, "ID");
                slot.Plugin = getBooleanLenient(sObj, "Plugin");
                slot.MaxUses = getInt(sObj, "MaxUses", 0);
                slot.PowerMultiplier = getFloat(sObj, "PowerMultiplier", 1.0f);
                slot.Icon = getString(sObj, "Icon");
                slot.AbilityValue = getInt(sObj, "AbilityValue", 0);
                slot.Consume = getBooleanLenient(sObj, "Consume"); // missing => false

                slots.add(slot);
            }
        }

        def.AbilitySlots = slots;
        return def;
    }

    // ==========================================
    // Overrides loading
    // ==========================================

    private void loadOverrides(String resourcePath) {
        JsonObject obj = readJsonObject(resourcePath);
        if (obj == null) {
            System.out.println("[WeaponRegistry] No overrides found at: " + resourcePath);
            return;
        }

        // 1) Shorthand map: "Overrides": { "Weapon_Sword_Mithril": "HCA_Weapon" }
        JsonElement mapEl = obj.get("Overrides");
        if (mapEl != null && mapEl.isJsonObject()) {
            JsonObject mapObj = mapEl.getAsJsonObject();

            for (Map.Entry<String, JsonElement> e : mapObj.entrySet()) {
                String itemId = e.getKey();
                String useDef = null;
                try { useDef = e.getValue().getAsString(); } catch (Throwable ignored) {}

                if (itemId == null || itemId.isBlank()) continue;
                if (useDef == null || useDef.isBlank()) continue;

                String nItem = ItemIdUtil.normalizeItemId(itemId);
                String nDef = ItemIdUtil.normalizeItemId(useDef);

                overrideUseDefinition.put(nItem, nDef);

                OverridePatch p = new OverridePatch();
                p.itemId = nItem;
                p.useDefinition = nDef;
                overrides.put(nItem, p);
            }
        }

        // 2) OverrideList: richer patches
        JsonElement listEl = obj.get("OverrideList");
        if (listEl != null && listEl.isJsonArray()) {
            JsonArray arr = listEl.getAsJsonArray();

            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();

                String itemId = ItemIdUtil.normalizeItemId(getString(o, "ItemId"));
                String useDef = ItemIdUtil.normalizeItemId(getString(o, "UseDefinition"));
                String abilityBar = getString(o, "AbilityBar");

                if (itemId == null || itemId.isBlank()) continue;
                if (useDef == null || useDef.isBlank()) continue;

                OverridePatch p = new OverridePatch();
                p.itemId = itemId;
                p.useDefinition = useDef;
                p.abilityBar = abilityBar;

                // SlotOverrides: object with keys "1".."9"
                JsonElement soEl = o.get("SlotOverrides");
                if (soEl != null && soEl.isJsonObject()) {
                    JsonObject so = soEl.getAsJsonObject();
                    p.slotOverrides = new HashMap<>();

                    for (Map.Entry<String, JsonElement> se : so.entrySet()) {
                        int idx1to9;
                        try { idx1to9 = Integer.parseInt(se.getKey().trim()); }
                        catch (Throwable t) { continue; }

                        if (idx1to9 < 1 || idx1to9 > 9) continue;
                        if (!se.getValue().isJsonObject()) continue;

                        JsonObject spObj = se.getValue().getAsJsonObject();
                        SlotPatch sp = new SlotPatch();

                        sp.Key = getString(spObj, "Key");
                        sp.RootInteraction = getString(spObj, "RootInteraction");
                        sp.ID = getString(spObj, "ID");
                        sp.Icon = getString(spObj, "Icon");

                        sp.Plugin = getNullableBoolean(spObj, "Plugin");
                        sp.Consume = getNullableBoolean(spObj, "Consume");

                        sp.MaxUses = getNullableInt(spObj, "MaxUses");
                        sp.PowerMultiplier = getNullableFloat(spObj, "PowerMultiplier");
                        sp.AbilityValue = getNullableInt(spObj, "AbilityValue");

                        p.slotOverrides.put(idx1to9, sp);
                    }
                }

                // update shorthand map too (so it still behaves like "Overrides")
                overrideUseDefinition.put(itemId, useDef);

                // last loaded wins
                overrides.put(itemId, p);
            }
        }
    }

    // ==========================================
    // Slot override application
    // ==========================================

    private static void applySlotOverrides(WeaponDefinition def, Map<Integer, SlotPatch> slotOverrides) {
        if (def == null) return;
        if (def.AbilitySlots == null) def.AbilitySlots = new ArrayList<>();

        for (Map.Entry<Integer, SlotPatch> e : slotOverrides.entrySet()) {
            int idx0 = e.getKey() - 1;
            if (idx0 < 0 || idx0 > 8) continue;

            while (def.AbilitySlots.size() < 9) {
                def.AbilitySlots.add(new WeaponAbilitySlot());
            }

            WeaponAbilitySlot slot = def.AbilitySlots.get(idx0);
            if (slot == null) slot = new WeaponAbilitySlot();

            SlotPatch p = e.getValue();
            if (p == null) continue;

            if (p.Key != null) slot.Key = p.Key;
            if (p.RootInteraction != null) slot.RootInteraction = p.RootInteraction;
            if (p.ID != null) slot.ID = p.ID;
            if (p.Icon != null) slot.Icon = p.Icon;

            if (p.Plugin != null) slot.Plugin = p.Plugin;
            if (p.Consume != null) slot.Consume = p.Consume;

            if (p.MaxUses != null) slot.MaxUses = p.MaxUses;
            if (p.PowerMultiplier != null) slot.PowerMultiplier = p.PowerMultiplier;
            if (p.AbilityValue != null) slot.AbilityValue = p.AbilityValue;

            def.AbilitySlots.set(idx0, slot);
        }
    }

    private static WeaponDefinition cloneDefinitionForItem(WeaponDefinition base, String newItemId) {
        WeaponDefinition d = new WeaponDefinition();
        d.ItemId = newItemId;
        d.AbilityBar = base.AbilityBar;

        List<WeaponAbilitySlot> out = new ArrayList<>();
        if (base.AbilitySlots != null) {
            for (WeaponAbilitySlot s : base.AbilitySlots) {
                out.add(cloneSlot(s));
            }
        }
        d.AbilitySlots = out;
        return d;
    }

    private static WeaponAbilitySlot cloneSlot(WeaponAbilitySlot s) {
        WeaponAbilitySlot c = new WeaponAbilitySlot();
        if (s == null) return c;

        c.Key = s.Key;
        c.RootInteraction = s.RootInteraction;
        c.ID = s.ID;
        c.Plugin = s.Plugin;
        c.MaxUses = s.MaxUses;
        c.PowerMultiplier = s.PowerMultiplier;
        c.Icon = s.Icon;
        c.AbilityValue = s.AbilityValue;
        c.Consume = s.Consume;

        return c;
    }

    // ==========================================
    // JSON reading helpers
    // ==========================================

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

    private JsonObject readJsonObjectFromLoader(ClassLoader loader, String resourcePath) {
        InputStream is = loader.getResourceAsStream(resourcePath);
        if (is == null) return null;

        try (InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) return null;
            return el.getAsJsonObject();
        } catch (Throwable t) {
            System.out.println("[WeaponRegistry] Failed to read (Contributor) " + resourcePath + " : " + t.getMessage());
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

    private static Integer getNullableInt(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull()) return null;
        try { return e.getAsInt(); } catch (Throwable ignored) { return null; }
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull()) return def;
        try { return e.getAsFloat(); } catch (Throwable ignored) { return def; }
    }

    private static Float getNullableFloat(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull()) return null;
        try { return e.getAsFloat(); } catch (Throwable ignored) { return null; }
    }

    // Accepts: true/false OR "true"/"false" OR 0/1
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

    private static Boolean getNullableBoolean(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull()) return null;

        try {
            if (e.isJsonPrimitive()) {
                JsonPrimitive p = e.getAsJsonPrimitive();
                if (p.isBoolean()) return p.getAsBoolean();
                if (p.isString()) return Boolean.parseBoolean(p.getAsString().trim().toLowerCase());
                if (p.isNumber()) return p.getAsInt() != 0;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    // ==========================================
    // Small models
    // ==========================================

    private static final class WeaponIndex {
        public List<String> Includes;
        public List<String> Weapons;
    }

    private static final class OverridePatch {
        String itemId;
        String useDefinition;
        String abilityBar;
        Map<Integer, SlotPatch> slotOverrides;
    }

    private static final class SlotPatch {
        String Key;
        String RootInteraction;
        String ID;
        String Icon;
        Boolean Plugin;
        Boolean Consume;
        Integer MaxUses;
        Float PowerMultiplier;
        Integer AbilityValue;
    }
}
