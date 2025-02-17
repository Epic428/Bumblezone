package com.telepathicgrunt.the_bumblezone.mixin.entities;

import com.telepathicgrunt.the_bumblezone.blocks.HeavyAir;
import com.telepathicgrunt.the_bumblezone.entities.TemporaryPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTimeInAirMixin extends LivingEntity implements TemporaryPlayerData {

    @Shadow public abstract boolean isCreative();

    @Shadow public abstract boolean isSpectator();

    @Unique
    private int bumblezone$ticksOffGroundInHeavyAir = 0;

    protected ServerPlayerTimeInAirMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "tick()V", at = @At(value = "HEAD"))
    private void bumblezone$recordTicksOffGround(CallbackInfo ci) {
        if (this.onGround() || (this.getControlledVehicle() != null && this.getControlledVehicle().onGround())) {
            this.bumblezone$ticksOffGroundInHeavyAir = 0;
        }
        else if (!this.isCreative() && !this.isSpectator()) {
            if (HeavyAir.isInHeavyAir(this.level(), this.getBoundingBox())) {
                this.bumblezone$ticksOffGroundInHeavyAir++;
            }
        }
    }

    @Override
    public int playTickOffGroundInHeavyAir() { return bumblezone$ticksOffGroundInHeavyAir; }
}