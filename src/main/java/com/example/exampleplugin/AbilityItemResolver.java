package com.example.exampleplugin;

public final class AbilityItemResolver {

    private AbilityItemResolver() {}

    public static String itemIdToIconPath(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            itemId = AbilityRegistry.EMPTY_ITEM_ID;
        }

        // AbilityBar.ui is in: Common/UI/Custom/
        // Icons are in:        Common/Icons/ItemsGenerated/
        // => go up twice
        return "../../Icons/ItemsGenerated/" + itemId + ".png";
    }
}
