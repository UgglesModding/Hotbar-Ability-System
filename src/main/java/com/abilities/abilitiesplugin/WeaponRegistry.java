package com.abilities.abilitiesplugin;

import com.google.gson.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WeaponRegistry {

    // NEW: pack file inside your jar
    private static final String DEFAULT_PACK_PATH = "HCA/HCA_pack.json";

    // Base definitions loaded from indexes (and any direct weapon defs by ItemId)
    private final Map<String, WeaponDefinition> byItemId = new HashMap<>();

    // Resolved (patched) per-item definitions
    private final Map<String, WeaponDefinition> resolvedByItemId = new HashMap<>();

    // Shorthand map: itemId -> useDefinitionId (from "Overrides")
    private final Map<String, String> overrideUseDefinition = new HashMap<>();

    // Full patch map: itemId -> patch (from OverrideList + Overrides synthesized)
    private final Map<String, OverridePatch> patchesByItemId = new HashMap<>();

    // For “mods loaded later can overwrite”
    private final Map<String, Integer> patchRevision = new HashMap<>();
    private final Map<String, Integer> resolvedRevision = new HashMap<>();

    private final Gson gson = new GsonBuilder().create();

    // ==========================================
    // Loading
    // ==========================================

    /** Clears everything and loads ONLY from this pack path in this plugin jar. */
    public void loadAllFromPack() {
        loadAllFromPack(DEFAULT_PACK_PATH);
    }

    public void loadAllFromPack(String packResourcePath) {
        clearAll();

        WeaponPack pack = readPack(getClass().getClassLoader(), packResourcePath);
        if (pack == null) {
            System.out.println("[WeaponRegistry] Missing/invalid pack: " + packResourcePath);
            return;
        }

        mergePack(getClass().getClassLoader(), pack, pack.Source != null ? pack.Source : "HotbarAbilities", true);

        System.out.println("[WeaponRegistry] Loaded defs=" + byItemId.size()
                + " resolved=" + resolvedByItemId.size()
                + " overrideMap=" + overrideUseDefinition.size()
                + " patches=" + patchesByItemId.size()
                + " from pack=" + packResourcePath);
    }

    /**
     * Merge a contributor pack. Does NOT clear. Last loaded wins.
     * This is how other mods can overwrite data after your plugin loads.
     */
    public void mergeContributorPack(ClassLoader contributorLoader, String packResourcePath, String sourceTag) {
        if (contributorLoader == null) return;

        WeaponPack pack = readPack(contributorLoader, packResourcePath);
        if (pack == null) {
            System.out.println("[WeaponRegistry] (Contributor) Missing/invalid pack: " + packResourcePath + " from " + sourceTag);
            return;
        }

        mergePack(contributorLoader, pack, sourceTag, false);

        System.out.println("[WeaponRegistry] (Contributor) Merged pack from " + sourceTag
                + " defs=" + byItemId.size()
                + " resolved=" + resolvedByItemId.size()
                + " patches=" + patchesByItemId.size());
    }

    private void mergePack(ClassLoader loader, WeaponPack pack, String sourceTag, boolean isFreshLoad) {
        if (pack == null) return;

        // 1) Load indexes (base definitions)
        Set<String> visited = new HashSet<>();
        if (pack.Indexes != null) {
            for (String idx : pack.Indexes) {
                if (idx == null || idx.isBlank()) continue;
                loadIndexRecursiveFromLoader(loader, idx, visited, sourceTag);
            }
        }

        // 2) Merge shorthand overrides (Overrides)
        if (pack.Overrides != null && !pack.Overrides.isEmpty()) {
            for (var e : pack.Overrides.entrySet()) {
                String itemId = ItemIdUtil.normalizeItemId(e.getKey());
                String useDef = ItemIdUtil.normalizeItemId(e.getValue());
                if (itemId == null || itemId.isBlank()) continue;
                if (useDef == null || useDef.isBlank()) continue;

                overrideUseDefinition.put(itemId, useDef);

                // Synthesize a patch entry too (so ensureRegistered can build from it even without OverrideList)
                OverridePatch p = patchesByItemId.get(itemId);
                if (p == null) p = new OverridePatch();
                p.itemId = itemId;
                p.useDefinition = useDef;
                patchesByItemId.put(itemId, p);

                bumpPatchRevision(itemId);
            }
        }

        // 3) Merge OverrideList patches (wins over shorthand)
        if (pack.OverrideList != null && !pack.OverrideList.isEmpty()) {
            mergeOverrideList(pack.OverrideList, sourceTag);
        }

        // If this is a merge (mods loaded later), we do NOT clear resolvedByItemId,
        // but revisions will force a rebuild on next access.
        // If you want immediate rebuild, you can call ensureRegistered(itemId) after bump.
    }

    private WeaponPack readPack(ClassLoader loader, String packResourcePath) {
        JsonObject obj = readJsonObjectFromLoader(loader, packResourcePath);
        if (obj == null) return null;

        try {
            return gson.fromJson(obj, WeaponPack.class);
        } catch (Throwable t) {
            System.out.println("[WeaponRegistry] Failed to parse pack " + packResourcePath + " : " + t.getMessage());
            return null;
        }
    }

    // ==========================================
    // Lookups
    // ==========================================

    /** Returns raw base definition if present (no override patching). */
    public WeaponDefinition getBaseDefinition(String itemOrDefId) {
        if (itemOrDefId == null) return null;
        String n = ItemIdUtil.normalizeItemId(itemOrDefId);
        return byItemId.get(n);
    }

    /** Returns resolved definition for the HELD item (applies patches). */
    public WeaponDefinition getResolved(String itemId) {
        return getResolvedDefinition(itemId);
    }

    public List<WeaponAbilitySlot> getAbilitySlots(String itemId) {
        WeaponDefinition d = getResolvedDefinition(itemId);
        return (d == null) ? null : d.AbilitySlots;
    }

    public String getAbilityBarPath(String itemId) {
        WeaponDefinition d = getResolvedDefinition(itemId);
        return (d == null) ? null : d.AbilityBar;
    }

    /**
     * Core resolution:
     * - If item has a patch => ensure it's registered (clone base def -> apply patch -> cache)
     * - Else if item is directly defined => return it
     * - Else if shorthand override exists => return base def (unpatched clone not needed)
     */
    private WeaponDefinition getResolvedDefinition(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        String nItem = ItemIdUtil.normalizeItemId(itemId);

        OverridePatch patch = patchesByItemId.get(nItem);
        if (patch != null) {
            // If patch exists, we must build/refresh the resolved entry
            if (ensureRegistered(nItem)) {
                WeaponDefinition resolved = resolvedByItemId.get(nItem);
                if (resolved != null) return resolved;
            }
            // If patch exists but failed to resolve, fall through (debug will show why)
        }

        // Direct base definition (some mods may define weapon defs matching itemId)
        WeaponDefinition direct = byItemId.get(nItem);
        if (direct != null) return direct;

        // Shorthand override map: item -> useDef
        String useDef = overrideUseDefinition.get(nItem);
        if (useDef != null && !useDef.isBlank()) {
            return byItemId.get(useDef);
        }

        return null;
    }

    // ==========================================
    // Override patching (the logic you asked for)
    // ==========================================

    /**
     * Ensures that for this itemId, if there is a patch, we create/refresh a resolved definition:
     * - if resolved missing OR patch updated => clone from base definition (UseDefinition) and apply patch
     */
    public boolean ensureRegistered(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;

        String nItem = ItemIdUtil.normalizeItemId(itemId);

        OverridePatch patch = patchesByItemId.get(nItem);
        if (patch == null) return false;

        int wantRev = patchRevision.getOrDefault(nItem, 0);
        int haveRev = resolvedRevision.getOrDefault(nItem, -1);

        // If we already built this exact revision, no work needed.
        if (resolvedByItemId.containsKey(nItem) && haveRev == wantRev) {
            return true;
        }

        // Determine base definition id (UseDefinition required here)
        String useDef = (patch.useDefinition != null) ? ItemIdUtil.normalizeItemId(patch.useDefinition) : null;

        // If UseDefinition missing, try shorthand fallback
        if (useDef == null || useDef.isBlank()) {
            useDef = overrideUseDefinition.get(nItem);
        }

        if (useDef == null || useDef.isBlank()) {
            System.out.println("[WeaponRegistry] ensureRegistered failed: no UseDefinition for item=" + nItem);
            return false;
        }

        WeaponDefinition base = byItemId.get(useDef);
        if (base == null) {
            System.out.println("[WeaponRegistry] ensureRegistered failed: missing base definition useDef=" + useDef + " for item=" + nItem);
            return false;
        }

        // Clone base into a new resolved definition bound to this itemId
        WeaponDefinition resolved = cloneDefinitionForItem(base, nItem);

        // Apply patch overwrites AFTER cloning (as you requested)
        if (patch.abilityBar != null && !patch.abilityBar.isBlank()) {
            resolved.AbilityBar = patch.abilityBar;
        }

        if (patch.slotOverrides != null && !patch.slotOverrides.isEmpty()) {
            applySlotOverrides(resolved, patch.slotOverrides);
        }

        // Cache
        resolvedByItemId.put(nItem, resolved);
        resolvedRevision.put(nItem, wantRev);

        System.out.println("[WeaponRegistry] Patched resolve: item=" + nItem + " useDef=" + useDef + " rev=" + wantRev);
        return true;
    }

    private void bumpPatchRevision(String itemId) {
        int next = patchRevision.getOrDefault(itemId, 0) + 1;
        patchRevision.put(itemId, next);
        // do NOT clear resolved here; revision mismatch will force rebuild
    }

    // ==========================================
    // OverrideList merge
    // ==========================================

    private void mergeOverrideList(List<JsonObject> overrideList, String sourceTag) {
        for (JsonObject o : overrideList) {
            if (o == null) continue;

            String itemId = ItemIdUtil.normalizeItemId(getString(o, "ItemId"));
            if (itemId == null || itemId.isBlank()) continue;

            String useDef = ItemIdUtil.normalizeItemId(getString(o, "UseDefinition"));
            String abilityBar = getString(o, "AbilityBar");

            // If UseDefinition is omitted in OverrideList, allow fallback to shorthand map
            if (useDef == null || useDef.isBlank()) {
                useDef = overrideUseDefinition.get(itemId);
            }

            if (useDef == null || useDef.isBlank()) {
                System.out.println("[WeaponRegistry] OverrideList missing UseDefinition and no Overrides fallback for ItemId=" + itemId + " from " + sourceTag);
                continue;
            }

            OverridePatch p = patchesByItemId.get(itemId);
            if (p == null) p = new OverridePatch();

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

            // Also update shorthand map so it behaves like Overrides too
            overrideUseDefinition.put(itemId, useDef);

            // Last loaded wins (overwrite existing patch object)
            patchesByItemId.put(itemId, p);

            bumpPatchRevision(itemId);

            System.out.println("[WeaponRegistry] OverrideList merged: item=" + itemId + " useDef=" + useDef + " from " + sourceTag);
        }
    }

    // ==========================================
    // Index loading
    // ==========================================

    private void loadIndexRecursiveFromLoader(ClassLoader loader, String indexPath, Set<String> visited, String sourceTag) {
        if (loader == null) return;
        if (visited == null) return;

        String normalized = normalizePath(indexPath);
        String visitKey = sourceTag + "::" + normalized;
        if (!visited.add(visitKey)) return;

        JsonObject obj = readJsonObjectFromLoader(loader, normalized);
        if (obj == null) {
            System.out.println("[WeaponRegistry] Missing/invalid index: " + normalized + " from " + sourceTag);
            return;
        }

        WeaponIndex idx = gson.fromJson(obj, WeaponIndex.class);
        if (idx == null) return;

        if (idx.Includes != null) {
            for (String p : idx.Includes) {
                if (p == null || p.isBlank()) continue;
                String np = normalizePath(p);

                if (np.endsWith("index.json")) {
                    loadIndexRecursiveFromLoader(loader, np, visited, sourceTag);
                } else {
                    loadWeaponDefFromLoader(loader, np, sourceTag);
                }
            }
        }

        if (idx.Weapons != null) {
            for (String p : idx.Weapons) {
                if (p == null || p.isBlank()) continue;
                loadWeaponDefFromLoader(loader, normalizePath(p), sourceTag);
            }
        }
    }

    private void loadWeaponDefFromLoader(ClassLoader loader, String weaponPath, String sourceTag) {
        String normalized = normalizePath(weaponPath);

        JsonObject obj = readJsonObjectFromLoader(loader, normalized);
        if (obj == null) {
            System.out.println("[WeaponRegistry] Missing/invalid weapon json: " + normalized + " from " + sourceTag);
            return;
        }

        WeaponDefinition def = parseWeaponDefinition(obj);
        if (def == null || def.ItemId == null || def.ItemId.isBlank()) {
            System.out.println("[WeaponRegistry] Weapon missing ItemId: " + normalized + " from " + sourceTag);
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
                slot.Consume = getBooleanLenient(sObj, "Consume");
                slots.add(slot);
            }
        }

        def.AbilitySlots = slots;
        return def;
    }

    // ==========================================
    // Slot patch application
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
    // JSON helpers
    // ==========================================

    private JsonObject readJsonObjectFromLoader(ClassLoader loader, String resourcePath) {
        if (loader == null) return null;

        String normalized = normalizePath(resourcePath);
        InputStream is = loader.getResourceAsStream(normalized);
        if (is == null) return null;

        try (InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) return null;
            return el.getAsJsonObject();
        } catch (Throwable t) {
            System.out.println("[WeaponRegistry] Failed to read " + normalized + " : " + t.getMessage());
            return null;
        }
    }

    private static String normalizePath(String p) {
        if (p == null) return null;
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
    public void registerOverrideMap(Map<String, String> overridesMap, String sourceTag) {
        if (overridesMap == null || overridesMap.isEmpty()) return;

        for (var e : overridesMap.entrySet()) {
            String itemId = e.getKey();
            String useDef = e.getValue();
            if (itemId == null || itemId.isBlank()) continue;
            if (useDef == null || useDef.isBlank()) continue;

            String nItem = ItemIdUtil.normalizeItemId(itemId);
            String nDef  = ItemIdUtil.normalizeItemId(useDef);

            overrideUseDefinition.put(nItem, nDef);

            // Ensure a patch entry exists so ensureRegistered() can build a resolved def
            OverridePatch p = patchesByItemId.get(nItem);
            if (p == null) p = new OverridePatch();
            p.itemId = nItem;
            p.useDefinition = nDef;
            patchesByItemId.put(nItem, p);

            bumpPatchRevision(nItem);
        }

        System.out.println("[WeaponRegistry] Override map merged (" + overridesMap.size() + ") from " + sourceTag);
    }

    public void registerOverrideList(List<JsonObject> overrideList, String sourceTag) {
        if (overrideList == null || overrideList.isEmpty()) return;
        mergeOverrideList(overrideList, sourceTag);
    }


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
        def.ItemId = key;
        byItemId.put(key, def);

        System.out.println("[WeaponRegistry] (Contributor) Registered: " + key + " slots=" +
                ((def.AbilitySlots == null) ? 0 : def.AbilitySlots.size()) + " from " + sourceTag);
    }


    private static String safeString(JsonElement e) {
        try { return e.getAsString(); } catch (Throwable ignored) { return null; }
    }

    private void clearAll() {
        byItemId.clear();
        resolvedByItemId.clear();
        overrideUseDefinition.clear();
        patchesByItemId.clear();
        patchRevision.clear();
        resolvedRevision.clear();
    }

    // ==========================================
    // Models
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


    private static final class WeaponPack {
        String Source;
        List<String> Indexes;
        Map<String, String> Overrides;
        List<JsonObject> OverrideList;
    }
}
