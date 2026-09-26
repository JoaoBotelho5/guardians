package com.Guardians.newguardians.client.renderer;

import com.Guardians.newguardians.NewGuardiansMod;
import com.Guardians.newguardians.entity.Guard;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class GuardRenderer extends HumanoidMobRenderer<Guard, PlayerModel<Guard>> {

    private static final ResourceLocation SKIN =
            ResourceLocation.fromNamespaceAndPath(NewGuardiansMod.MODID, "textures/entity/guard.png");

    public GuardRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);

        // HumanoidMobRenderer does NOT add an armor layer by itself (ZombieRenderer etc. add their own).
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));

        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(Guard entity) {
        return SKIN;
    }
}