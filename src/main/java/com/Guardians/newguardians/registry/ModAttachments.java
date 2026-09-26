package com.Guardians.newguardians.registry;

import com.Guardians.newguardians.NewGuardiansMod;
import com.Guardians.newguardians.entity.GuardProgressionData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NewGuardiansMod.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GuardProgressionData>> GUARD_PROGRESSION =
            ATTACHMENT_TYPES.register("guard_progression",
                    () -> AttachmentType.builder(() -> GuardProgressionData.DEFAULT)
                            .serialize(GuardProgressionData.CODEC)
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
