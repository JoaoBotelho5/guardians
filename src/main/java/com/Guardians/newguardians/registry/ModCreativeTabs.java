package com.Guardians.newguardians.registry;

import com.Guardians.newguardians.NewGuardiansMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, NewGuardiansMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> NEW_GUARDIANS_TAB =
            TABS.register("new_guardians", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.newguardians"))
                    .icon(() -> ModItems.GUARD_SPAWN_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.GUARD_SPAWN_ITEM.get());
                        output.accept(ModItems.GUARD_CAGE.get());
                        output.accept(ModItems.GUARD_CAGE_FILLED.get());
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}