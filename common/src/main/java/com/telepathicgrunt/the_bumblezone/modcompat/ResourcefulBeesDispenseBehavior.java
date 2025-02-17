package com.telepathicgrunt.the_bumblezone.modcompat;

import com.telepathicgrunt.the_bumblezone.blocks.EmptyHoneycombBrood;
import com.telepathicgrunt.the_bumblezone.blocks.HoneycombBrood;
import com.telepathicgrunt.the_bumblezone.mixin.blocks.DefaultDispenseItemBehaviorInvoker;
import com.telepathicgrunt.the_bumblezone.modinit.BzBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;


public class ResourcefulBeesDispenseBehavior extends DefaultDispenseItemBehavior {
    public static DispenseItemBehavior DEFAULT_BOTTLED_BEE_DISPENSE_BEHAVIOR;
    public static DefaultDispenseItemBehavior DROP_ITEM_BEHAVIOR = new DefaultDispenseItemBehavior();

    /**
     * Dispense the specified stack, play the dispenser sound and spawn particles.
     */
    public ItemStack execute(BlockSource source, ItemStack stack) {
        ServerLevel world = source.getLevel();
        Position dispensePosition = DispenserBlock.getDispensePosition(source);
        BlockPos dispenseBlockPos = BlockPos.containing(dispensePosition);
        BlockState blockstate = world.getBlockState(dispenseBlockPos);

        if (blockstate.getBlock() == BzBlocks.EMPTY_HONEYCOMB_BROOD.get() && ResourcefulBeesCompat.isFilledBeeJarItem(stack)) {
            world.setBlockAndUpdate(dispenseBlockPos, BzBlocks.HONEYCOMB_BROOD.get().defaultBlockState()
                .setValue(HoneycombBrood.FACING, blockstate.getValue(EmptyHoneycombBrood.FACING))
                .setValue(HoneycombBrood.STAGE, ResourcefulBeesCompat.isFilledBabyBeeJarItem(stack) ? 2 : 3));

            stack.getOrCreateTag().remove("Entity");
            return stack;
        }
        else {
            return ((DefaultDispenseItemBehaviorInvoker) DEFAULT_BOTTLED_BEE_DISPENSE_BEHAVIOR).invokeExecute(source, stack);
        }
    }

    /**
     * Play the dispenser sound from the specified block.
     */
    protected void playSound(BlockSource source) {
        source.getLevel().levelEvent(1002, source.getPos(), 0);
    }
}
