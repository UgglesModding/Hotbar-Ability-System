package com.abilities.abilitiesplugin;

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

    private final AbilityHotbarState state;
    private final AbilitySystem abilitySystem;

    public AbilityHotbarPacketFilter(
            AbilityHotbarState state,
            AbilitySystem abilitySystem
    ) {
        this.state = state;
        this.abilitySystem = abilitySystem;
    }

    private static final class EmptyHud extends CustomUIHud {
        public EmptyHud(@Nonnull PlayerRef ref) { super(ref); }
        @Override protected void build(@Nonnull UICommandBuilder ui) {}
    }

    @Override
    public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {

        if (!(packet instanceof SyncInteractionChains chains)) return false;

        var s = state.get(playerRef.getUsername());
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return false;

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        for (SyncInteractionChain chain : chains.updates) {
            if (!chain.initial) continue;

            if (chain.interactionType.name().equalsIgnoreCase("Ability1")) {
                world.execute(() -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player == null) return;

                    // Always refresh first
                    abilitySystem.refreshFromHeldWeapon(playerRef, store, ref);

                    var s2 = state.get(playerRef.getUsername());

                    // Check if this weapon actually has anything usable
                    boolean hasAnything =
                            (s2.hotbarAbilityIds[0] != null && !s2.hotbarAbilityIds[0].isBlank()) ||
                                    (s2.hotbarRootInteractions[0] != null && !s2.hotbarRootInteractions[0].isBlank());

                    if (!hasAnything) {
                        //playerRef.sendMessage(Message.raw("[AbilityBar] No abilities for held weapon."));
                        // force it off
                        s2.enabled = false;
                        player.getHudManager().setCustomHud(playerRef, new EmptyHud(playerRef));
                        return;
                    }

                    // Now toggle ON/OFF
                    s2.enabled = !s2.enabled;

                    if (s2.enabled) {
                        player.getHudManager().setCustomHud(playerRef, new AbilityHotbarHud(playerRef, state));
                        //playerRef.sendMessage(Message.raw("[AbilityBar] ON"));
                    } else {
                        player.getHudManager().setCustomHud(playerRef, new EmptyHud(playerRef));
                        //playerRef.sendMessage(Message.raw("[AbilityBar] OFF"));
                    }
                });

                return true;
            }

            // ---- Block hotbar & activate abilities ----
            if (!s.enabled) continue;

            if (chain.interactionType == InteractionType.SwapFrom && chain.data != null) {
                int original = chain.activeHotbarSlot;
                int target = chain.data.targetSlot;
                if (target < 0 || target > 8) continue;

                int slot = target + 1;

                world.execute(() -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player == null) return;

                    player.getInventory().setActiveHotbarSlot((byte) original);
                    playerRef.getPacketHandler().write(
                            new SetActiveSlot(Inventory.HOTBAR_SECTION_ID, original)
                    );

                    abilitySystem.useSlot(playerRef, store, ref, world, slot);
                });

                return true;
            }
        }

        return false;
    }
}
