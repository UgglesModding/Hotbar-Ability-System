package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class AbilityDebugCommand extends AbstractPlayerCommand {

    private final AbilityHotbarState state;

    public AbilityDebugCommand(AbilityHotbarState state) {
        super("abilitydebug", "Prints current ability bar slots 1-9.");
        this.state = state;
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

            StringBuilder sb = new StringBuilder();
            sb.append("Bar=").append(s.currentAbilityBarId).append(" | ");
            sb.append("Selected=").append(s.selectedAbilitySlot).append("\n");

            for (int i = 0; i < 9; i++) {
                sb.append(i + 1)
                        .append(": ")
                        .append(s.hotbarItemIds[i])
                        .append("\n");
            }

            ctx.sendMessage(Message.raw(sb.toString()));
        });
    }
}
