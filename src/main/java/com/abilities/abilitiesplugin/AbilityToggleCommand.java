package com.abilities.abilitiesplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class AbilityToggleCommand extends AbstractPlayerCommand {

    private final AbilityHotbarState state;
    private final AbilitySystem abilitySystem;

    public AbilityToggleCommand(
            AbilityHotbarState state,
            AbilitySystem abilitySystem
    ) {
        super("abilitybar", "Toggle ability bar");
        this.state = state;
        this.abilitySystem = abilitySystem;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        world.execute(() -> {
            var s = state.get(playerRef.getUsername());
            s.enabled = !s.enabled;

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            var hudManager = player.getHudManager();

            if (s.enabled) {
                abilitySystem.refreshFromHeldWeapon(playerRef, store, ref);

                hudManager.setCustomHud(
                        playerRef,
                        new AbilityHotbarHud(playerRef, state)
                );

                ctx.sendMessage(Message.raw("Ability Bar: ON"));
            } else {
                AbilityBarUtil.forceOff(state, player, playerRef);
                ctx.sendMessage(Message.raw("Ability Bar: OFF"));
            }
        });
    }
}
