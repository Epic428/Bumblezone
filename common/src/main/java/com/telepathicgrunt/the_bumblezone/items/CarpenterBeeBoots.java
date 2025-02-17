package com.telepathicgrunt.the_bumblezone.items;

import com.telepathicgrunt.the_bumblezone.modinit.BzCriterias;
import com.telepathicgrunt.the_bumblezone.modinit.BzEffects;
import com.telepathicgrunt.the_bumblezone.modinit.BzStats;
import com.telepathicgrunt.the_bumblezone.modinit.BzTags;
import com.telepathicgrunt.the_bumblezone.platform.ItemExtension;
import com.telepathicgrunt.the_bumblezone.utils.GeneralUtils;
import com.telepathicgrunt.the_bumblezone.utils.OptionalBoolean;
import com.telepathicgrunt.the_bumblezone.utils.PlatformHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CarpenterBeeBoots extends BeeArmor implements ItemExtension {

    public CarpenterBeeBoots(ArmorMaterial material, ArmorItem.Type armorType, Properties properties, int variant) {
        super(material, armorType, properties, variant, false);
    }

    /**
     * Return whether this item is repairable in an anvil.
     */
    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(BzTags.BEE_ARMOR_REPAIR_ITEMS);
    }

    // Runs on Forge
    public void onArmorTick(ItemStack itemstack, Level world, Player player) {
        this.bz$onArmorTick(itemstack, world, player);
    }

    @Override
    public void bz$onArmorTick(ItemStack itemstack, Level world, Player player) {
        if (player.isSpectator()) {
            return;
        }

        if (player.getCooldowns().isOnCooldown(itemstack.getItem())) {
            return;
        }

        RandomSource random = player.getRandom();
        int beeWearablesCount = BeeArmor.getBeeThemedWearablesCount(player);
        CompoundTag tag = itemstack.getOrCreateTag();

        if(!world.isClientSide()) {
            int itemId = generateUniqueItemId(world, random, tag, tag.getInt("itemstackId"));
            int lastSentState = tag.getInt("lastSentState");

            double xInBlock = Math.abs(player.position().x()) % 1;
            double zInBlock = Math.abs(player.position().z()) % 1;
            if (player.isShiftKeyDown() &&
                player.getLookAngle().y() < -0.9d &&
                xInBlock > 0.2d &&
                xInBlock < 0.8d &&
                zInBlock > 0.2d &&
                zInBlock < 0.8d &&
                GeneralUtils.isPermissionAllowedAtSpot(world, player, player.blockPosition().below(), false))
            {
                BlockPos belowBlockPos = BlockPos.containing(player.position().add(0, -0.1d, 0));
                BlockState belowBlockState = world.getBlockState(belowBlockPos);

                if (belowBlockState.is(BzTags.CARPENTER_BEE_BOOTS_MINEABLES)) {
                    int miningStartTime = tag.getInt("miningStartTime");
                    if (miningStartTime == 0) {
                        miningStartTime = (int) world.getGameTime();

                        if (!world.isClientSide()) {
                            tag.putInt("miningStartTime", miningStartTime);
                        }
                    }

                    int timeDiff = ((int) (world.getGameTime()) - miningStartTime);
                    float miningProgress = (float) (timeDiff + 1);

                    float blockDestroyTime = belowBlockState.getDestroySpeed(world, belowBlockPos);
                    float playerMiningSpeed = getPlayerDestroySpeed(player, itemstack, ((beeWearablesCount - 1) * 0.1F) + 0.3F);
                    int finalMiningProgress = (int) ((miningProgress * playerMiningSpeed) / blockDestroyTime);

                    if (!(finalMiningProgress == 0 && playerMiningSpeed < 0.001f) && (finalMiningProgress != lastSentState)) {
                        world.destroyBlockProgress(itemId, belowBlockPos, finalMiningProgress);
                        tag.putInt("lastSentState", finalMiningProgress);
                    }

                    if (finalMiningProgress >= 10) {
                        world.destroyBlockProgress(itemId, belowBlockPos, -1);

                        // Post the block break event
                        BlockState state = world.getBlockState(belowBlockPos);
                        BlockEntity entity = world.getBlockEntity(belowBlockPos);

                        // Handle if the event is canceled
                        if (PlatformHooks.sendBlockBreakEvent(world, belowBlockPos, state, entity, player)) {
                            return;
                        }

                        boolean blockBroken = world.destroyBlock(belowBlockPos, false, player);

                        if (blockBroken) {
                            BlockEntity blockEntity = belowBlockState.hasBlockEntity() ? world.getBlockEntity(belowBlockPos) : null;

                            belowBlockState.getBlock().playerDestroy(
                                    world,
                                    player,
                                    belowBlockPos,
                                    belowBlockState,
                                    blockEntity,
                                    itemstack);

                            if(random.nextFloat() < 0.045) {
                                itemstack.hurtAndBreak(1, player, (playerEntity) -> playerEntity.broadcastBreakEvent(EquipmentSlot.FEET));
                            }

                            if(player instanceof ServerPlayer serverPlayer) {
                                serverPlayer.awardStat(BzStats.CARPENTER_BEE_BOOTS_MINED_BLOCKS_RL.get());

                                if(serverPlayer.getStats().getValue(Stats.CUSTOM.get(BzStats.CARPENTER_BEE_BOOTS_MINED_BLOCKS_RL.get(), StatFormatter.DEFAULT)) >= 64) {
                                    BzCriterias.CARPENTER_BEE_BOOTS_MINED_BLOCKS_TRIGGER.trigger(serverPlayer);
                                }
                            }
                            PlatformHooks.afterBlockBreakEvent(world, belowBlockPos, state, entity, player);
                        }

                        tag.putInt("lastSentState", -1);
                        tag.putInt("miningStartTime", 0);
                    }
                }
                else {
                    world.destroyBlockProgress(itemId, belowBlockPos, -1);
                    tag.putInt("lastSentState", -1);
                    tag.putInt("miningStartTime", 0);
                }
            }
            else if (lastSentState != -1) {
                BlockPos belowBlockPos = BlockPos.containing(player.position().add(0, -0.1d, 0));
                world.destroyBlockProgress(itemId, belowBlockPos, -1);
                tag.putInt("lastSentState", -1);
                tag.putInt("miningStartTime", 0);
            }
        }

        int hangTime = tag.getInt("hangTime");
        if(!world.isClientSide() && hangTime > 0 && player.onGround()) {
            hangTime = 0;
            tag.putInt("hangTime", hangTime);
        }

        double playerDeltaY = player.getDeltaMovement().y();
        int hangCooldownTimer = tag.getInt("hangCooldownTimer");
        int maxHangTime = ((beeWearablesCount - 1) * 22) + 35;
        if (!player.getAbilities().flying &&
            !player.isPassenger() &&
            !player.onClimbable() &&
            !player.onGround() &&
            !player.isInWater() &&
            !player.isShiftKeyDown() &&
            playerDeltaY <= 0 &&
            playerDeltaY >= -1.2f &&
            hangCooldownTimer <= 0)
        {
            for (float xOffset = -0.45f; xOffset <= 0.45f; xOffset += 0.45f) {
                for (float zOffset = -0.45f; zOffset <= 0.45f; zOffset += 0.45f) {
                    if(xOffset != 0 && zOffset != 0) {
                        BlockPos posToCheck = BlockPos.containing(player.position().add(xOffset, 0.057f, zOffset));
                        BlockState sideBlockState = world.getBlockState(posToCheck);
                        if (sideBlockState.is(BzTags.CARPENTER_BEE_BOOTS_CLIMBABLES)) {
                            double newDeltaY = Math.min(playerDeltaY * 0.9d + 0.07d, -0.0055);
                            player.setDeltaMovement(new Vec3(
                                player.getDeltaMovement().x(),
                                newDeltaY,
                                player.getDeltaMovement().z()
                            ));

                            if (newDeltaY >= -0.0135f) {
                                player.setJumping(false);
                                player.setOnGround(true);
                            }

                            if (newDeltaY >= -0.4f) {
                                player.fallDistance = 0;
                            }

                            if(!world.isClientSide()) {
                                if(hangTime >= maxHangTime) {
                                    tag.putInt("hangCooldownTimer", 10);
                                    tag.putInt("hangTime", 0);
                                }
                                else {
                                    tag.putInt("hangTime", hangTime + 1);

                                    if(player instanceof ServerPlayer serverPlayer) {
                                        serverPlayer.awardStat(BzStats.CARPENTER_BEE_BOOTS_WALL_HANG_TIME_RL.get());

                                        if(serverPlayer.getStats().getValue(Stats.CUSTOM.get(BzStats.CARPENTER_BEE_BOOTS_WALL_HANG_TIME_RL.get(), StatFormatter.DEFAULT)) >= 4000) {
                                            BzCriterias.CARPENTER_BEE_BOOTS_WALL_HANGING_TRIGGER.trigger(serverPlayer);
                                        }
                                    }
                                }
                            }

                            if(random.nextFloat() < 0.001) {
                                itemstack.hurtAndBreak(1, player, (playerEntity) -> playerEntity.broadcastBreakEvent(EquipmentSlot.FEET));
                            }
                        }
                    }
                }
            }
        }
        else if(!world.isClientSide() && hangCooldownTimer > 0) {
            int newCoolDown = hangCooldownTimer - 1;
            tag.putInt("hangCooldownTimer", newCoolDown);

        }
    }

    public static float getPlayerDestroySpeed(Player player, ItemStack beeBoots, float currentSpeed) {
        int efficencyLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, beeBoots);
        if (efficencyLevel > 0) {
            currentSpeed += efficencyLevel / 4f;
        }

        if (MobEffectUtil.hasDigSpeed(player)) {
            currentSpeed *= 1.0F + (float)(MobEffectUtil.getDigSpeedAmplification(player) + 1) * 0.2F;
        }

        if (player.hasEffect(BzEffects.BEENERGIZED.get())) {
            currentSpeed *= 1.0F + ((float)(player.getEffect(BzEffects.BEENERGIZED.get()).getAmplifier() + 1) );
        }

        if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            float miningDecrease = switch (player.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };

            currentSpeed *= miningDecrease;
        }

        if (player.isEyeInFluid(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)) {
            currentSpeed /= 5.0F;
        }

        if (!player.onGround()) {
            currentSpeed /= 5.0F;
        }

        return currentSpeed;
    }

    private static int generateUniqueItemId(Level world, RandomSource random, CompoundTag tag, int itemId) {
        if (itemId == 0 && !world.isClientSide()) {
            while (true) {
                boolean anymatch = false;
                itemId = random.nextInt(Integer.MAX_VALUE);
                for (Player worldPlayer : world.players()) {
                    if (worldPlayer.getId() == itemId) {
                        anymatch = true;
                        break;
                    }
                }
                if (!anymatch) {
                    break;
                }
            }
            tag.putInt("itemstackId", itemId);
        }
        return itemId;
    }

    public static ItemStack getEntityBeeBoots(Entity entity) {
        for(ItemStack armor : entity.getArmorSlots()) {
            if(armor.getItem() instanceof CarpenterBeeBoots) {
                return armor;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public OptionalBoolean bz$canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if(enchantment.category == EnchantmentCategory.DIGGER) {
            return OptionalBoolean.TRUE;
        }

        return OptionalBoolean.of(enchantment.category.canEnchant(stack.getItem()));
    }

    // Runs on Forge
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return this.bz$canApplyAtEnchantingTable(stack, enchantment)
                .orElseGet(() -> enchantment.category.canEnchant(stack.getItem()));
    }
}