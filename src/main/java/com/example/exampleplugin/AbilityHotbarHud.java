package com.example.exampleplugin;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public class AbilityHotbarHud extends CustomUIHud {

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

        // Always paint icons from state (no bar id concept anymore)
        applyIcon(ui, "IconZero", AbilityRegistry.EMPTY_ITEM_ID);

        for (int i = 0; i < 9; i++) {
            applyIcon(ui, "Icon" + (i + 1), s.hotbarItemIds[i]);
        }
    }

    private void applyIcon(UICommandBuilder ui, String nodeId, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            itemId = AbilityRegistry.EMPTY_ITEM_ID;
        }

        // icon path convention in your pack: Common/Icons/ItemsGenerated/<ItemId>.png
        String iconPath = AbilityItemResolver.itemIdToIconPath(itemId);

        ui.set("#" + nodeId + ".Visible", true);
        ui.set("#" + nodeId + ".Background",
                "PatchStyle(TexturePath: \"" + iconPath + "\")"
        );
    }
}
