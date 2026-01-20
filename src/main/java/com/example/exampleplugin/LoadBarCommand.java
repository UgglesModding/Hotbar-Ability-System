package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class LoadBarCommand extends AbstractPlayerCommand {

    private final AbilityHotbarState state;
    private final AbilitySystem abilitySystem;

    private final RequiredArg<String> barArg =
            this.withRequiredArg("bar", "Ability bar id or short name", ArgTypes.STRING);

    // Safe empty HUD (never pass null)
    private static final class EmptyHud extends CustomUIHud {
        public EmptyHud(@Nonnull PlayerRef playerRef) { super(playerRef); }
        @Override protected void build(@Nonnull UICommandBuilder ui) { }
    }

    public LoadBarCommand(AbilityHotbarState state, AbilitySystem abilitySystem) {
        super("loadbar", "Loads an ability bar: /loadbar <barIdOrName>");
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
        final String raw = barArg.get(ctx);

        world.execute(() -> {
            String barId = normalizeBarId(raw);

            // Load bar into server state
            abilitySystem.loadNewAbilityBar(playerRef, barId);

            // Refresh HUD if currently enabled
            var s = state.get(playerRef.getUsername());
            Player player = store.getComponent(ref, Player.getComponentType());
            var hudManager = player.getHudManager();

            if (s.enabled) {
                hudManager.setCustomHud(
                        playerRef,
                        new AbilityHotbarHud(playerRef, abilitySystem.getRegistry(), state)
                );
            } else {
                hudManager.setCustomHud(playerRef, new EmptyHud(playerRef));
            }

            ctx.sendMessage(Message.raw("Loaded ability bar: " + barId));
        });
    }

    private String normalizeBarId(String input) {
        String trimmed = input.trim();

        // Full namespaced ID provided
        if (trimmed.contains(":")) {
            return trimmed;
        }

        // Short name -> uggles_combat:bar/<name>
        return "uggles_combat:bar/" + trimmed;
    }
}
