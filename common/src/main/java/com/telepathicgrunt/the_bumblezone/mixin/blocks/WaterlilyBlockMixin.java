package com.telepathicgrunt.the_bumblezone.mixin.blocks;


import com.telepathicgrunt.the_bumblezone.modinit.BzFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WaterlilyBlock.class)
public class WaterlilyBlockMixin {

    @Inject(method = "mayPlaceOn(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(value = "RETURN"), cancellable = true, require = 0)
    private void bumblezone$allowPlacingOnSugarWater(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CallbackInfoReturnable<Boolean> cir) {
        if(!cir.getReturnValue()) {
            FluidState fluidState = blockGetter.getFluidState(blockPos);
            FluidState fluidState2 = blockGetter.getFluidState(blockPos.above());
            if (fluidState.getType() == BzFluids.SUGAR_WATER_FLUID.get() && fluidState2.getType() == Fluids.EMPTY) {
                cir.setReturnValue(true);
            }
        }
    }
}
