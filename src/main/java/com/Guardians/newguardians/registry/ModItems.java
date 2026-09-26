package com.Guardians.newguardians.registry;

import com.Guardians.newguardians.NewGuardiansMod;
import com.Guardians.newguardians.item.GuardCageItem;
import com.Guardians.newguardians.item.GuardSpawnItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(NewGuardiansMod.MODID);

    public static final DeferredItem<GuardSpawnItem> GUARD_SPAWN_ITEM =
            ITEMS.registerItem("guard_spawn_item", GuardSpawnItem::new, new Item.Properties().stacksTo(16));

    // Vazia (textures/items/cage.png): clicar num guard vivo apanha-o.
    public static final DeferredItem<GuardCageItem> GUARD_CAGE =
            ITEMS.registerItem("guard_cage",
                    properties -> new GuardCageItem(properties, false),
                    new Item.Properties().stacksTo(16));

    // Cheia / "soul cage" (textures/items/soul_cage.png): clicar num bloco reinvoca o guard.
    public static final DeferredItem<GuardCageItem> GUARD_CAGE_FILLED =
            ITEMS.registerItem("guard_cage_filled",
                    properties -> new GuardCageItem(properties, true),
                    new Item.Properties().stacksTo(1));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}