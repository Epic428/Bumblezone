package com.telepathicgrunt.the_bumblezone.items;

import com.telepathicgrunt.the_bumblezone.Bumblezone;
import com.telepathicgrunt.the_bumblezone.client.utils.GeneralUtilsClient;
import com.telepathicgrunt.the_bumblezone.modinit.BzCriterias;
import com.telepathicgrunt.the_bumblezone.modinit.BzItems;
import com.telepathicgrunt.the_bumblezone.modinit.BzSounds;
import com.telepathicgrunt.the_bumblezone.modinit.BzTags;
import com.telepathicgrunt.the_bumblezone.utils.ThreadExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Vanishable;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class HoneyCompass extends Item implements Vanishable {
    public static final String TAG_TARGET_POS = "TargetPos";
    public static final String TAG_TARGET_DIMENSION = "TargetDimension";
    public static final String TAG_TYPE = "CompassType";
    public static final String TAG_LOADING = "IsLoading";
    public static final String TAG_FAILED = "IsFailed";
    public static final String TAG_STRUCTURE_TAG = "TargetStructureTag";
    public static final String TAG_TARGET_BLOCK = "TargetBlock";
    public static final String TAG_CUSTOM_NAME_TYPE = "CustomName";
    public static final String TAG_CUSTOM_DESCRIPTION_TYPE = "CustomDescription";
    public static final String TAG_LOCKED = "Locked";
    public static final String TAG_COMPASS_SEARCH_ID = "searchId";

    public HoneyCompass(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack itemStack) {
        return getBooleanTag(itemStack.getTag(), TAG_LOCKED) || super.isFoil(itemStack);
    }

    @Override
    public String getDescriptionId(ItemStack itemStack) {
        if (getBooleanTag(itemStack.getTag(), TAG_LOADING)) {
            return "item.the_bumblezone.honey_compass_structure_loading";
        }

        if (getBooleanTag(itemStack.getTag(), TAG_FAILED)) {
            return "item.the_bumblezone.honey_compass_structure_failed";
        }

        if (hasTagSafe(itemStack.getTag(), TAG_CUSTOM_NAME_TYPE)) {
            return itemStack.getTag().getString(TAG_CUSTOM_NAME_TYPE);
        }

        if(isStructureCompass(itemStack)) {
            return "item.the_bumblezone.honey_compass_structure";
        }

        if(isBlockCompass(itemStack)) {
            return "item.the_bumblezone.honey_compass_block";
        }

        return super.getDescriptionId(itemStack);
    }

    public Component getName(ItemStack itemStack) {
        if(isBlockCompass(itemStack)) {
            String blockString = getStoredBlock(itemStack);
            if (blockString != null) {
                Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(blockString));
                if (block != Blocks.AIR) {
                    return Component.translatable(this.getDescriptionId(itemStack), block.getName());
                }
            }
            return Component.translatable(this.getDescriptionId(itemStack), Component.translatable("item.the_bumblezone.honey_compass_unknown_block"));
        }
        return Component.translatable(this.getDescriptionId(itemStack));
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Level level, List<Component> components, TooltipFlag tooltipFlag) {
        if (getBooleanTag(itemStack.getTag(), TAG_FAILED)) {
            components.add(Component.translatable("item.the_bumblezone.honey_compass_structure_failed_description"));
            return;
        }

        if (hasTagSafe(itemStack.getTag(), TAG_CUSTOM_DESCRIPTION_TYPE)) {
            components.add(Component.translatable(itemStack.getTag().getString(TAG_CUSTOM_DESCRIPTION_TYPE)));
            appendAdvancedTooltipInfo(itemStack, level, components, tooltipFlag);
            return;
        }

        if (isBlockCompass(itemStack)) {
            components.add(Component.translatable("item.the_bumblezone.honey_compass_block_description1"));
            components.add(Component.translatable("item.the_bumblezone.honey_compass_block_description2"));
            components.add(Component.translatable("item.the_bumblezone.honey_compass_block_description3"));
            components.add(Component.translatable("item.the_bumblezone.honey_compass_block_description4"));
        }
        else if (isStructureCompass(itemStack)) {
            components.add(Component.translatable("item.the_bumblezone.honey_compass_structure_description1"));
            components.add(Component.translatable("item.the_bumblezone.honey_compass_structure_description2"));
            components.add(Component.translatable("item.the_bumblezone.honey_compass_structure_description3"));
        }
        else {
            components.add(Component.translatable("item.the_bumblezone.honey_compass_description1"));
            components.add(Component.translatable("item.the_bumblezone.honey_compass_description2"));
            components.add(Component.translatable("item.the_bumblezone.honey_compass_description3"));
            components.add(Component.translatable("item.the_bumblezone.honey_compass_description4"));
        }

        appendAdvancedTooltipInfo(itemStack, level, components, tooltipFlag);
    }

    private static void appendAdvancedTooltipInfo(ItemStack itemStack, Level level, List<Component> components, TooltipFlag tooltipFlag) {
        if (level != null && level.isClientSide()) {
            Player player = GeneralUtilsClient.getClientPlayer();
            if (player != null && tooltipFlag.isAdvanced() && (isBlockCompass(itemStack) || isStructureCompass(itemStack))) {
                BlockPos pos = getStoredPosition(itemStack);
                Optional<ResourceKey<Level>> storedDimension = getStoredDimension(itemStack);
                if (pos != null && storedDimension.isPresent() && level.dimension().equals(storedDimension.get())) {
                    components.add(Component.translatable("item.the_bumblezone.honey_compass_distance", player.blockPosition().distManhattan(pos))
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (!level.isClientSide) {
            CompoundTag tag = itemStack.getOrCreateTag();

            if (level.getGameTime() % 20 == 0) {
                UUID searchId = getSearchId(tag);
                if (searchId != null) {
                    // Location was found and already saved.
                    if (tag.contains(TAG_TARGET_POS)) {
                        removeSearchIdMode(tag, searchId);
                    }
                    else {
                        Optional<BlockPos> searchResult = ThreadExecutor.getSearchResult(searchId);
                        // null return mean no search queued up for this compass
                        if (searchResult == null) {
                            itemStack.getOrCreateTag().putBoolean(TAG_FAILED, true);
                        }
                        else {
                            searchResult.ifPresent(blockPos ->
                                    HoneyCompass.addFoundStructureLocation(level.dimension(), blockPos, tag)
                            );
                        }
                    }
                }
            }

            if (!getBooleanTag(tag, TAG_FAILED) &&
                !getBooleanTag(tag, TAG_LOADING) &&
                hasTagSafe(tag, TAG_STRUCTURE_TAG) &&
                !hasTagSafe(tag, TAG_TARGET_POS))
            {
                itemStack.getOrCreateTag().putBoolean(TAG_FAILED, true);
            }

            if (getBooleanTag(tag, TAG_LOADING) && !ThreadExecutor.isRunningASearch() && !ThreadExecutor.hasQueuedSearch()) {
                tag.putBoolean(TAG_LOADING, false);
                tag.putBoolean(TAG_FAILED, true);
            }

            if (isBlockCompass(itemStack)) {
                if (tag.contains(TAG_TARGET_POS) && tag.contains(TAG_TARGET_BLOCK) && tag.contains(TAG_TARGET_DIMENSION)) {

                    Optional<ResourceKey<Level>> optional = Level.RESOURCE_KEY_CODEC.parse(NbtOps.INSTANCE, tag.get(HoneyCompass.TAG_TARGET_DIMENSION)).result();
                    if (optional.isPresent() && optional.get().equals(level.dimension())) {
                        BlockPos blockPos = NbtUtils.readBlockPos(tag.getCompound(TAG_TARGET_POS));
                        if (!level.isInWorldBounds(blockPos)) {
                            tag.remove(TAG_TARGET_POS);
                            tag.remove(TAG_TARGET_DIMENSION);
                            tag.remove(TAG_TARGET_BLOCK);
                            tag.remove(TAG_TYPE);
                            return;
                        }

                        ChunkAccess chunk = level.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4, ChunkStatus.FULL, false);
                        if(chunk != null && !(BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(blockPos).getBlock()).toString().equals(tag.getString(TAG_TARGET_BLOCK)))) {
                            tag.remove(TAG_TARGET_POS);
                            tag.remove(TAG_TARGET_DIMENSION);
                            tag.remove(TAG_TARGET_BLOCK);
                            tag.remove(TAG_TYPE);
                        }
                    }
                }
                else {
                    tag.remove(TAG_TARGET_POS);
                    tag.remove(TAG_TARGET_DIMENSION);
                    tag.remove(TAG_TARGET_BLOCK);
                    tag.remove(TAG_TYPE);
                }
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand)  {
        ItemStack itemStack = player.getItemInHand(interactionHand);
        BlockPos playerPos = player.blockPosition();

        if (getBooleanTag(itemStack.getTag(), TAG_FAILED) && hasTagSafe(itemStack.getTag(), TAG_STRUCTURE_TAG)) {
            if (level instanceof ServerLevel serverLevel && serverLevel.getServer().getWorldData().worldGenOptions().generateStructures()) {
                TagKey<Structure> structureTagKey = TagKey.create(Registries.STRUCTURE, new ResourceLocation(itemStack.getOrCreateTag().getString(TAG_STRUCTURE_TAG)));
                Optional<HolderSet.Named<Structure>> optional = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(structureTagKey);
                boolean structureExists = optional.isPresent() && optional.get().stream().anyMatch(structureHolder -> serverLevel.getChunkSource().getGeneratorState().getPlacementsForStructure(structureHolder).size() > 0);
                if (structureExists) {
                    itemStack.getOrCreateTag().putBoolean(TAG_LOADING, true);
                    itemStack.getOrCreateTag().putBoolean(TAG_FAILED, false);
                    ThreadExecutor.locate((ServerLevel) level, structureTagKey, playerPos, 100, false)
                            .thenOnServerThread(foundPos -> setCompassData((ServerLevel) level, (ServerPlayer) player, interactionHand, itemStack, foundPos));
                }
                else {
                    player.displayClientMessage(Component.translatable("item.the_bumblezone.honey_compass_structure_wrong_dimension"), true);
                    return InteractionResultHolder.pass(itemStack);
                }
            }
            return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
        }

        if (itemStack.hasTag() && getBooleanTag(itemStack.getOrCreateTag(), TAG_LOADING)) {
            if (ThreadExecutor.isRunningASearch() || ThreadExecutor.hasQueuedSearch()) {
                return InteractionResultHolder.fail(itemStack);
            }
            else {
                itemStack.getOrCreateTag().putBoolean(TAG_LOADING, false);
            }
        }

        if (getBooleanTag(itemStack.getTag(), TAG_LOCKED)) {
            return super.use(level, player, interactionHand);
        }

        if (level instanceof ServerLevel serverLevel && !isStructureCompass(itemStack)) {
            Optional<HolderSet.Named<Structure>> optional = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(BzTags.HONEY_COMPASS_DEFAULT_LOCATING);
            boolean structureExists = optional.isPresent() && optional.get().stream().anyMatch(structureHolder -> serverLevel.getChunkSource().getGeneratorState().getPlacementsForStructure(structureHolder).size() > 0);
            if (structureExists) {
                itemStack.getOrCreateTag().putBoolean(TAG_LOADING, true);
                ThreadExecutor.locate((ServerLevel) level, BzTags.HONEY_COMPASS_DEFAULT_LOCATING, playerPos, 100, false)
                        .thenOnServerThread(foundPos -> setCompassData((ServerLevel) level, (ServerPlayer) player, interactionHand, itemStack, foundPos));
            }
            else {
                player.displayClientMessage(Component.translatable("item.the_bumblezone.honey_compass_structure_wrong_dimension"), true);
                return InteractionResultHolder.pass(itemStack);
            }
            return InteractionResultHolder.success(itemStack);
        }

        return super.use(level, player, interactionHand);
    }

    private void setCompassData(ServerLevel serverLevel, ServerPlayer serverPlayer, InteractionHand interactionHand, ItemStack itemStack, BlockPos structurePos) {
        itemStack.getOrCreateTag().putBoolean(TAG_LOADING, false);
        serverLevel.playSound(null, serverPlayer.blockPosition(), BzSounds.HONEY_COMPASS_STRUCTURE_LOCK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);

        serverPlayer.awardStat(Stats.ITEM_USED.get(itemStack.getItem()));

        if (structurePos == null) {
            serverPlayer.swing(interactionHand);
            serverPlayer.displayClientMessage(Component.translatable("item.the_bumblezone.honey_compass_structure_failed"), false);
            return;
        }

        BzCriterias.HONEY_COMPASS_USE_TRIGGER.trigger(serverPlayer);
        boolean singleCompass = !serverPlayer.getAbilities().instabuild && itemStack.getCount() == 1;
        if (singleCompass) {
            addFoundStructureLocation(serverLevel.dimension(), structurePos, itemStack.getOrCreateTag());
        }
        else {
            ItemStack newCompass = new ItemStack(BzItems.HONEY_COMPASS.get(), 1);
            CompoundTag newCompoundTag = itemStack.hasTag() ? itemStack.getTag().copy() : new CompoundTag();
            newCompass.setTag(newCompoundTag);
            if (!serverPlayer.getAbilities().instabuild) {
                itemStack.shrink(1);
            }

            addFoundStructureLocation(serverLevel.dimension(), structurePos, newCompoundTag);
            if (!serverPlayer.getInventory().add(newCompass)) {
                serverPlayer.drop(newCompass, false);
            }
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext useOnContext) {
        BlockPos blockPos = useOnContext.getClickedPos();
        Level level = useOnContext.getLevel();
        Player player = useOnContext.getPlayer();
        ItemStack handCompass = useOnContext.getItemInHand();
        BlockState targetBlock = level.getBlockState(blockPos);

        if (handCompass.hasTag() && getBooleanTag(handCompass.getOrCreateTag(), TAG_LOADING)) {
            if (ThreadExecutor.isRunningASearch() || ThreadExecutor.hasQueuedSearch()) {
                return InteractionResult.FAIL;
            }
            else {
                handCompass.getOrCreateTag().putBoolean(TAG_LOADING, false);
            }
        }

        if (getBooleanTag(handCompass.getTag(), TAG_LOCKED)) {
            return super.useOn(useOnContext);
        }

        if (player != null && isValidBeeHive(targetBlock)) {
            level.playSound(null, blockPos, BzSounds.HONEY_COMPASS_BLOCK_LOCK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            if(player instanceof ServerPlayer serverPlayer) {
                BzCriterias.HONEY_COMPASS_USE_TRIGGER.trigger(serverPlayer);
            }

            if (!level.isClientSide()) {
                boolean singleCompass = !player.getAbilities().instabuild && handCompass.getCount() == 1;
                if (singleCompass) {
                    addBlockTags(level.dimension(), blockPos, handCompass.getOrCreateTag(), targetBlock.getBlock());
                }
                else {
                    ItemStack newCompass = new ItemStack(BzItems.HONEY_COMPASS.get(), 1);
                    CompoundTag newCompoundTag = handCompass.hasTag() ? handCompass.getTag().copy() : new CompoundTag();
                    newCompass.setTag(newCompoundTag);
                    if (!player.getAbilities().instabuild) {
                        handCompass.shrink(1);
                    }

                    addBlockTags(level.dimension(), blockPos, newCompoundTag, targetBlock.getBlock());
                    if (!player.getInventory().add(newCompass)) {
                        player.drop(newCompass, false);
                    }
                }
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useOn(useOnContext);
    }

    public static boolean isValidBeeHive(BlockState block) {
        if (block.is(BzTags.FORCED_ALLOWED_POSITION_TRACKING_BLOCKS)) return true;

        if(block.is(BzTags.DISALLOWED_POSITION_TRACKING_BLOCKS)) return false;

        if(block.is(BlockTags.BEEHIVES) || block.getBlock() instanceof BeehiveBlock) {
            return true;
        }

        return false;
    }

    public static boolean getBooleanTag(CompoundTag compoundTag, String tagName) {
        if (compoundTag == null || !compoundTag.contains(tagName)) {
            return false;
        }
        return compoundTag.getBoolean(tagName);
    }

    public static boolean hasTagSafe(CompoundTag compoundTag, String tagName) {
        return compoundTag != null && compoundTag.contains(tagName);
    }

    public static void setSearchId(CompoundTag compoundTag, UUID uuid) {
        compoundTag.putUUID(TAG_COMPASS_SEARCH_ID, uuid);
    }

    public static UUID getSearchId(CompoundTag compoundTag) {
        if (compoundTag.contains(TAG_COMPASS_SEARCH_ID)) {
            return compoundTag.getUUID(TAG_COMPASS_SEARCH_ID);
        }

        return null;
    }

    public static void setStructureTags(CompoundTag compoundTag, TagKey<Structure> structureTagKey) {
        compoundTag.putString(TAG_STRUCTURE_TAG, structureTagKey.location().toString());
    }

    public static void addFoundStructureLocation(ResourceKey<Level> resourceKey, BlockPos blockPos, CompoundTag compoundTag) {
        compoundTag.put(TAG_TARGET_POS, NbtUtils.writeBlockPos(blockPos));
        Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, resourceKey).resultOrPartial(Bumblezone.LOGGER::error).ifPresent(tag -> compoundTag.put(TAG_TARGET_DIMENSION, tag));
        compoundTag.putString(TAG_TYPE, "structure");
        compoundTag.remove(TAG_TARGET_BLOCK);
        compoundTag.remove(HoneyCompass.TAG_LOADING);
        compoundTag.remove(HoneyCompass.TAG_FAILED);
        compoundTag.remove(HoneyCompass.TAG_STRUCTURE_TAG);
    }

    public static void addBlockTags(ResourceKey<Level> resourceKey, BlockPos blockPos, CompoundTag compoundTag, Block block) {
        compoundTag.put(TAG_TARGET_POS, NbtUtils.writeBlockPos(blockPos));
        Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, resourceKey).resultOrPartial(Bumblezone.LOGGER::error).ifPresent(tag -> compoundTag.put(TAG_TARGET_DIMENSION, tag));
        compoundTag.putString(TAG_TYPE, "block");
        compoundTag.putString(TAG_TARGET_BLOCK, BuiltInRegistries.BLOCK.getKey(block).toString());
        compoundTag.remove(HoneyCompass.TAG_LOADING);
        compoundTag.remove(HoneyCompass.TAG_FAILED);
        compoundTag.remove(HoneyCompass.TAG_STRUCTURE_TAG);
    }

    public static boolean isBlockCompass(ItemStack compassItem) {
        if(compassItem.hasTag()) {
            CompoundTag tag = compassItem.getTag();
            return tag != null && tag.contains(TAG_TYPE) && tag.getString(TAG_TYPE).equals("block");
        }
        return false;
    }

    public static String getStoredBlock(ItemStack compassItem) {
        if(compassItem.hasTag()) {
            CompoundTag tag = compassItem.getTag();
            if (tag != null && tag.contains(TAG_TARGET_BLOCK)) {
                return tag.getString(TAG_TARGET_BLOCK);
            }
        }
        return null;
    }

    public static BlockPos getStoredPosition(ItemStack compassItem) {
        if(compassItem.hasTag()) {
            CompoundTag tag = compassItem.getTag();
            if (tag != null && tag.contains(TAG_TARGET_POS)) {
                return NbtUtils.readBlockPos(tag.getCompound(TAG_TARGET_POS));
            }
        }
        return null;
    }

    public static Optional<ResourceKey<Level>> getStoredDimension(ItemStack compassItem) {
        if(compassItem.hasTag()) {
            CompoundTag tag = compassItem.getTag();
            if (tag != null && tag.contains(TAG_TARGET_DIMENSION)) {
                return Level.RESOURCE_KEY_CODEC.parse(NbtOps.INSTANCE, tag.get(HoneyCompass.TAG_TARGET_DIMENSION)).result();
            }
        }
        return Optional.empty();
    }

    public static boolean isStructureCompass(ItemStack compassItem) {
        if(compassItem.hasTag()) {
            CompoundTag tag = compassItem.getTag();
            return tag != null && tag.contains(TAG_TYPE) && tag.getString(TAG_TYPE).equals("structure");
        }
        return false;
    }

    private static void removeSearchIdMode(CompoundTag tag, UUID searchId) {
        tag.remove(HoneyCompass.TAG_COMPASS_SEARCH_ID);
        ThreadExecutor.removeSearchResult(searchId);
    }
}
