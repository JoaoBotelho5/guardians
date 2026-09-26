package com.Guardians.newguardians;

import com.Guardians.newguardians.client.NewGuardiansModClient;
import com.Guardians.newguardians.client.GuardCageCooldownOverlay;
import com.Guardians.newguardians.event.GuardCombatHandler;
import com.Guardians.newguardians.registry.ModAttachments;
import com.Guardians.newguardians.registry.ModCreativeTabs;
import com.Guardians.newguardians.registry.ModEntities;
import com.Guardians.newguardians.registry.ModItems;
import com.Guardians.newguardians.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(NewGuardiansMod.MODID)
public class NewGuardiansMod {

    public static final String MODID = "newguardians";

    public NewGuardiansMod(IEventBus modEventBus) {
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        ModAttachments.register(modEventBus);
        ModMenus.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        modEventBus.addListener(this::onEntityAttributeCreation);
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(new GuardCombatHandler());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(NewGuardiansModClient::onRegisterRenderers);
            modEventBus.addListener(NewGuardiansModClient::onRegisterMenuScreens);
            modEventBus.addListener(NewGuardiansModClient::onRegisterGuiLayers);
            NeoForge.EVENT_BUS.register(new GuardCageCooldownOverlay());
        }
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GUARD.get(), com.Guardians.newguardians.entity.Guard.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }
}