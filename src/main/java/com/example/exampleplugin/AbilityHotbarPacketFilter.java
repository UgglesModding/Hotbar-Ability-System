package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class AbilityHotbarPacketFilter implements PlayerPacketFilter {

    private final AbilityHotbarState state;
    private final AbilitySystem abilitySystem;

    public AbilityHotbarPacketFilter(AbilityHotbarState state, AbilitySystem abilitySystem) {
        this.state = state;
        this.abilitySystem = abilitySystem;
    }

    @Override
    public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {

        if (!(packet instanceof SyncInteractionChains chains)) {
            return false;
        }

        var s = state.get(playerRef.getUsername());
        if (!s.enabled) {
            return false;
        }

        for (SyncInteractionChain chain : chains.updates) {
            if (!chain.initial) continue;
            if (chain.data == null) continue;

            // CONFIRM: when player uses (you can bind your confirm key to Use)
            if (chain.interactionType == InteractionType.Use) {
                Ref<EntityStore> entityRef = playerRef.getReference();
                if (entityRef == null || !entityRef.isValid()) {
                    return true;
                }

                Store<EntityStore> store = entityRef.getStore();
                World world = store.getExternalData().getWorld();

                world.execute(() -> {
                    // Confirm executes selected slot (0 => selectedAbilitySlot)
                    abilitySystem.useSlot(playerRef, 0);
                });

                return true; // block vanilla Use
            }

            // 1–9 keys (hotbar select) show up as SwapFrom with targetSlot
            if (chain.interactionType == InteractionType.SwapFrom) {
                int originalSlot = chain.activeHotbarSlot;     // 0..8
                int targetSlot = chain.data.targetSlot;        // 0..8

                if (targetSlot < 0 || targetSlot > 8) continue;

                int slot1to9 = targetSlot + 1;
                s.selectedAbilitySlot = slot1to9;

                Ref<EntityStore> entityRef = playerRef.getReference();
                if (entityRef == null || !entityRef.isValid()) {
                    return true; // still block the swap
                }

                Store<EntityStore> store = entityRef.getStore();
                World world = store.getExternalData().getWorld();

                world.execute(() -> {
                    Player playerComponent = store.getComponent(entityRef, Player.getComponentType());
                    if (playerComponent == null) return;

                    // Force server-side slot back
                    playerComponent.getInventory().setActiveHotbarSlot((byte) originalSlot);

                    // Force client-side slot back (fix desync)
                    playerRef.getPacketHandler().write(
                            new SetActiveSlot(Inventory.HOTBAR_SECTION_ID, originalSlot)
                    );

                    // OPTIONAL: select-only (no activation)
                    // abilitySystem.useSlot(playerRef, 0); // confirm uses selected

                    // OR: press 1-9 activates immediately
                    abilitySystem.useSlot(playerRef, slot1to9);

                    playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                            "[Ability] Pressed " + slot1to9 + " -> itemId=" + s.hotbarItemIds[slot1to9 - 1]
                    ));
                });

                return true;
            }
        }

        return false;
    }
}
