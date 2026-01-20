package com.example.exampleplugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class GiveAbilityCommand extends AbstractPlayerCommand {

    private final AbilityRegistry registry;

    @Nonnull
    private final RequiredArg<String> idArg =
            this.withRequiredArg("id", "Ability ID (uggles_combat:daggerleap) OR Item ID (Ability_DaggerLeap)", ArgTypes.STRING);

    public GiveAbilityCommand(AbilityRegistry registry) {
        super("giveability", "Gives an ability/item into your normal hotbar (slot 1).");
        this.registry = registry;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        final String input = idArg.get(ctx);

        world.execute(() -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                ctx.sendMessage(Message.raw("[GiveAbility] Player component missing."));
                return;
            }

            // Resolve: ability-id -> item-id, otherwise treat as item-id
            String itemId = AbilityItemResolver.resolveItemId(registry, input);
            if (itemId == null || itemId.isBlank()) {
                itemId = AbilityRegistry.EMPTY_ITEM_ID;
            }

            ItemContainer hotbar = player.getInventory().getHotbar();
            ItemStack stack = new ItemStack(itemId, 1);

            // Slot 0 = key "1"
            hotbar.setItemStackForSlot((short) 0, stack);

            ctx.sendMessage(Message.raw("[GiveAbility] Input=" + input + " -> ItemId=" + itemId + " (placed in slot 1)"));
        });
    }
}
