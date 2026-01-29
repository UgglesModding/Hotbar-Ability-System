package com.abilities.abilitiesplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class AbilityContext {

    public final PlayerRef PlayerRef;
    public final Player Player;
    public final Store<EntityStore> Store;
    public final Ref<EntityStore> EntityRef;
    public final World World;

    // NEW: runtime player multiplier + runtime value
    public final float PowerMultiplier;
    public final int AbilityValue;

    private AbilityContext(
            PlayerRef PlayerRef,
            Player Player,
            Store<EntityStore> Store,
            Ref<EntityStore> EntityRef,
            World World,
            float PowerMultiplier,
            int AbilityValue
    ) {
        this.PlayerRef = PlayerRef;
        this.Player = Player;
        this.Store = Store;
        this.EntityRef = EntityRef;
        this.World = World;
        this.PowerMultiplier = PowerMultiplier;
        this.AbilityValue = AbilityValue;
    }

    public static AbilityContext from(
            PlayerRef playerRef,
            Store<EntityStore> store,
            Ref<EntityStore> entityRef,
            World world,
            AbilityHotbarState hotbarState,
            int abilityValue
    ) {
        com.hypixel.hytale.server.core.entity.entities.Player player =
                store.getComponent(entityRef,
                        com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());

        // read per-player multiplier from hotbar state
        var s = hotbarState.get(playerRef.getUsername());
        float pm = (s == null) ? 1.0f : s.PlayerPowerMultiplier;
        if (pm <= 0.0f) pm = 1.0f;

        return new AbilityContext(playerRef, player, store, entityRef, world, pm, abilityValue);
    }
}
