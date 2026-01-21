package com.example.exampleplugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AbilityRegistry {

    private final Gson gson = new GsonBuilder().create();

    // Ability ID: "uggles_combat:daggerleap"
    private final Map<String, AbilityData> abilitiesById = new HashMap<>();

    // Item ID (ingame filename): "Ability_DaggerLeap"
    private final Map<String, AbilityData> abilitiesByItemId = new HashMap<>();

    // ItemAsset path: "Items/U_Abilities/Ability_DaggerLeap"
    private final Map<String, AbilityData> abilitiesByItemAsset = new HashMap<>();

    private final Map<String, AbilityBarData> barsById = new HashMap<>();

    public static final String EMPTY_ITEM_ID = "Ability_Empty";
    public static final String EMPTY_ABILITY_ID = "uggles_combat:empty";

    public void loadAllFromResources() {
        abilitiesById.clear();
        abilitiesByItemId.clear();
        abilitiesByItemAsset.clear();
        barsById.clear();

        // 1) Load ability index
        AbilityIndex abilityIndex = readJson("Server/U_Abilities/index.json", AbilityIndex.class);
        if (abilityIndex == null || abilityIndex.abilities == null) {
            System.out.println("[AbilityRegistry] Missing or invalid: Server/U_Abilities/index.json");
        } else {
            for (String path : abilityIndex.abilities) {
                if (path == null || path.isBlank()) continue;
                loadAbility(normalizePath(path));
            }
        }

        // 2) Load bar index
        AbilityBarIndex barIndex = readJson("Server/U_AbilityBars/index.json", AbilityBarIndex.class);
        if (barIndex == null || barIndex.bars == null) {
            System.out.println("[AbilityRegistry] Missing or invalid: Server/U_AbilityBars/index.json");
        } else {
            for (String path : barIndex.bars) {
                if (path == null || path.isBlank()) continue;
                loadBar(normalizePath(path));
            }
        }

        System.out.println("[AbilityRegistry] Loaded abilities=" + abilitiesById.size() + " bars=" + barsById.size());
        if (!abilitiesById.containsKey(EMPTY_ABILITY_ID)) {
            System.out.println("[AbilityRegistry] WARNING: missing required ability id " + EMPTY_ABILITY_ID);
        }
        if (!abilitiesByItemId.containsKey(EMPTY_ITEM_ID)) {
            System.out.println("[AbilityRegistry] WARNING: missing required empty item id " + EMPTY_ITEM_ID);
        }
    }

    public AbilityData getAbility(String abilityId) {
        return abilitiesById.get(abilityId);
    }

    public AbilityData getAbilityByItemId(String itemId) {
        if (itemId == null) return null;
        return abilitiesByItemId.get(itemId);
    }

    public AbilityData getAbilityByItemAsset(String itemAsset) {
        if (itemAsset == null) return null;
        return abilitiesByItemAsset.get(itemAsset);
    }

    public AbilityBarData getBar(String barId) {
        return barsById.get(barId);
    }

    private void loadAbility(String resourcePath) {
        AbilityData data = readJson(resourcePath, AbilityData.class);
        if (data == null) {
            System.out.println("[AbilityRegistry] Ability NOT FOUND or FAILED: " + resourcePath);
            return;
        }

        if (data.ID == null || data.ID.isBlank()) {
            System.out.println("[AbilityRegistry] Ability missing ID: " + resourcePath);
            return;
        }

        abilitiesById.put(data.ID, data);

        if (data.ItemAsset != null && !data.ItemAsset.isBlank()) {
            abilitiesByItemAsset.put(data.ItemAsset, data);
            String itemId = normalizeItemIdFromItemAsset(data.ItemAsset);
            if (itemId != null && !itemId.isBlank()) {
                abilitiesByItemId.put(itemId, data);
            }
        }

        String use = (data.Interactions == null) ? null : data.Interactions.Use;
        System.out.println("[AbilityRegistry] " + data.ID +
                " Interactions=" + (data.Interactions == null ? "null" : "ok") +
                " Use=" + (data.Interactions == null ? "null" : data.Interactions.Use));
    }


    private void loadBar(String resourcePath) {
        AbilityBarData data = readJson(resourcePath, AbilityBarData.class);
        if (data == null) {
            System.out.println("[AbilityRegistry] Bar NOT FOUND or FAILED: " + resourcePath);
            return;
        }
        if (data.ID == null || data.ID.isBlank()) {
            System.out.println("[AbilityRegistry] Bar missing ID: " + resourcePath);
            return;
        }
        barsById.put(data.ID, data);
        System.out.println("[AbilityRegistry] Loaded bar: " + data.ID + " (abilities=" +
                (data.Abilities == null ? 0 : data.Abilities.size()) + ") from " + resourcePath);
    }

    private static String normalizeItemIdFromItemAsset(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;

        s = s.replace('\\', '/');

        // Take last segment after '/'
        int slash = s.lastIndexOf('/');
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }

        // Strip ".json" if present
        if (s.endsWith(".json")) {
            s = s.substring(0, s.length() - 5);
        }

        return s;
    }

    private String normalizePath(String path) {
        String p = path.trim();
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    private <T> T readJson(String resourcePath, Class<T> clazz) {
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) return null;

        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, clazz);
        } catch (Exception e) {
            System.out.println("[AbilityRegistry] JSON parse error in " + resourcePath);
            e.printStackTrace();
            return null;
        }
    }
}
