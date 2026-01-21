package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class AbilityHotbarPacketFilter implements PlayerPacketFilter {

    // Turn this ON while you debug Q
    private static final boolean DEBUG_INTERACTIONS = true;

    private final AbilityHotbarState state;
    private final AbilitySystem abilitySystem;

    // Safe empty HUD
    private static final class EmptyHud extends CustomUIHud {
        public EmptyHud(@Nonnull PlayerRef playerRef) { super(playerRef); }
        @Override protected void build(@Nonnull UICommandBuilder ui) { }
    }

    public AbilityHotbarPacketFilter(AbilityHotbarState state, AbilitySystem abilitySystem) {
        this.state = state;
        this.abilitySystem = abilitySystem;
    }

    @Override
    public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {

        if (!(packet instanceof SyncInteractionChains chains)) {
            return false;
        }

        // Get state (always)
        var s = state.get(playerRef.getUsername());

        // We need world thread access for HUD changes / inventory forcing
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            return false;
        }

        Store<EntityStore> store = entityRef.getStore();
        World world = store.getExternalData().getWorld();

        for (SyncInteractionChain chain : chains.updates) {
            if (!chain.initial) continue;

            // ---- DEBUG: show every interaction type that arrives ----
            if (DEBUG_INTERACTIONS) {
                String dataStr = (chain.data == null) ? "null" : chain.data.toString();
                playerRef.sendMessage(Message.raw(
                        "[DBG] it=" + chain.interactionType.name()
                                + " active=" + chain.activeHotbarSlot
                                + " data=" + dataStr
                ));
                System.out.println("[DBG] " + playerRef.getUsername()
                        + " it=" + chain.interactionType.name()
                        + " active=" + chain.activeHotbarSlot
                        + " data=" + dataStr);
            }

            // ---- 1) Q toggle: detect Ability1 ----
            // We avoid hard enum constants so this compiles on any SDK:
            // If your packets call it "Ability1", this will catch it.
            String itName = chain.interactionType.name();
            if (itName.equalsIgnoreCase("Ability1")) {

                // Toggle HUD on world thread
                world.execute(() -> {
                    Player playerComponent = store.getComponent(entityRef, Player.getComponentType());
                    if (playerComponent == null) return;

                    var hudManager = playerComponent.getHudManager();

                    s.enabled = !s.enabled;

                    if (s.enabled) {
                        // Optional: ensure slot data isn't null (you can replace later with weapon-reading logic)
                        // abilitySystem.setSlots(playerRef, ...);
                        hudManager.setCustomHud(playerRef, new AbilityHotbarHud(playerRef, abilitySystem.getRegistry(), state));
                        playerRef.sendMessage(Message.raw("[AbilityBar] ON (toggled by Ability1/Q)"));
                    } else {
                        hudManager.setCustomHud(playerRef, new EmptyHud(playerRef));
                        playerRef.sendMessage(Message.raw("[AbilityBar] OFF (toggled by Ability1/Q)"));
                    }
                });

                // Block vanilla Ability1 from firing while we use it as a toggle
                return true;
            }

            // ---- 2) If bar is not enabled, don't block normal hotbar behavior ----
            if (!s.enabled) {
                continue;
            }

            // ---- 3) 1–9 key interception (SwapFrom) ----
            if (chain.interactionType == InteractionType.SwapFrom) {
                if (chain.data == null) continue;

                int originalSlot = chain.activeHotbarSlot; // 0..8
                int targetSlot = chain.data.targetSlot;    // 0..8
                if (targetSlot < 0 || targetSlot > 8) continue;

                int slot1to9 = targetSlot + 1;
                s.selectedAbilitySlot = slot1to9;

                world.execute(() -> {
                    Player playerComponent = store.getComponent(entityRef, Player.getComponentType());
                    if (playerComponent == null) return;

                    // Force server-side slot back
                    playerComponent.getInventory().setActiveHotbarSlot((byte) originalSlot);

                    // Force client-side slot back (fix desync)
                    SetActiveSlot setActiveSlotPacket = new SetActiveSlot(
                            Inventory.HOTBAR_SECTION_ID,
                            originalSlot
                    );
                    playerRef.getPacketHandler().write(setActiveSlotPacket);

                    // Trigger ability logic
                    abilitySystem.useSlot(playerRef, slot1to9);

                    // Proof message
                    playerRef.sendMessage(Message.raw(
                            "[Ability] Pressed " + slot1to9 + " -> itemId=" + s.hotbarItemIds[slot1to9 - 1]
                    ));
                });

                // Block vanilla hotbar switching
                return true;
            }
        }

        // If we didn’t explicitly block anything, allow packet through
        return false;
    }
}
