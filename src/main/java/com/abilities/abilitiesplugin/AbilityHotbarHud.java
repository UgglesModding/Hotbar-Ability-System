package com.abilities.abilitiesplugin;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public class AbilityHotbarHud extends CustomUIHud {

    // Must match the slot size in AbilityBar.ui
    private static final int SLOT_PIXELS = 40;

    private final AbilityHotbarState state;

    public AbilityHotbarHud(
            @Nonnull PlayerRef playerRef,
            @Nonnull AbilityHotbarState state
    ) {
        super(playerRef);
        this.state = state;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder ui) {
        var s = state.get(this.getPlayerRef().getUsername());


        String uiPath = (s.abilityBarUiPath == null || s.abilityBarUiPath.isBlank())
                ? "AbilityBar.ui"
                : s.abilityBarUiPath;

        ui.append(uiPath);

        for (int i = 1; i <= 9; i++) {
            applyUseBar(ui, i, s.hotbarRemainingUses[i - 1], s.hotbarMaxUses[i - 1]);
        }
    }

    private static final int BAR_STEPS = 10;

    private void applyUseBar(UICommandBuilder ui, int barIndex1to9, int remaining, int max) {
        int level;

        if (max <= 0) {
            // Unlimited -> show 100
            level = BAR_STEPS;
        } else if (remaining <= 0) {
            level = 0;
        } else {
            float ratio = Math.min(1.0f, (float) remaining / (float) max);
            level = Math.round(ratio * BAR_STEPS);
            if (level < 0) level = 0;
            if (level > BAR_STEPS) level = BAR_STEPS;
        }

        // Hide Bar{slot}{0..10}
        for (int i = 0; i <= BAR_STEPS; i++) {
            ui.set("#Bar" + barIndex1to9 + i + ".Visible", false);
        }

        // Show the chosen level (including 0)
        ui.set("#Bar" + barIndex1to9 + level + ".Visible", true);
    }



}
