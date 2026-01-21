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

    // Ability JSON "ID" (namespaced) -> data
    private final Map<String, AbilityData> abilitiesById = new HashMap<>();

    // In-game item id (e.g. "Ability_DaggerLeap") -> data
    private final Map<String, AbilityData> abilitiesByItemId = new HashMap<>();

    // Optional: ItemAsset path (e.g. "Items/U_Abilities/Ability_DaggerLeap") -> data
    private final Map<String, AbilityData> abilitiesByItemAsset = new HashMap<>();

    public static final String EMPTY_ITEM_ID = "Ability_Empty";
    public static final String EMPTY_ABILITY_ID = "uggles_combat:empty";

    public void loadAllFromResources() {
        abilitiesById.clear();
        abilitiesByItemId.clear();
        abilitiesByItemAsset.clear();

        // ✅ NEW PATH (matches your Look.zip)
        AbilityIndex abilityIndex = readJson(
                "Server/UgglesCombat/U_Abilities/index.json",
                AbilityIndex.class
        );

        if (abilityIndex == null || abilityIndex.abilities == null) {
            System.out.println("[AbilityRegistry] Missing or invalid: Server/Item/Items/U_Abilities/index.json");
            return;
        }

        for (String path : abilityIndex.abilities) {
            if (path == null || path.isBlank()) continue;
            loadAbility(normalizePath(path));
        }

        System.out.println("[AbilityRegistry] Loaded abilities=" + abilitiesById.size());

        if (!abilitiesById.containsKey(EMPTY_ABILITY_ID)) {
            System.out.println("[AbilityRegistry] WARNING: missing required ability id " + EMPTY_ABILITY_ID);
        }
    }

    public AbilityData getAbility(String namespacedId) {
        return abilitiesById.get(namespacedId);
    }

    public AbilityData getAbilityByItemId(String itemId) {
        return abilitiesByItemId.get(itemId);
    }

    public AbilityData getAbilityByItemAsset(String itemAsset) {
        return abilitiesByItemAsset.get(itemAsset);
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

        // ✅ Derive in-game item id from ItemAsset if present
        // ItemAsset example: "Items/U_Abilities/Ability_DaggerLeap" => itemId "Ability_DaggerLeap"
        if (data.ItemAsset != null && !data.ItemAsset.isBlank()) {
            abilitiesByItemAsset.put(data.ItemAsset, data);

            String itemId = AbilitySystem.normalizeItemId(data.ItemAsset);
            if (itemId != null && !itemId.isBlank()) {
                abilitiesByItemId.put(itemId, data);
            }
        }

        System.out.println("[AbilityRegistry] Loaded ability: " + data.ID + " (ItemAsset=" + data.ItemAsset + ")");
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
