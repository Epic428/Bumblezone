package com.telepathicgrunt.the_bumblezone.entities.mobs;

import com.telepathicgrunt.the_bumblezone.Bumblezone;
import com.telepathicgrunt.the_bumblezone.client.rendering.beequeen.BeeQueenPose;
import com.telepathicgrunt.the_bumblezone.configs.BzBeeAggressionConfigs;
import com.telepathicgrunt.the_bumblezone.configs.BzGeneralConfigs;
import com.telepathicgrunt.the_bumblezone.entities.goals.BeeQueenAlwaysLookAtPlayerGoal;
import com.telepathicgrunt.the_bumblezone.entities.goals.BeeQueenAngerableMeleeAttackGoal;
import com.telepathicgrunt.the_bumblezone.entities.queentrades.QueensTradeManager;
import com.telepathicgrunt.the_bumblezone.entities.queentrades.WeightedTradeResult;
import com.telepathicgrunt.the_bumblezone.items.essence.EssenceOfTheBees;
import com.telepathicgrunt.the_bumblezone.mixin.entities.ItemEntityAccessor;
import com.telepathicgrunt.the_bumblezone.mixin.entities.PlayerAdvancementsAccessor;
import com.telepathicgrunt.the_bumblezone.modinit.BzCriterias;
import com.telepathicgrunt.the_bumblezone.modinit.BzEffects;
import com.telepathicgrunt.the_bumblezone.modinit.BzItems;
import com.telepathicgrunt.the_bumblezone.modinit.BzSounds;
import com.telepathicgrunt.the_bumblezone.modinit.BzTags;
import com.telepathicgrunt.the_bumblezone.modules.PlayerDataHandler;
import com.telepathicgrunt.the_bumblezone.modules.base.ModuleHelper;
import com.telepathicgrunt.the_bumblezone.modules.registry.ModuleRegistry;
import com.telepathicgrunt.the_bumblezone.utils.GeneralUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class BeeQueenEntity extends Animal implements NeutralMob {
    private final static TargetingConditions PLAYER_ACKNOWLEDGE_SIGHT = TargetingConditions.forNonCombat();

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState attackAnimationState = new AnimationState();
    public final AnimationState itemThrownAnimationState = new AnimationState();
    public final AnimationState itemRejectAnimationState = new AnimationState();
    public static final EntityDataSerializer<BeeQueenPose> QUEEN_POSE_SERIALIZER = EntityDataSerializer.simpleEnum(BeeQueenPose.class);
    private static final EntityDataAccessor<Integer> THROWCOOLDOWN = SynchedEntityData.defineId(BeeQueenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BEESPAWNCOOLDOWN = SynchedEntityData.defineId(BeeQueenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REMAINING_ANGER_TIME = SynchedEntityData.defineId(BeeQueenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BeeQueenPose> QUEEN_POSE = SynchedEntityData.defineId(BeeQueenEntity.class, QUEEN_POSE_SERIALIZER);
    private static final EntityDataAccessor<Integer> REMAINING_BONUS_TRADE_TIME = SynchedEntityData.defineId(BeeQueenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> BONUS_TRADE_ITEM = SynchedEntityData.defineId(BeeQueenEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(60, 120);
    private final Set<UUID> acknowledgedPlayers = new HashSet<>();
    private final HashMap<UUID, Item> acknowledgedPlayerHeldItem = new HashMap<>();
    private UUID persistentAngerTarget;
    private int underWaterTicks;
    private int poseTicks;
    private boolean hasTrades = true;
    private static final WeightedTradeResult ESSENCE_DROP = new WeightedTradeResult(null, Optional.of(List.of(BzItems.ESSENCE_OF_THE_BEES.get())), 1, 1000, 1);

    public BeeQueenEntity(EntityType<? extends BeeQueenEntity> type, Level world) {
        super(type, world);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(THROWCOOLDOWN, 0);
        this.entityData.define(REMAINING_ANGER_TIME, 0);
        this.entityData.define(BEESPAWNCOOLDOWN, 0);
        this.entityData.define(QUEEN_POSE, BeeQueenPose.NONE);
        this.entityData.define(REMAINING_BONUS_TRADE_TIME, 0);
        this.entityData.define(BONUS_TRADE_ITEM, ItemStack.EMPTY);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
        if (QUEEN_POSE.equals(entityDataAccessor)) {
            BeeQueenPose pose = this.getQueenPose();
            setAnimationState(pose, BeeQueenPose.ATTACKING, this.attackAnimationState);
            setAnimationState(pose, BeeQueenPose.ITEM_REJECT, this.itemRejectAnimationState);
            setAnimationState(pose, BeeQueenPose.ITEM_THROW, this.itemThrownAnimationState);
        }

        super.onSyncedDataUpdated(entityDataAccessor);
    }

    private void setAnimationState(BeeQueenPose pose, BeeQueenPose poseToCheckFor, AnimationState animationState) {
        if (pose == poseToCheckFor) {
            animationState.start(this.tickCount);
        }
        else {
            animationState.stop();
        }
    }

    public static AttributeSupplier.Builder getAttributeBuilder() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.ATTACK_DAMAGE, 10.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new BeeQueenAngerableMeleeAttackGoal(this));
        this.goalSelector.addGoal(2, new BeeQueenAlwaysLookAtPlayerGoal(this, Player.class, 60));
        this.goalSelector.addGoal(3, new FloatGoal(this));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("throwcooldown", getThrowCooldown());
        tag.putInt("beespawncooldown", getBeeSpawnCooldown());
        tag.putInt("bonusTradetime", getRemainingBonusTradeTime());
        tag.put("bonusTradeitem", getBonusTradeItem().save(new CompoundTag()));
        this.addPersistentAngerSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setThrowCooldown(tag.getInt("throwcooldown"));
        setBeeSpawnCooldown(tag.getInt("beespawncooldown"));
        setRemainingBonusTradeTime(tag.getInt("bonusTradetime"));
        setBonusTradeItem(ItemStack.of(tag.getCompound("bonusTradeitem")));

        if (getBonusTradeItem().is(BzTags.DISALLOWED_RANDOM_BONUS_TRADE_ITEMS) &&
            !getBonusTradeItem().is(BzTags.FORCED_ALLOWED_RANDOM_BONUS_TRADE_ITEMS))
        {
            setBonusTradeItem(ItemStack.EMPTY);
            setRemainingBonusTradeTime(0);
        }

        this.readPersistentAngerSaveData(this.level(), tag);
    }

    public void setQueenPose(BeeQueenPose beeQueenPose) {
        this.entityData.set(QUEEN_POSE, beeQueenPose);
    }

    public BeeQueenPose getQueenPose() {
        return this.entityData.get(QUEEN_POSE);
    }

    @Override
    protected PathNavigation createNavigation(Level pLevel) {
        return new DirectPathNavigator(this, pLevel);
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    public static boolean checkMobSpawnRules(EntityType<? extends Mob> entityType, LevelAccessor iWorld, MobSpawnType spawnReason, BlockPos blockPos, RandomSource random) {
        return true;
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor world, MobSpawnType spawnReason) {
        return true;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader worldReader) {
        AABB box = getBoundingBox();
        return !worldReader.containsAnyLiquid(box) && worldReader.getBlockStates(box).noneMatch(state -> state.blocksMotion()) && worldReader.isUnobstructed(this);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public double getPassengersRidingOffset() {
        return this.getDimensions(Pose.STANDING).height * 0.90f;
    }

    @Override
    public void positionRider(Entity entity, MoveFunction moveFunction) {
        if (this.hasPassenger(entity)) {
            double riderYOffset = this.getY() + this.getPassengersRidingOffset() + entity.getMyRidingOffset();
            Vec3 forwardVect = Vec3.directionFromRotation(0, this.getVisualRotationYInDegrees());
            Vec3 sideVect = Vec3.directionFromRotation(0, this.getVisualRotationYInDegrees() - 90);
            moveFunction.accept(entity, this.getX() + sideVect.x() - (forwardVect.x() * 0.5d), riderYOffset, this.getZ() + sideVect.z() - (forwardVect.z() * 0.5d));
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        if (damageSource == level().damageSources().sweetBerryBush()) {
            return true;
        }
        return super.isInvulnerableTo(damageSource);
    }

    @Override
    public void makeStuckInBlock(BlockState blockState, Vec3 speedMult) {
        if (blockState.getBlock() instanceof SweetBerryBushBlock) {
            return;
        }
        super.makeStuckInBlock(blockState, speedMult);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        else if(isOnPortalCooldown() && source == level().damageSources().inWall()) {
            spawnAngryParticles(6);
            playHurtSound(source);
            return false;
        }
        else {
            if (!this.isNoAi()) {
                Entity entity = source.getEntity();
                if (entity instanceof LivingEntity livingEntity && !livingEntity.isSpectator()) {
                    if (livingEntity instanceof Player player && (level().getDifficulty() == Difficulty.PEACEFUL || player.isCreative())) {
                        spawnAngryParticles(6);
                        return super.hurt(source, amount);
                    }

                    if ((livingEntity.level().dimension().location().equals(Bumblezone.MOD_DIMENSION_ID) ||
                        BzBeeAggressionConfigs.allowWrathOfTheHiveOutsideBumblezone) &&
                        BzBeeAggressionConfigs.aggressiveBees)
                    {
                        if(livingEntity.hasEffect(BzEffects.PROTECTION_OF_THE_HIVE.get())) {
                            livingEntity.removeEffect(BzEffects.PROTECTION_OF_THE_HIVE.get());
                        }
                        else {
                            //Now all bees nearby in Bumblezone will get VERY angry!!!
                            livingEntity.addEffect(new MobEffectInstance(BzEffects.WRATH_OF_THE_HIVE.get(), BzBeeAggressionConfigs.howLongWrathOfTheHiveLasts, 3, false, BzBeeAggressionConfigs.showWrathOfTheHiveParticles, true));
                        }
                    }

                    this.startPersistentAngerTimer();
                    this.setPersistentAngerTarget(livingEntity.getUUID());
                    this.setTarget(livingEntity);
                }
            }

            spawnAngryParticles(6);
            return super.hurt(source, amount);
        }
    }

    protected void customServerAiStep() {
        if (this.isUnderWater()) {
            ++this.underWaterTicks;
        }
        else {
            this.underWaterTicks = 0;
        }

        if (this.underWaterTicks > 100) {
            this.hurt(level().damageSources().drown(), 3.0F);
        }

        if (!this.level().isClientSide) {
            this.updatePersistentAnger((ServerLevel)this.level(), false);
        }
    }

    public static void applyMiningFatigueInStructures(ServerPlayer serverPlayer) {
        if(serverPlayer.isCreative() || serverPlayer.isSpectator() || EssenceOfTheBees.hasEssence(serverPlayer)) {
            return;
        }

        StructureManager structureManager = ((ServerLevel)serverPlayer.level()).structureManager();
        if (structureManager.getStructureWithPieceAt(serverPlayer.blockPosition(), BzTags.BEE_QUEEN_MINING_FATIGUE).isValid() &&
            !serverPlayer.level().getEntitiesOfClass(BeeQueenEntity.class, serverPlayer.getBoundingBox().inflate(30.0D, 30.0D, 30.0D), (e) -> !e.isNoAi()).isEmpty())
        {
            serverPlayer.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN,
                    100,
                    2,
                    false,
                    false,
                    true));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isAlive()) {
            this.idleAnimationState.startIfStopped(this.tickCount);
        }
        else {
            this.idleAnimationState.stop();
        }

        BeeQueenPose pose = this.getQueenPose();
        if (pose != BeeQueenPose.NONE) {
            if (pose == BeeQueenPose.ATTACKING && poseTicks > 17) {
                setQueenPose(BeeQueenPose.NONE);
                poseTicks = 0;
            }
            if (pose == BeeQueenPose.ITEM_REJECT && poseTicks > 20) {
                setQueenPose(BeeQueenPose.NONE);
                poseTicks = 0;
            }
            if (pose == BeeQueenPose.ITEM_THROW && poseTicks > 20) {
                setQueenPose(BeeQueenPose.NONE);
                poseTicks = 0;
            }

            poseTicks++;
        }

        if (!this.isNoAi()) {
            if (!this.level().isClientSide() && this.level().getGameTime() % 200 == 0 && !this.isDeadOrDying()) {
                this.heal(1);
            }

            if (!this.level().isClientSide()) {
                if (this.isAngry()) {
                    performAngryActions();
                }
                else {
                    performGroundTrades();
                }
            }

            performBonusTradeTick();
        }
    }

    private void performBonusTradeTick() {
        if (!this.level().isClientSide()) {
            if (BzGeneralConfigs.beeQueenBonusTradeRewardMultiplier <= 1 ||
                BzGeneralConfigs.beeQueenBonusTradeDurationInTicks == 0 ||
                BzGeneralConfigs.beeQueenBonusTradeAmountTillSatified == 0)
            {
                if (getRemainingBonusTradeTime() > 0) {
                    setRemainingBonusTradeTime(0);
                }
                if (!getBonusTradeItem().isEmpty()) {
                    setBonusTradeItem(ItemStack.EMPTY);
                    this.acknowledgedPlayers.clear();
                }
            }

            int minNotifyTime = 1200;

            if (getRemainingBonusTradeTime() > 0) {
                setRemainingBonusTradeTime(getRemainingBonusTradeTime() - 1);
            }
            else if (!getBonusTradeItem().isEmpty()) {
                setBonusTradeItem(ItemStack.EMPTY);
                this.acknowledgedPlayers.clear();
            }

            if (hasTrades && !this.isAngry() && (this.level().getGameTime() + this.getUUID().getLeastSignificantBits()) % 20 == 0) {
                List<Player> nearbyPlayers = this.level().getNearbyPlayers(PLAYER_ACKNOWLEDGE_SIGHT, this, this.getBoundingBox().inflate(8));

                if (getRemainingBonusTradeTime() == 0) {
                    if (nearbyPlayers.size() > 0) {
                        setRemainingBonusTradeTime(BzGeneralConfigs.beeQueenBonusTradeDurationInTicks);

                        List<Item> allowedBonusTradeItems = QueensTradeManager.QUEENS_TRADE_MANAGER.queenTrades.keySet().stream()
                                .filter(i -> ((i.isEnabled(level().enabledFeatures()) &&
                                        !i.builtInRegistryHolder().is(BzTags.DISALLOWED_RANDOM_BONUS_TRADE_ITEMS)) ||
                                        i.builtInRegistryHolder().is(BzTags.FORCED_ALLOWED_RANDOM_BONUS_TRADE_ITEMS)))
                                .toList();

                        if (allowedBonusTradeItems.size() > 0) {
                            setBonusTradeItem(allowedBonusTradeItems.get(getRandom().nextInt(allowedBonusTradeItems.size())).getDefaultInstance());
                            getBonusTradeItem().grow(BzGeneralConfigs.beeQueenBonusTradeAmountTillSatified);
                        }
                        else {
                            hasTrades = false;
                            setRemainingBonusTradeTime(0);
                        }
                    }
                }

                for (Player player : nearbyPlayers) {
                    Item heldItem = player.getMainHandItem().getItem();
                    if (!this.acknowledgedPlayerHeldItem.containsKey(player.getUUID()) || !this.acknowledgedPlayerHeldItem.get(player.getUUID()).equals(heldItem)) {
                        if ((this.getBonusTradeItem().isEmpty() || !this.getBonusTradeItem().is(heldItem)) &&
                            QueensTradeManager.QUEENS_TRADE_MANAGER.queenTrades.containsKey(heldItem))
                        {
                            player.displayClientMessage(Component.translatable("entity.the_bumblezone.bee_queen.mention_regular_trade_held").withStyle(ChatFormatting.WHITE), true);
                        }
                        this.acknowledgedPlayerHeldItem.put(player.getUUID(), heldItem);
                    }
                }

                if (hasTrades && getBonusTradeItem().isEmpty() && getRemainingBonusTradeTime() > 0) {
                    if (getRemainingBonusTradeTime() > minNotifyTime) {
                        for (Player player : nearbyPlayers) {
                            if (!this.acknowledgedPlayers.contains(player.getUUID())) {
                                player.displayClientMessage(Component.translatable("entity.the_bumblezone.bee_queen.mention_bonus_trade_satisfied").withStyle(ChatFormatting.WHITE), true);
                                this.acknowledgedPlayers.add(player.getUUID());
                            }
                        }
                    }

                    return;
                }

                if (!getBonusTradeItem().isEmpty() && getRemainingBonusTradeTime() >= minNotifyTime) {
                    boolean notifiedAPlayer = false;
                    for (Player player : nearbyPlayers) {
                        if (!this.acknowledgedPlayers.contains(player.getUUID())) {
                            Component itemName = getBonusTradeItem().getHoverName();
                            if (itemName instanceof MutableComponent mutableComponent) {
                                mutableComponent.withStyle(ChatFormatting.YELLOW);
                            }

                            if (player.inventoryMenu.slots.stream().anyMatch(s -> s.getItem().is(getBonusTradeItem().getItem()))) {
                                player.displayClientMessage(Component.translatable("entity.the_bumblezone.bee_queen.mention_bonus_trade_inventory", itemName).withStyle(ChatFormatting.WHITE), true);
                            }
                            else {
                                player.displayClientMessage(Component.translatable("entity.the_bumblezone.bee_queen.mention_bonus_trade", itemName, (getRemainingBonusTradeTime() / minNotifyTime)).withStyle(ChatFormatting.WHITE), true);
                            }

                            notifiedAPlayer = true;
                            this.acknowledgedPlayers.add(player.getUUID());
                        }
                    }
                    if (notifiedAPlayer) {
                        setQueenPose(BeeQueenPose.ITEM_THROW);
                    }
                }
            }
        }
    }

    private void performAngryActions() {
        if (level().getDifficulty() == Difficulty.PEACEFUL && this.getTarget() instanceof Player) {
            this.stopBeingAngry();
            return;
        }

        int beeCooldown = this.getBeeSpawnCooldown();
        if (beeCooldown <= 0 &&
            !this.isImmobile() &&
            this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING))
        {
            this.setBeeSpawnCooldown(this.random.nextInt(50) + 75);

            // Grab a nearby air materialposition a bit away
            BlockPos spawnBlockPos = GeneralUtils.getRandomBlockposWithinRange(this, 5, 0);
            if(!this.level().getBlockState(spawnBlockPos).isAir()) {
                return;
            }

            Bee bee = EntityType.BEE.create(this.level());
            if(bee == null) return;
            ((NeutralMob)bee).setRemainingPersistentAngerTime(this.getRemainingPersistentAngerTime());
            ((NeutralMob)bee).setPersistentAngerTarget(this.getPersistentAngerTarget());
            bee.setTarget(this.getTarget());

            bee.absMoveTo(
                    spawnBlockPos.getX() + 0.5D,
                    spawnBlockPos.getY() + 0.5D,
                    spawnBlockPos.getZ() + 0.5D,
                    this.random.nextFloat() * 360.0F,
                    0.0F);

            bee.finalizeSpawn(
                    (ServerLevel) this.level(),
                    this.level().getCurrentDifficultyAt(spawnBlockPos),
                    MobSpawnType.TRIGGERED,
                    null,
                    null);

            bee.addEffect(new MobEffectInstance(
                    MobEffects.WITHER,
                    Integer.MAX_VALUE,
                    0,
                    true,
                    false,
                    false));

            this.level().addFreshEntity(bee);
            this.spawnAngryParticles(6);
            setQueenPose(BeeQueenPose.ATTACKING);
        }
        else {
            this.setBeeSpawnCooldown(beeCooldown - 1);
        }
    }

    private void performGroundTrades() {
        int throwCooldown = getThrowCooldown();
        if (throwCooldown > 0) {
            setThrowCooldown(throwCooldown - 1);
        }

        if ((this.level().getGameTime() + this.getUUID().getLeastSignificantBits()) % 20 == 0 && throwCooldown <= 0) {
            Vec3 forwardVect = Vec3.directionFromRotation(0, this.getVisualRotationYInDegrees());
            Vec3 sideVect = Vec3.directionFromRotation(0, this.getVisualRotationYInDegrees() - 90);
            AABB scanArea = this.getBoundingBox().deflate(0.45, 0.9, 0.45).move(forwardVect.x() * 0.5d, -0.95, forwardVect.z() * 0.5d);
            List<ItemEntity> items = this.level().getEntitiesOfClass(ItemEntity.class, scanArea);
            items.stream().filter(ie -> !ie.hasPickUpDelay()).findFirst().ifPresent((itemEntity) -> {
                int tradedItems = 0;
                Item item = itemEntity.getItem().getItem();
                if (QueensTradeManager.QUEENS_TRADE_MANAGER.queenTrades.containsKey(item)) {
                    for (int i = 0; i < itemEntity.getItem().getCount(); i++) {
                        Optional<WeightedTradeResult> reward = QueensTradeManager.QUEENS_TRADE_MANAGER.queenTrades.get(item).getRandom(this.random);
                        if (reward.isPresent()) {
                            spawnReward(forwardVect, sideVect, reward.get(), itemEntity.getItem(), ((ItemEntityAccessor)itemEntity).getThrower());
                            tradedItems++;
                        }
                    }
                }

                if (tradedItems > 0) {
                    itemEntity.remove(RemovalReason.DISCARDED);
                }
                else {
                    itemEntity.remove(RemovalReason.DISCARDED);
                    ItemEntity rejectedItemEntity = new ItemEntity(
                            this.level(),
                            this.getX() + (sideVect.x() * 1.75) + (forwardVect.x() * 1),
                            this.getY() + 0.3,
                            this.getZ() + (sideVect.z() * 1.75) + (forwardVect.x() * 1),
                            itemEntity.getItem(),
                            (this.random.nextFloat() - 0.5f) / 10 + forwardVect.x() / 3,
                            0.4f,
                            (this.random.nextFloat() - 0.5f) / 10 + forwardVect.z() / 3);
                    this.level().addFreshEntity(rejectedItemEntity);
                    rejectedItemEntity.setDefaultPickUpDelay();
                    spawnAngryParticles(2);
                    setQueenPose(BeeQueenPose.ITEM_REJECT);
                }

                setThrowCooldown(50);

                if (tradedItems > 0 && itemEntity.getOwner() != null) {
                    if (level().getPlayerByUUID(itemEntity.getOwner().getUUID()) instanceof ServerPlayer serverPlayer) {
                        BzCriterias.BEE_QUEEN_FIRST_TRADE_TRIGGER.trigger(serverPlayer);
                        PlayerDataHandler.onQueenBeeTrade(serverPlayer, tradedItems);

                        if (finalbeeQueenAdvancementDone(serverPlayer)) {
                            ModuleHelper.getModule(serverPlayer, ModuleRegistry.PLAYER_DATA).ifPresent(capability -> {
                                if (!capability.receivedEssencePrize) {
                                    spawnReward(forwardVect, sideVect, ESSENCE_DROP, ItemStack.EMPTY, serverPlayer.getUUID());
                                    capability.receivedEssencePrize = true;
                                    serverPlayer.displayClientMessage(Component.translatable("entity.the_bumblezone.bee_queen.mention_reset").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GOLD), false);
                                }
                            });
                        }
                    }
                }
            });
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.isNoAi()) {
            return InteractionResult.PASS;
        }

        if (this.isAngry() || hand == InteractionHand.OFF_HAND) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = player.getItemInHand(hand);
        Item item = stack.getItem();

        if (stack.isEmpty() && player instanceof ServerPlayer serverPlayer) {
            if (finalbeeQueenAdvancementDone(serverPlayer)) {
                ModuleHelper.getModule(serverPlayer, ModuleRegistry.PLAYER_DATA).ifPresent(capability -> {
                    if (!capability.receivedEssencePrize) {
                        Vec3 forwardVect = Vec3.directionFromRotation(0, this.getVisualRotationYInDegrees());
                        Vec3 sideVect = Vec3.directionFromRotation(0, this.getVisualRotationYInDegrees() - 90);
                        spawnReward(forwardVect, sideVect, ESSENCE_DROP, ItemStack.EMPTY, serverPlayer.getUUID());
                        capability.receivedEssencePrize = true;
                        serverPlayer.displayClientMessage(Component.translatable("entity.the_bumblezone.bee_queen.mention_reset").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GOLD), false);
                    }
                    else {
                        long timeDiff = this.level().getGameTime() - capability.tradeResetPrimedTime;
                        if (timeDiff < 200 && timeDiff > 10) {
                            resetAdvancementTree(serverPlayer, BzCriterias.QUEENS_DESIRE_ROOT_ADVANCEMENT);
                            capability.resetAllTrackerStats();
                            serverPlayer.displayClientMessage(Component.translatable("entity.the_bumblezone.bee_queen.reset_advancements").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GOLD), false);
                        }
                        else {
                            capability.tradeResetPrimedTime = this.level().getGameTime();
                            serverPlayer.displayClientMessage(Component.translatable("entity.the_bumblezone.bee_queen.advancements_warning").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GOLD), false);
                        }
                    }
                });
            }

            return InteractionResult.PASS;
        }

        boolean traded = false;
        if (QueensTradeManager.QUEENS_TRADE_MANAGER.queenTrades.containsKey(item)) {
            if (this.level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            Vec3 forwardVect = Vec3.directionFromRotation(0, this.getVisualRotationYInDegrees());
            Vec3 sideVect = Vec3.directionFromRotation(0, this.getVisualRotationYInDegrees() - 90);

            Optional<WeightedTradeResult> reward = QueensTradeManager.QUEENS_TRADE_MANAGER.queenTrades.get(item).getRandom(this.random);
            if (reward.isPresent()) {
                spawnReward(forwardVect, sideVect, reward.get(), stack, player.getUUID());
                traded = true;
            }
        }

        if (!this.level().isClientSide()) {
            if (!traded) {
                spawnAngryParticles(2);
                setQueenPose(BeeQueenPose.ITEM_REJECT);
            }
            else {
                setThrowCooldown(50);
                stack.shrink(1);
                player.setItemInHand(hand, stack);

                if (player instanceof ServerPlayer serverPlayer) {
                    BzCriterias.BEE_QUEEN_FIRST_TRADE_TRIGGER.trigger(serverPlayer);
                    PlayerDataHandler.onQueenBeeTrade(serverPlayer);

                    if (finalbeeQueenAdvancementDone(serverPlayer)) {
                        ModuleHelper.getModule(serverPlayer, ModuleRegistry.PLAYER_DATA).ifPresent(capability -> {
                            if (!capability.receivedEssencePrize) {
                                Vec3 forwardVect = Vec3.directionFromRotation(0, this.getVisualRotationYInDegrees());
                                Vec3 sideVect = Vec3.directionFromRotation(0, this.getVisualRotationYInDegrees() - 90);
                                spawnReward(forwardVect, sideVect, ESSENCE_DROP, ItemStack.EMPTY, serverPlayer.getUUID());
                                capability.receivedEssencePrize = true;
                                serverPlayer.displayClientMessage(Component.translatable("entity.the_bumblezone.bee_queen.mention_reset").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GOLD), false);
                            }
                        });
                    }
                }

                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }


    private static final ResourceLocation BEE_ESSENCE_ADVANCEMENT_RL = new ResourceLocation(Bumblezone.MODID, "essence/bee_essence_infusion");
    private void resetAdvancementTree(ServerPlayer serverPlayer, ResourceLocation advancementRL) {
        Iterable<Advancement> advancements = serverPlayer.server.getAdvancements().getAdvancement(advancementRL).getChildren();
        for (Advancement advancement : advancements) {
            if (advancement.getId().equals(BEE_ESSENCE_ADVANCEMENT_RL)) {
                continue;
            }

            AdvancementProgress advancementprogress = serverPlayer.getAdvancements().getOrStartProgress(advancement);
            for(String criteria : advancementprogress.getCompletedCriteria()) {
                serverPlayer.getAdvancements().revoke(advancement, criteria);
            }
            resetAdvancementTree(serverPlayer, advancement.getId());
        }
    }

    private static boolean finalbeeQueenAdvancementDone(ServerPlayer serverPlayer) {
        Advancement advancement = serverPlayer.server.getAdvancements().getAdvancement(BzCriterias.QUEENS_DESIRE_FINAL_ADVANCEMENT);
        Map<Advancement, AdvancementProgress> advancementsProgressMap = ((PlayerAdvancementsAccessor)serverPlayer.getAdvancements()).getProgress();
        return advancement != null &&
                advancementsProgressMap.containsKey(advancement) &&
                advancementsProgressMap.get(advancement).isDone();
    }

    private boolean isContainerBlockEntity(ItemStack itemStack) {
        return itemStack.getItem() instanceof BlockItem blockItem &&
                blockItem.getBlock() instanceof EntityBlock block &&
                block.newBlockEntity(this.blockPosition(), blockItem.getBlock().defaultBlockState()) instanceof Container;
    }

    private void spawnReward(Vec3 forwardVect, Vec3 sideVect, WeightedTradeResult reward, ItemStack originalItem, UUID playerUUID) {
        int rewardMultiplier = 1;
        if (getBonusTradeItem().is(originalItem.getItem()) && BzGeneralConfigs.beeQueenBonusTradeRewardMultiplier > 1) {
            rewardMultiplier = BzGeneralConfigs.beeQueenBonusTradeRewardMultiplier;
            getBonusTradeItem().shrink(1);
            if (getBonusTradeItem().isEmpty()) {
                setBonusTradeItem(ItemStack.EMPTY);
            }

            if (playerUUID != null) {
                Player player = level().getPlayerByUUID(playerUUID);
                if (player != null) {
                    if (!getBonusTradeItem().isEmpty()) {
                        player.displayClientMessage(Component.translatable("entity.the_bumblezone.bee_queen.mention_bonus_trade_performed", BzGeneralConfigs.beeQueenBonusTradeRewardMultiplier).withStyle(ChatFormatting.WHITE), true);
                    }
                    else  {
                        this.acknowledgedPlayers.clear();
                        player.displayClientMessage(Component.translatable("entity.the_bumblezone.bee_queen.mention_bonus_trade_satisfied").withStyle(ChatFormatting.WHITE), true);
                        this.acknowledgedPlayers.add(playerUUID);
                    }
                }
            }
        }

        int remainingItemToSpawn = reward.count * rewardMultiplier;
        Item chosenItem = reward.getItems().get(random.nextInt(reward.getItems().size()));
        int itemStackMaxSize = chosenItem.getMaxStackSize();

        while (remainingItemToSpawn > 0) {
            ItemStack rewardItem = chosenItem.getDefaultInstance();
            setQueenPose(BeeQueenPose.ITEM_THROW);

            if (originalItem.is(ItemTags.BANNERS) && rewardItem.is(ItemTags.BANNERS) && originalItem.hasTag()) {
                rewardItem.getOrCreateTag().merge(originalItem.getOrCreateTag());
            }
            else if (originalItem.is(rewardItem.getItem()) && originalItem.hasTag()) {
                rewardItem.getOrCreateTag().merge(originalItem.getOrCreateTag());
            }
            else if (isContainerBlockEntity(originalItem) && isContainerBlockEntity(rewardItem) && originalItem.hasTag()) {
                rewardItem.getOrCreateTag().merge(originalItem.getOrCreateTag());
            }

            int currentItemStackCount = Math.min(remainingItemToSpawn, itemStackMaxSize);
            rewardItem.setCount(currentItemStackCount);
            remainingItemToSpawn -= currentItemStackCount;

            ItemEntity rewardItemEntity = new ItemEntity(
                    this.level(),
                    this.getX() + (sideVect.x() * 0.9d) + (forwardVect.x() * 1),
                    this.getY() + 0.3d,
                    this.getZ() + (sideVect.z() * 0.9d) + (forwardVect.x() * 1),
                    rewardItem,
                    (this.random.nextFloat() - 0.5f) / 10 + forwardVect.x() / 4d,
                    0.3f,
                    (this.random.nextFloat() - 0.5f) / 10 + forwardVect.z() / 4d);
            this.level().addFreshEntity(rewardItemEntity);
            rewardItemEntity.setDefaultPickUpDelay();
            spawnHappyParticles();

            if (reward.xpReward > 0 && this.level() instanceof ServerLevel serverLevel) {
                ExperienceOrb.award(
                        serverLevel,
                        new Vec3(this.getX() + (forwardVect.x() * 1),
                                this.getY() + 0.3,
                                this.getZ() + (forwardVect.x() * 1)),
                        reward.xpReward);
            }
        }

        this.level().playSound(
                null,
                this.blockPosition(),
                BzSounds.BEE_QUEEN_HAPPY.get(),
                SoundSource.NEUTRAL,
                1.0F,
                (this.getRandom().nextFloat() * 0.2F) + 0.6F);
    }

    public void spawnAngryParticles(int particles) {
        if(!this.level().isClientSide()) {
            ((ServerLevel)this.level()).sendParticles(
                    ParticleTypes.ANGRY_VILLAGER,
                    getX(),
                    getY() + 0.45f,
                    getZ(),
                    particles,
                    this.getRandom().nextFloat() - 0.5f,
                    this.getRandom().nextFloat() * 0.4f + 0.4f,
                    this.getRandom().nextFloat() - 0.5f,
                    this.getRandom().nextFloat() * 0.8f + 0.4f);
        }
    }

    private void spawnHappyParticles() {
        ((ServerLevel)this.level()).sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                getX(),
                getY() + 0.75d,
                getZ(),
                5,
                0.8d,
                0.75d,
                0.8d,
                this.getRandom().nextFloat() + 0.5d);
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverWorld, AgeableMob ageableEntity) {
        Bee bee = EntityType.BEE.create(serverWorld);
        bee.setBaby(true);
        return bee;
    }

    @Override
    public int getHeadRotSpeed() {
        return 1;
    }

    @Override
    public int getMaxHeadXRot() {
        return 90;
    }

    public int getThrowCooldown() {
        return this.entityData.get(THROWCOOLDOWN);
    }

    public void setThrowCooldown(Integer cooldown) {
        this.entityData.set(THROWCOOLDOWN, cooldown);
    }

    public int getBeeSpawnCooldown() {
        return this.entityData.get(BEESPAWNCOOLDOWN);
    }

    public void setBeeSpawnCooldown(Integer cooldown) {
        this.entityData.set(BEESPAWNCOOLDOWN, cooldown);
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.entityData.get(REMAINING_ANGER_TIME);
    }

    @Override
    public void setRemainingPersistentAngerTime(int remainingPersistentAngerTime) {
        this.entityData.set(REMAINING_ANGER_TIME, remainingPersistentAngerTime);
    }

    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(UUID uuid) {
        this.persistentAngerTarget = uuid;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    public void stopBeingAngry() {
        NeutralMob.super.stopBeingAngry();
        this.setBeeSpawnCooldown(0);
        this.setTarget(null);
    }

    public int getRemainingBonusTradeTime() {
        return this.entityData.get(REMAINING_BONUS_TRADE_TIME);
    }

    public void setRemainingBonusTradeTime(Integer remainingBonusTradeItem) {
        this.entityData.set(REMAINING_BONUS_TRADE_TIME, remainingBonusTradeItem);
    }

    public ItemStack getBonusTradeItem() {
        return this.entityData.get(BONUS_TRADE_ITEM);
    }

    public void setBonusTradeItem(ItemStack bonusTradeItem) {
        this.entityData.set(BONUS_TRADE_ITEM, bonusTradeItem);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return BzSounds.BEE_QUEEN_LOOP.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return BzSounds.BEE_QUEEN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return BzSounds.BEE_QUEEN_DEATH.get();
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.5f * this.getEyeHeight(), this.getBbWidth() * 0.2f);
    }

    public static class DirectPathNavigator extends GroundPathNavigation {

        private final Mob mob;

        public DirectPathNavigator(Mob mob, Level world) {
            super(mob, world);
            this.mob = mob;
        }

        @Override
        public void tick() {
            ++this.tick;
        }

        @Override
        public boolean moveTo(double x, double y, double z, double speedIn) {
            mob.getMoveControl().setWantedPosition(x, y, z, speedIn);
            return true;
        }

        @Override
        public boolean moveTo(Entity entityIn, double speedIn) {
            mob.getMoveControl().setWantedPosition(entityIn.getX(), entityIn.getY(), entityIn.getZ(), speedIn);
            return true;
        }
    }
}
