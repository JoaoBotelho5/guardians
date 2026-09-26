package com.Guardians.newguardians.client;

import com.Guardians.newguardians.NewGuardiansMod;
import com.Guardians.newguardians.client.renderer.GuardRenderer;
import com.Guardians.newguardians.client.screen.GuardEquipmentScreen;
import com.Guardians.newguardians.registry.ModEntities;
import com.Guardians.newguardians.registry.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class NewGuardiansModClient {

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GUARD.get(), GuardRenderer::new);
    }

    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.GUARD_EQUIPMENT.get(), GuardEquipmentScreen::new);
    }

    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(NewGuardiansMod.MODID, "guard_cage_cooldown"),
                new GuardCageCooldownOverlay());
    }
}