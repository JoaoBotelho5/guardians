package com.Guardians.newguardians.registry;

import com.Guardians.newguardians.NewGuardiansMod;
import com.Guardians.newguardians.entity.Guard;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE, NewGuardiansMod.MODID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>, EntityType<Guard>> GUARD =
            ENTITY_TYPES.register("guard",
                    () -> EntityType.Builder.of(Guard::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .build("guard"));

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
