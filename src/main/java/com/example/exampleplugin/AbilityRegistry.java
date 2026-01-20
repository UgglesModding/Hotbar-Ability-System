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

    private final Map<String, AbilityData> abilitiesById = new HashMap<>();
    private final Map<String, AbilityData> abilitiesByItemAsset = new HashMap<>();
    private final Map<String, AbilityBarData> barsById = new HashMap<>();
    public static final String EMPTY_ITEM_ID = "Ability_Empty";


    public static final String EMPTY_ABILITY_ID = "uggles_combat:empty";

    public void loadAllFromResources() {
        abilitiesById.clear();
        barsById.clear();
        abilitiesByItemAsset.clear();

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
    }

    public AbilityData getAbility(String id) {
        return abilitiesById.get(id);
    }

    public AbilityData getAbilityByItemAsset(String itemAsset) {
        return abilitiesByItemAsset.get(itemAsset);
    }


    public AbilityBarData getBar(String id) {
        return barsById.get(id);
    }

    private void loadAbility(String resourcePath) {
        AbilityData data = readJson(resourcePath, AbilityData.class);
        if (data == null) {
            System.out.println("[AbilityRegistry] Ability NOT FOUND or FAILED: " + resourcePath);
            return;
        }
        if (data.ID == null || data.ID.isBlank()) {
            System.out.println("[AbilityRegistry] Ability missing id: " + resourcePath);
            return;
        }
        abilitiesById.put(data.ID, data);
        if (data.ItemAsset != null && !data.ItemAsset.isBlank()) {
            abilitiesByItemAsset.put(data.ItemAsset, data);
        }

        System.out.println("Loaded Ability JSON => ID=" + data.ID + " Icon=" + data.Icon + " ItemAsset=" + data.ItemAsset);
        System.out.println("[AbilityRegistry] Loaded ability: " + data.ID + " from " + resourcePath);
    }

    private void loadBar(String resourcePath) {
        AbilityBarData data = readJson(resourcePath, AbilityBarData.class);
        if (data == null) {
            System.out.println("[AbilityRegistry] Bar NOT FOUND or FAILED: " + resourcePath);
            return;
        }
        if (data.ID == null || data.ID.isBlank()) {
            System.out.println("[AbilityRegistry] Bar missing id: " + resourcePath);
            return;
        }
        barsById.put(data.ID, data);
        System.out.println("[AbilityRegistry] Loaded bar: " + data.ID + " (abilities=" +
                (data.Abilities == null ? 0 : data.Abilities.size()) + ") from " + resourcePath);
    }

    private String normalizePath(String path) {
        // Allow index to list either:
        // - "Server/U_Abilities/daggerleap.json"
        // - "/Server/U_Abilities/daggerleap.json"
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
