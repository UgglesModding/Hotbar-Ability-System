package com.abilities.abilitiesplugin;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public class AbilityHotbarHud extends CustomUIHud {

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
        HCA_AbilityApi.TickAllSlots(this.getPlayerRef());


        String uiPath = normalizeUiPath(s.abilityBarUiPath);
        ui.append(uiPath);

        for (int i = 1; i <= 9; i++) {
            applyUseBar(ui, i, s.hotbarRemainingUses[i - 1], s.hotbarMaxUses[i - 1]);
            applyCooldownOverlay(ui, i, s);
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

    private void applyCooldownOverlay(UICommandBuilder ui, int slot1to9, AbilityHotbarState.State s) {
        int idx = slot1to9 - 1;
        long now = System.currentTimeMillis();
        float ratio = HCA_AbilityApi.getCooldownOverlayRatio(s, idx, now);

        for (int step = 0; step <= BAR_STEPS; step++) {
            ui.set("#CD" + slot1to9 + step + ".Visible", false);
        }

        if (ratio <= 0.0f) {
            ui.set("#CD" + slot1to9 + ".Visible", false);
            return;
        }

        int level = (int) Math.ceil(ratio * BAR_STEPS);
        if (level < 1) level = 1;
        if (level > BAR_STEPS) level = BAR_STEPS;

        ui.set("#CD" + slot1to9 + ".Visible", true);
        ui.set("#CD" + slot1to9 + level + ".Visible", true);
    }

    private static String normalizeUiPath(String uiPath) {
        if (uiPath == null || uiPath.isBlank()) return "AbilityBar.ui";

        String cleaned = uiPath.trim().replace('\\', '/');
        if (cleaned.isEmpty()) return "AbilityBar.ui";

        String lower = cleaned.toLowerCase();
        if (!lower.endsWith(".ui")) cleaned = cleaned + ".ui";

        return cleaned;
    }

}
