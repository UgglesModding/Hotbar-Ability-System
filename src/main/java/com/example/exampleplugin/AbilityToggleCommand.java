package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class AbilityToggleCommand extends AbstractPlayerCommand {

    private final AbilityHotbarState state;
    private final AbilitySystem abilitySystem;

    // Safe empty HUD (never pass null)
    private static final class EmptyHud extends CustomUIHud {
        public EmptyHud(@Nonnull PlayerRef playerRef) { super(playerRef); }
        @Override protected void build(@Nonnull UICommandBuilder ui) { }
    }

    public AbilityToggleCommand(AbilityHotbarState state, AbilitySystem abilitySystem) {
        super("abilitybar", "Toggles the ability bar overlay.");
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
            var hudManager = player.getHudManager();

            if (s.enabled) {
                // TEMP: ensure the test bar is loaded when turning on
                abilitySystem.loadNewAbilityBar(playerRef, null);

                hudManager.setCustomHud(
                        playerRef,
                        new AbilityHotbarHud(playerRef, abilitySystem.getRegistry(), state)
                );

                ctx.sendMessage(Message.raw("Ability Bar: ON"));
            } else {
                hudManager.setCustomHud(playerRef, new EmptyHud(playerRef));
                ctx.sendMessage(Message.raw("Ability Bar: OFF"));
            }
        });
    }
}
