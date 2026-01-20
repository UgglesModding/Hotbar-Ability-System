package com.example.exampleplugin;

public final class AbilityItemResolver {

    private AbilityItemResolver() {}

    /**
     * Accepts either:
     *  - ability id: "uggles_combat:daggerleap"
     *  - item id:    "Ability_DaggerLeap"
     *
     * Returns an IN-GAME ITEM ID that ItemStack understands.
     */
    public static String resolveItemId(AbilityRegistry registry, String input) {
        if (input == null || input.isBlank()) {
            return AbilityRegistry.EMPTY_ITEM_ID;
        }

        // If they passed an ability id, convert to item id
        AbilityData ability = registry.getAbility(input);
        if (ability != null) {
            // IMPORTANT: you said the real in-game id is the filename like "Ability_DaggerLeap"
            if (ability.ItemAsset != null && !ability.ItemAsset.isBlank()) {
                return ability.ItemAsset;
            }
            // fallback to empty if ability exists but doesn't define item id
            return AbilityRegistry.EMPTY_ITEM_ID;
        }

        // Otherwise treat input as already being an in-game item id
        return input;
    }

    /**
     * Convert an item id into its icon texture path.
     * This assumes your generated icons follow this naming convention.
     */
    public static String itemIdToIconPath(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            itemId = AbilityRegistry.EMPTY_ITEM_ID;
        }

        // If your icons are exactly: Common/Icons/ItemsGenerated/<ItemId>.png
        return "Icons/ItemsGenerated/" + itemId + ".png";
    }
}
