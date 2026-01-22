package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class AbilityContext {
    public final PlayerRef playerRef;
    public final Store<EntityStore> store;
    public final Ref<EntityStore> ref;
    public final World world;
    public final Player player;

    private AbilityContext(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref, World world, Player player) {
        this.playerRef = playerRef;
        this.store = store;
        this.ref = ref;
        this.world = world;
        this.player = player;
    }

    public static AbilityContext from(PlayerRef playerRef, Store<EntityStore> store, Ref<EntityStore> ref, World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        return new AbilityContext(playerRef, store, ref, world, player);
    }
}
