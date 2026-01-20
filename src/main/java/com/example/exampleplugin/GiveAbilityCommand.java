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
            this.withRequiredArg("id", "Ability ID or Item ID to give", ArgTypes.STRING);

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
        String input = idArg.get(ctx);

        world.execute(() -> {
            // Get the Player component (note: NOT entity.entities.player.Player)
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                ctx.sendMessage(Message.raw("[GiveAbility] Player component missing."));
                return;
            }

            // If input is an Ability ID, swap to its ItemAsset; else treat as item id directly
            String itemId = input;

            AbilityData ability = registry.getAbility(input);
            if (ability != null) {
                // ItemAsset is actually the in-game item id (e.g. Ability_DaggerLeap)
                if (ability.ItemAsset != null && !ability.ItemAsset.isBlank()) {
                    itemId = ability.ItemAsset;
                } else {
                    ctx.sendMessage(Message.raw("[GiveAbility] Ability exists but ItemAsset (item id) is missing for: " + input));
                    return;
                }
            }


            // Create stack and place into hotbar slot 0 (key "1")
            ItemContainer hotbar = player.getInventory().getHotbar();
            ItemStack stack = new ItemStack(itemId, 1);

            // Slot index uses short in this API
            hotbar.setItemStackForSlot((short) 0, stack);

            ctx.sendMessage(Message.raw("[GiveAbility] Gave: " + itemId + " (from input: " + input + ")"));
        });
    }
}
