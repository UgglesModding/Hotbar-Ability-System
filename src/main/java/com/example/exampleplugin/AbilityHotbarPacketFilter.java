package com.example.exampleplugin;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public class AbilityHotbarPacketFilter implements PlayerPacketFilter {

    private final AbilityHotbarState state;

    public AbilityHotbarPacketFilter(AbilityHotbarState state) {
        this.state = state;
    }

    @Override
    public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {

        if (!(packet instanceof SyncInteractionChains chains)) {
            return false;
        }

        // Per-player state key
        var s = state.get(playerRef.getUsername());
        if (!s.enabled) {
            return false;
        }

        for (SyncInteractionChain chain : chains.updates) {

            if (chain.interactionType != InteractionType.SwapFrom) continue;
            if (!chain.initial) continue;
            if (chain.data == null) continue;

            int targetSlot = chain.data.targetSlot;
            if (targetSlot < 0 || targetSlot > 8) continue;

            // Record ability slot selection
            s.selectedAbilitySlot = targetSlot + 1;

            // Block vanilla hotbar switching
            return true;
        }

        return false;
    }
}
