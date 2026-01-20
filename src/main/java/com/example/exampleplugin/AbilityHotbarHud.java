package com.example.exampleplugin;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public class AbilityHotbarHud extends CustomUIHud {

    private static final boolean DEBUG_ICONS = true;

    private final AbilityRegistry registry;
    private final AbilityHotbarState state;

    public AbilityHotbarHud(@Nonnull PlayerRef playerRef,
                            @Nonnull AbilityRegistry registry,
                            @Nonnull AbilityHotbarState state) {
        super(playerRef);
        this.registry = registry;
        this.state = state;
    }


    @Override
    protected void build(@Nonnull UICommandBuilder ui) {
        ui.append("AbilityBar.ui");

        var s = state.get(this.getPlayerRef().getUsername());

        // If no bar selected, DO NOT overwrite UI defaults
        if (s.currentAbilityBarId == null || s.currentAbilityBarId.isBlank()) {
            return;
        }

        // Otherwise, apply dynamic icons
        applyIcon(ui, "IconZero", AbilityRegistry.EMPTY_ITEM_ID);

        for (int i = 0; i < 9; i++) {
            applyIcon(ui, "Icon" + (i + 1), s.hotbarItemIds[i]);
        }
    }


    private void applyIcon(UICommandBuilder ui, String nodeId, String inputId) {

        debugSay("Slot " + nodeId + " input = " + inputId);

        String itemId = AbilityItemResolver.resolveItemId(registry, inputId);
        debugSay("Resolved itemId = " + itemId);

        String iconPath = AbilityItemResolver.itemIdToIconPath(itemId);
        debugSay("Icon path = " + iconPath);

        ui.set("#" + nodeId + ".Visible", true);
        ui.set("#" + nodeId + ".Background",
                "PatchStyle(TexturePath: \"" + iconPath + "\")"
        );
    }




    private String iconPathFromItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            itemId = AbilityRegistry.EMPTY_ITEM_ID;
        }
        return "U_Abilities/" + itemId + ".png";
    }
    
    private void debugSay(String message) {
        if (!DEBUG_ICONS) return;

        getPlayerRef().sendMessage(
                com.hypixel.hytale.server.core.Message.raw("[AbilityHUD] " + message)
        );
    }





}
