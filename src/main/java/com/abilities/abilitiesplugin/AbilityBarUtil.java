package com.abilities.abilitiesplugin;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class AbilityBarUtil {

    private AbilityBarUtil() {}

    private static final class EmptyHud extends CustomUIHud {
        public EmptyHud(@Nonnull PlayerRef ref) { super(ref); }
        @Override protected void build(@Nonnull UICommandBuilder ui) {}
    }

    /** Turns the bar OFF if it is currently enabled. Safe to call any time. */
    public static void turnOffIfOn(AbilityHotbarState state, Player player, PlayerRef playerRef) {
        if (state == null || player == null || playerRef == null) return;

        var s = state.get(playerRef.getUsername());
        if (s == null) return;

        if (!s.enabled) return; // already off

        s.enabled = false;
        player.getHudManager().setCustomHud(playerRef, new EmptyHud(playerRef));
    }

    /** Forces OFF (even if already off). */
    public static void forceOff(AbilityHotbarState state, Player player, PlayerRef playerRef) {
        if (state == null || player == null || playerRef == null) return;

        var s = state.get(playerRef.getUsername());
        if (s != null) s.enabled = false;

        player.getHudManager().setCustomHud(playerRef, new EmptyHud(playerRef));
    }
}
