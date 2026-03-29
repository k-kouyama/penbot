package com.example.penbot.entity.client;

import com.example.penbot.PenbotMod;
import com.example.penbot.entity.PenbotEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PenbotRenderer extends MobRenderer<PenbotEntity, HumanoidModel<PenbotEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PenbotMod.MOD_ID, "textures/entity/penbot.png");

    public PenbotRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(PenbotEntity entity) {
        return TEXTURE;
    }
}
