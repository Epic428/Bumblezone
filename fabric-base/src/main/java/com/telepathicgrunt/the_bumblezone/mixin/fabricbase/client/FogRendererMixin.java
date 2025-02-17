package com.telepathicgrunt.the_bumblezone.mixin.fabricbase.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.telepathicgrunt.the_bumblezone.Bumblezone;
import com.telepathicgrunt.the_bumblezone.client.dimension.BzDimensionSpecialEffects;
import com.telepathicgrunt.the_bumblezone.client.utils.GeneralUtilsClient;
import com.telepathicgrunt.the_bumblezone.fluids.base.BzFlowingFluid;
import com.telepathicgrunt.the_bumblezone.fluids.base.ClientFluidProperties;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Optional;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V", ordinal = 1))
    private static void bumblezone$onModifyFogColor(float red, float green, float blue, float alpha, Operation<Void> original, Camera camera, float partialTicks, ClientLevel level, int renderDistance, float bossColorModifier) {
        FluidState state = level.getFluidState(camera.getBlockPosition());
        if (camera.getPosition().y < (double)((float)camera.getBlockPosition().getY() + state.getHeight(level, camera.getBlockPosition()))) {
            if (state.getType() instanceof BzFlowingFluid bzFluid) {
                ClientFluidProperties properties = ClientFluidProperties.get(bzFluid.info().properties().id());
                if (properties != null) {
                    Optional<Vector3f> fogColor = properties.modifyFogColor(camera, partialTicks, level, renderDistance, bossColorModifier, new Vector3f(red, green, blue));
                    if (fogColor.isPresent()) {
                        Vector3f color = fogColor.get();
                        original.call(color.x, color.y, color.z, alpha);
                        return;
                    }
                }
            }
        }
        original.call(red, green, blue, alpha);
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void bumblezone$onRenderFog(Camera camera, FogRenderer.FogMode fogMode, float renderDistance, boolean bl, float partialTicks, CallbackInfo ci) {
        FluidState state = camera.getEntity().level().getFluidState(camera.getBlockPosition());
        if (state.getType() instanceof BzFlowingFluid bzFluid) {
            ClientFluidProperties properties = ClientFluidProperties.get(bzFluid.info().properties().id());
            if (properties != null) {
                float start = RenderSystem.getShaderFogStart();
                float end = RenderSystem.getShaderFogEnd();
                FogShape shape = RenderSystem.getShaderFogShape();
                properties.modifyFog(camera, fogMode, renderDistance, partialTicks, start, end, shape);
            }
        }
    }

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogStart(F)V", ordinal = 0),
            require = 0, locals = LocalCapture.CAPTURE_FAILSOFT)
    private static void bumblezone$reduceFogThickness(Camera camera,
                                                      FogRenderer.FogMode mode,
                                                      float fogEnd,
                                                      boolean thickFog,
                                                      float p_,
                                                      CallbackInfo ci,
                                                      FogType fogType,
                                                      Entity entity,
                                                      FogRenderer.FogData fogData)
    {
        if (mode == FogRenderer.FogMode.FOG_TERRAIN &&
                fogType == FogType.NONE &&
                thickFog &&
                GeneralUtilsClient.getClientPlayer() != null &&
                GeneralUtilsClient.getClientPlayer().level().dimension().location().equals(Bumblezone.MOD_DIMENSION_ID))
        {
            BzDimensionSpecialEffects.fogThicknessAdjustments(fogEnd, fogData);
        }
    }
}