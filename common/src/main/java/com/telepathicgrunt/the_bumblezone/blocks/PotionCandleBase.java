package com.telepathicgrunt.the_bumblezone.blocks;

import com.telepathicgrunt.the_bumblezone.blocks.blockentities.PotionCandleBlockEntity;
import com.telepathicgrunt.the_bumblezone.events.player.PlayerCraftedItemEvent;
import com.telepathicgrunt.the_bumblezone.modinit.BzBlockEntities;
import com.telepathicgrunt.the_bumblezone.modinit.BzBlocks;
import com.telepathicgrunt.the_bumblezone.modinit.BzCriterias;
import com.telepathicgrunt.the_bumblezone.modinit.BzItems;
import com.telepathicgrunt.the_bumblezone.modinit.BzTags;
import com.telepathicgrunt.the_bumblezone.platform.BlockExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;


public class PotionCandleBase extends BaseEntityBlock implements SimpleWaterloggedBlock, SuperCandle, BlockExtension {
    public static final BooleanProperty LIT = AbstractCandleBlock.LIT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape AABB = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);

    public PotionCandleBase() {
        super(Properties.of()
                .mapColor(MapColor.SAND)
                .lightLevel((blockState) -> blockState.getValue(LIT) ? 15 : 0)
                .noOcclusion()
                .strength(0.1F)
                .sound(SoundType.CANDLE)
                .pushReaction(PushReaction.DESTROY));

        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, Boolean.FALSE).setValue(WATERLOGGED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, WATERLOGGED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AABB;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        SuperCandle.placeWickIfPossible(level, pos, false);
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        boolean flag = fluidstate.is(FluidTags.WATER) && fluidstate.isSource();
        return super.getStateForPlacement(context).setValue(WATERLOGGED, flag);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    public void neighborChanged(BlockState blockstate, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean notify) {
        SuperCandle.placeWickIfPossible(world, pos, false);
        super.neighborChanged(blockstate, world, pos, block, fromPos, notify);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public boolean canPlaceLiquid(BlockGetter world, BlockPos blockPos, BlockState blockState, Fluid fluid) {
        return !blockState.getValue(WATERLOGGED) && fluid.is(FluidTags.WATER) && fluid.defaultFluidState().isSource();
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!state.getValue(WATERLOGGED) && fluidState.is(FluidTags.WATER) && fluidState.isSource()) {
            BlockState blockstate = state.setValue(WATERLOGGED, Boolean.TRUE);
            if (state.getValue(LIT)) {
                SuperCandleWick.extinguish(null, level.getBlockState(pos.above()), level, pos.above());
                level.setBlock(pos, blockstate.setValue(LIT, false), 3);
            }
            else {
                level.setBlock(pos, blockstate, 3);
            }

            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide() && oldState.is(BzBlocks.POTION_BASE_CANDLE.get()) && state.is(BzBlocks.POTION_BASE_CANDLE.get())) {
            if (oldState.getValue(LIT) && !state.getValue(LIT)) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof PotionCandleBlockEntity potionCandleBlockEntity) {
                    potionCandleBlockEntity.resetCurrentDuration();
                    potionCandleBlockEntity.resetInstantStartTime();
                }
            }
            else if (!oldState.getValue(LIT) && state.getValue(LIT)) {
                resetTimingFields(level, pos);
            }
        }
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public InteractionResult use(BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        if (player.getAbilities().mayBuild) {
            if (CandleLightBehaviors(blockState, level, blockPos, player, interactionHand)) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (projectile.isOnFire() && SuperCandle.canBeLit(level, state, hit.getBlockPos())) {
            boolean litWick = SuperCandleWick.setLit(level, level.getBlockState(hit.getBlockPos().above()), hit.getBlockPos().above(), true);
            if (litWick && projectile.getOwner() instanceof ServerPlayer serverPlayer) {
                BlockEntity blockEntity = level.getBlockEntity(hit.getBlockPos());
                if (blockEntity instanceof PotionCandleBlockEntity potionCandleBlockEntity &&
                    potionCandleBlockEntity.getMobEffect() != null &&
                    potionCandleBlockEntity.getMobEffect().isInstantenous() &&
                    !potionCandleBlockEntity.getMobEffect().isBeneficial())
                {
                    BzCriterias.PROJECTILE_LIGHT_INSTANT_POTION_CANDLE_TRIGGER.trigger(serverPlayer);
                }
            }
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player != null && player.getAbilities().instabuild) {
            super.playerWillDestroy(level, pos, state, player);
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PotionCandleBlockEntity potionCandleBlockEntity) {
            ItemStack itemStack = BzItems.POTION_CANDLE.get().getDefaultInstance();
            potionCandleBlockEntity.saveToItem(itemStack);
            ItemEntity itementity = new ItemEntity(level, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, itemStack);
            itementity.setDefaultPickUpDelay();
            level.addFreshEntity(itementity);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BzBlockEntities.POTION_CANDLE.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BzBlockEntities.POTION_CANDLE.get(), level.isClientSide ? (a, b, c, d) -> {} : PotionCandleBlockEntity::serverTick);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos pos) {
        return blockState.is(BzTags.CANDLES) && blockState.getValue(LIT) ? 5 : 0;
    }

    private void resetTimingFields(Level level, BlockPos blockPos) {
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (blockEntity instanceof PotionCandleBlockEntity potionCandleBlockEntity) {
            potionCandleBlockEntity.resetCurrentDuration();
            potionCandleBlockEntity.setInstantStartTime(Math.max(level.getGameTime() - 1L, 0L));
        }
    }

    public static int getItemColor(ItemStack itemStack) {
        if (itemStack.hasTag()) {
            CompoundTag tag = itemStack.getTag();
            if (tag != null && tag.contains("BlockEntityTag")) {
                CompoundTag blockEntityTag = tag.getCompound("BlockEntityTag");
                if (blockEntityTag.contains(PotionCandleBlockEntity.COLOR_TAG)) {
                    return blockEntityTag.getInt(PotionCandleBlockEntity.COLOR_TAG);
                }
            }
        }
        return PotionCandleBlockEntity.DEFAULT_COLOR;
    }

    public static int getBlockColor(BlockAndTintGetter world, BlockPos pos, int tintIndex) {
        if (world != null) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof PotionCandleBlockEntity potionCandleBlockEntity) {
                int currentColor = potionCandleBlockEntity.getColor();

                if (tintIndex == 1) {
                    // Change tint of lit top
                    int red = Math.max((currentColor >> 16 & 255), 40);
                    int green = Math.max((currentColor >> 8 & 255), 10);
                    int blue = Math.max((currentColor & 255), 5);
                    currentColor = (Math.min(red + 60, 255) << 16) + (Math.min(green + 30, 255) << 8) + Math.min(blue + 25, 255);
                }
                return currentColor;
            }
        }
        return tintIndex;
    }

    @Override
    public void animateTick(BlockState blockState, Level world, BlockPos position, RandomSource random) {
        if (blockState.hasProperty(LIT) && blockState.getValue(LIT)) {
            BlockEntity blockEntity = world.getBlockEntity(position);
            if (blockEntity instanceof PotionCandleBlockEntity potionCandleBlockEntity && potionCandleBlockEntity.getMobEffect() != null) {
                int color = potionCandleBlockEntity.getColor();
                Vec3 colorRGB = PotionCandleBlockEntity.convertIntegerColorToRGB(color);

                //number of particles in this tick
                for (int i = 0; i < random.nextInt(3); ++i) {
                    this.spawnAmbientEffectParticles(world, random, position, colorRGB.x(), colorRGB.y(), colorRGB.z());
                }
            }
        }
        super.animateTick(blockState, world, position, random);
    }


    private void spawnAmbientEffectParticles(Level world, RandomSource random, BlockPos position, double red, double green, double blue) {
        world.addParticle(ParticleTypes.AMBIENT_ENTITY_EFFECT,
                position.getX() + 0.4d + (random.nextDouble() * 0.2d),
                position.getY() + 0.7d + (random.nextDouble() * 0.2d),
                position.getZ() + 0.4d + (random.nextDouble() * 0.2d),
                red,
                green,
                blue);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return !(state.hasProperty(LIT) && state.getValue(LIT));
    }

    @Override
    public BlockPathTypes bz$getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, Mob mob) {
        if (state.hasProperty(LIT) && state.getValue(LIT)) {
            return BlockPathTypes.DAMAGE_FIRE;
        }
        return null;
    }

    public static void multiPotionCandleCrafted(PlayerCraftedItemEvent event) {
        if (event.player() instanceof ServerPlayer serverPlayer && event.item().is(BzItems.POTION_CANDLE.get())) {
            int containerSize = event.table().getContainerSize();
            int potionsUsed = 0;
            for (int i = 0; i < containerSize; i++) {
                ItemStack consumedItems = event.table().getItem(i);
                if (consumedItems.is(Items.POTION) ||
                    consumedItems.is(Items.SPLASH_POTION) ||
                    consumedItems.is(Items.LINGERING_POTION))
                {
                    potionsUsed++;
                }
            }

            if (potionsUsed >= 2) {
                BzCriterias.CRAFT_MULTI_POTION_POTION_CANDLE_TRIGGER.trigger(serverPlayer);
            }
        }
    }
}
