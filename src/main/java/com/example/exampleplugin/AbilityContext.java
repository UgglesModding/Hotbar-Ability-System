package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class AbilityContext {

    public final PlayerRef playerRef;
    public final Player player;
    public final Store<EntityStore> store;
    public final Ref<EntityStore> entityRef;
    public final World world;

    private AbilityContext(
            PlayerRef playerRef,
            Player player,
            Store<EntityStore> store,
            Ref<EntityStore> entityRef,
            World world
    ) {
        this.playerRef = playerRef;
        this.player = player;
        this.store = store;
        this.entityRef = entityRef;
        this.world = world;
    }

    public static AbilityContext from(
            PlayerRef playerRef,
            Store<EntityStore> store,
            Ref<EntityStore> entityRef,
            World world
    ) {
        Player player = store.getComponent(entityRef, Player.getComponentType());
        return new AbilityContext(playerRef, player, store, entityRef, world);
    }
}
