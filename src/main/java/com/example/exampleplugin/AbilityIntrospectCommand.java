package com.example.exampleplugin;

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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AbilityIntrospectCommand extends AbstractPlayerCommand {

    public AbilityIntrospectCommand() {
        super("abilityintrospect", "Lists methods containing 'Interaction'/'Root' on World/PlayerRef/Player.");
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
            Player player = store.getComponent(ref, Player.getComponentType());

            ctx.sendMessage(Message.raw("--- World methods ---"));
            dump(ctx, world);

            ctx.sendMessage(Message.raw("--- PlayerRef methods ---"));
            dump(ctx, playerRef);

            ctx.sendMessage(Message.raw("--- Player methods ---"));
            dump(ctx, player);

            ctx.sendMessage(Message.raw("--- ExternalData methods ---"));
            dump(ctx, store.getExternalData());

        });
    }

    private static void dump(CommandContext ctx, Object obj) {
        if (obj == null) {
            ctx.sendMessage(Message.raw("(null)"));
            return;
        }

        List<String> hits = new ArrayList<>();
        for (Method m : obj.getClass().getMethods()) {
            String n = m.getName();
            if (n.toLowerCase().contains("interaction") || n.toLowerCase().contains("root") || n.toLowerCase().contains("chain")) {
                hits.add(n + "(" + m.getParameterCount() + ")");
            }
        }

        if (hits.isEmpty()) {
            ctx.sendMessage(Message.raw("(no matches)"));
            return;
        }

        // keep it readable
        int max = Math.min(40, hits.size());
        for (int i = 0; i < max; i++) {
            ctx.sendMessage(Message.raw(hits.get(i)));
        }
        if (hits.size() > max) ctx.sendMessage(Message.raw("... +" + (hits.size() - max) + " more"));
    }
}
