package com.Guardians.newguardians.registry;

import com.Guardians.newguardians.NewGuardiansMod;
import com.Guardians.newguardians.menu.GuardEquipmentMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, NewGuardiansMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<GuardEquipmentMenu>> GUARD_EQUIPMENT =
            MENUS.register("guard_equipment", () -> IMenuTypeExtension.create(GuardEquipmentMenu::new));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}