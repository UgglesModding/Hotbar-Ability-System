package com.abilities.abilitiesplugin;

import com.google.gson.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WeaponRegistry {

    private final Map<String, WeaponDefinition> byItemId = new HashMap<>();

    // shorthand: item -> useDef
    private final Map<String, String> overrideUseDefinition = new HashMap<>();

    // full patch: item -> patch
    private final Map<String, OverridePatch> overridePatches = new HashMap<>();

    private final Gson gson = new GsonBuilder().create();

    // ----------------------------
    // Stats helpers (for debug logs)
    // ----------------------------
    public int countDefinitions() { return byItemId.size(); }
    public int countOverrideMap() { return overrideUseDefinition.size(); }
    public int countOverridePatches() { return overridePatches.size(); }

    // ----------------------------
    // Lookups
    // ----------------------------

    public WeaponDefinition getResolvedDefinition(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        String n = ItemIdUtil.normalizeItemId(itemId);

        // already resolved or base definition
        WeaponDefinition direct = byItemId.get(n);
        if (direct != null) return direct;

        // patch-based override (clone + patch)
        if (ensureRegistered(n)) {
            return byItemId.get(n);
        }

        // shorthand override map to base def
        String useDef = overrideUseDefinition.get(n);
        if (useDef != null && !useDef.isBlank()) {
            WeaponDefinition base = byItemId.get(useDef);
            if (base != null) return base;
        }

        return null;
    }

    public List<WeaponAbilitySlot> getAbilitySlots(String itemId) {
        WeaponDefinition d = getResolvedDefinition(itemId);
        return (d == null) ? null : d.AbilitySlots;
    }

    public String getAbilityBarPath(String itemId) {
        WeaponDefinition d = getResolvedDefinition(itemId);
        return (d == null) ? null : d.AbilityBar;
    }

    // ----------------------------
    // Pack registration methods
    // ----------------------------

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

            // also create a minimal patch so ensureRegistered can build a clone if needed
            OverridePatch p = overridePatches.getOrDefault(nItem, new OverridePatch());
            p.itemId = nItem;
            p.useDefinition = nDef;
            overridePatches.put(nItem, p);
        }

        System.out.println("[WeaponRegistry] Override map merged=" + overridesMap.size() + " from " + sourceTag);
    }

    /**
     * Reads OverrideList JSON entries and merges them.
     * Important detail: If UseDefinition is missing, we fall back to Overrides map.
     */
    public void registerOverrideListFromJson(JsonArray overrideList, String sourceTag) {
        if (overrideList == null || overrideList.size() == 0) return;

        int added = 0;

        for (JsonElement el : overrideList) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();

            String itemId = ItemIdUtil.normalizeItemId(getString(o, "ItemId"));
            if (itemId == null || itemId.isBlank()) continue;

            String useDef = ItemIdUtil.normalizeItemId(getString(o, "UseDefinition"));
            if (useDef == null || useDef.isBlank()) {
                // fallback to shorthand override map
                useDef = overrideUseDefinition.get(itemId);
            }

            if (useDef == null || useDef.isBlank()) {
                System.out.println("[WeaponRegistry] OverrideList entry missing UseDefinition and no fallback Override for ItemId=" + itemId);
                continue;
            }

            String abilityBar = getString(o, "AbilityBar");

            OverridePatch p = new OverridePatch();
            p.itemId = itemId;
            p.useDefinition = useDef;
            p.abilityBar = abilityBar;

            // slot overrides
            JsonObject so = (o.get("SlotOverrides") != null && o.get("SlotOverrides").isJsonObject())
                    ? o.getAsJsonObject("SlotOverrides")
                    : null;

            if (so != null) {
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

            // store shorthand too so it behaves like Overrides
            overrideUseDefinition.put(itemId, useDef);

            // last writer wins (load order)
            overridePatches.put(itemId, p);
            added++;
        }

        System.out.println("[WeaponRegistry] OverrideList merged=" + added + " from " + sourceTag);
    }

    public void registerOverrideList(List<JsonObject> overrideList, String sourceTag) {
        if (overrideList == null || overrideList.isEmpty()) return;

        for (JsonObject o : overrideList) {
            if (o == null) continue;

            String itemId = ItemIdUtil.normalizeItemId(getString(o, "ItemId"));
            String useDef = ItemIdUtil.normalizeItemId(getString(o, "UseDefinition"));
            String abilityBar = getString(o, "AbilityBar");

            if (itemId == null || itemId.isBlank()) continue;

            if (useDef == null || useDef.isBlank()) {
                useDef = overrideUseDefinition.get(itemId);
            }
            if (useDef == null || useDef.isBlank()) continue;

            OverridePatch p = new OverridePatch();
            p.itemId = itemId;
            p.useDefinition = useDef;
            p.abilityBar = abilityBar;

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

            overrideUseDefinition.put(itemId, useDef);
            overridePatches.put(itemId, p); // ✅ fixed (was overrides.put)
        }

        System.out.println("[WeaponRegistry] OverrideList merged (" + overrideList.size() + ") from " + sourceTag);
    }

    // Must exist (AbilityReceiver calls it)
    public boolean registerIndexFromAccess(
            ModPackScanner.ResourceAccess access,
            String indexPath,
            Set<String> visited,
            String sourceTag
    ) {
        if (access == null) return false;
        if (indexPath == null || indexPath.isBlank()) return false;
        if (visited == null) return false;

        String normalized = normalizePath(indexPath);
        String visitKey = sourceTag + "::" + normalized;
        if (!visited.add(visitKey)) return true;

        JsonObject obj = readJsonObject(access, normalized);
        if (obj == null) return false;

        WeaponIndex idx = gson.fromJson(obj, WeaponIndex.class);
        if (idx == null) return false;

        if (idx.Includes != null) {
            for (String p : idx.Includes) {
                if (p == null || p.isBlank()) continue;
                String np = normalizePath(p);
                if (np.endsWith("index.json")) {
                    registerIndexFromAccess(access, np, visited, sourceTag);
                } else {
                    registerWeaponDefFromAccess(access, np, sourceTag);
                }
            }
        }

        if (idx.Weapons != null) {
            for (String p : idx.Weapons) {
                if (p == null || p.isBlank()) continue;
                registerWeaponDefFromAccess(access, normalizePath(p), sourceTag);
            }
        }

        return true;
    }

    private void registerWeaponDefFromAccess(ModPackScanner.ResourceAccess access, String weaponPath, String sourceTag)
    {
        String normalized = normalizePath(weaponPath);

        JsonObject obj = readJsonObject(access, normalized);
        if (obj == null) return;

        WeaponDefinition def = parseWeaponDefinition(obj);
        if (def == null || def.ItemId == null || def.ItemId.isBlank()) return;

        String key = ItemIdUtil.normalizeItemId(def.ItemId);
        def.ItemId = key;
        byItemId.put(key, def);
    }

    // ----------------------------
    // Runtime resolution (patch cloning)
    // ----------------------------

    public boolean ensureRegistered(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        String nItem = ItemIdUtil.normalizeItemId(itemId);

        if (byItemId.containsKey(nItem)) return true;

        OverridePatch patch = overridePatches.get(nItem);
        if (patch == null) return false;

        String useDef = (patch.useDefinition != null) ? ItemIdUtil.normalizeItemId(patch.useDefinition) : null;
        if (useDef == null || useDef.isBlank()) return false;

        WeaponDefinition base = byItemId.get(useDef);
        if (base == null) {
            if (ensureRegistered(useDef)) {
                base = byItemId.get(useDef);
            }
        }

        if (base == null) {
            System.out.println("[WeaponRegistry] Override refers to missing UseDefinition=" + useDef + " for ItemId=" + nItem);
            return false;
        }

        WeaponDefinition resolved = cloneDefinitionForItem(base, nItem);

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

    // ----------------------------
    // Parsing
    // ----------------------------

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
                slot.Consume = getBooleanLenient(sObj, "Consume");

                slots.add(slot);
            }
        }

        def.AbilitySlots = slots;
        return def;
    }

    // ----------------------------
    // Slot override application
    // ----------------------------

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
            for (WeaponAbilitySlot s : base.AbilitySlots) out.add(cloneSlot(s));
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

    // ----------------------------
    // JSON reading helpers
    // ----------------------------

    private JsonObject readJsonObject(ModPackScanner.ResourceAccess access, String resourcePath) {
        if (access == null) return null;

        try (InputStream is = access.open(resourcePath)) {
            if (is == null) return null;

            try (InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonElement el = JsonParser.parseReader(r);
                if (el == null || !el.isJsonObject()) return null;
                return el.getAsJsonObject();
            }
        } catch (IOException e) {
            return null;
        } catch (Throwable t) {
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

    // ----------------------------
    // Small models
    // ----------------------------

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
