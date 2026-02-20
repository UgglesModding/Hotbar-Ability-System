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
        CooldownWidgetMode cooldownMode = resolveCooldownWidgetMode(uiPath);

        for (int i = 1; i <= 9; i++) {
            applyUseBar(ui, i, s.hotbarRemainingUses[i - 1], s.hotbarMaxUses[i - 1]);
            applyCooldownOverlay(ui, i, s, cooldownMode);
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

    private void applyCooldownOverlay(UICommandBuilder ui, int slot1to9, AbilityHotbarState.State s, CooldownWidgetMode mode) {
        int idx = slot1to9 - 1;
        long now = System.currentTimeMillis();
        float ratio = HCA_AbilityApi.getCooldownOverlayRatio(s, idx, now);

        if (mode == CooldownWidgetMode.PARENT_ONLY) {
            ui.set("#CD" + slot1to9 + ".Visible", ratio > 0.0f);
            return;
        }

        int minStep = (mode == CooldownWidgetMode.SEGMENTED_WITH_ZERO) ? 0 : 1;
        for (int step = minStep; step <= BAR_STEPS; step++) {
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

    private static CooldownWidgetMode resolveCooldownWidgetMode(String uiPath) {
        String fileName = fileNameOnly(uiPath);
        if (fileName.equalsIgnoreCase("AbilityBar.ui")) return CooldownWidgetMode.SEGMENTED_WITH_ZERO;
        if (fileName.equalsIgnoreCase("katanabar.ui")) return CooldownWidgetMode.SEGMENTED_NO_ZERO;
        return CooldownWidgetMode.PARENT_ONLY;
    }

    private static String fileNameOnly(String uiPath) {
        if (uiPath == null || uiPath.isBlank()) return "";
        String normalized = uiPath.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        return (idx >= 0) ? normalized.substring(idx + 1) : normalized;
    }

    private enum CooldownWidgetMode {
        PARENT_ONLY,
        SEGMENTED_WITH_ZERO,
        SEGMENTED_NO_ZERO
    }

}
