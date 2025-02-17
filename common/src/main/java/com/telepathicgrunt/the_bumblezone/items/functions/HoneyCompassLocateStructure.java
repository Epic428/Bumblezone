package com.telepathicgrunt.the_bumblezone.items.functions;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.telepathicgrunt.the_bumblezone.items.HoneyCompass;
import com.telepathicgrunt.the_bumblezone.modinit.BzItems;
import com.telepathicgrunt.the_bumblezone.modinit.BzLootFunctionTypes;
import com.telepathicgrunt.the_bumblezone.modinit.BzTags;
import com.telepathicgrunt.the_bumblezone.utils.ThreadExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HoneyCompassLocateStructure extends LootItemConditionalFunction {
    public static final int DEFAULT_SEARCH_RADIUS = 50;
    public static final boolean DEFAULT_SKIP_EXISTING = true;
    final TagKey<Structure> destination;
    final int searchRadius;
    final boolean skipKnownStructures;

    public HoneyCompassLocateStructure(LootItemCondition[] lootItemConditions, TagKey<Structure> destination, int searchRadius, boolean skipKnownStructrues) {
        super(lootItemConditions);
        this.destination = destination;
        this.searchRadius = searchRadius;
        this.skipKnownStructures = skipKnownStructrues;
    }

    @Override
    public LootItemFunctionType getType() {
        return BzLootFunctionTypes.HONEY_COMPASS_LOCATE_STRUCTURE.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of(LootContextParams.ORIGIN);
    }

    @Override
    public ItemStack run(ItemStack itemStack, LootContext lootContext) {
        if (itemStack.is(BzItems.HONEY_COMPASS.get())) {
            Vec3 vec3 = lootContext.getParamOrNull(LootContextParams.ORIGIN);
            if (vec3 != null) {
                UUID searchId = UUID.randomUUID();
                BlockPos blockPos = BlockPos.containing(vec3);

                itemStack.getOrCreateTag().putBoolean(HoneyCompass.TAG_LOADING, true);
                HoneyCompass.setStructureTags(itemStack.getOrCreateTag(), destination);
                HoneyCompass.setSearchId(itemStack.getOrCreateTag(), searchId);

                ResourceKey<Structure> structure = null;
                Registry<Structure> structureRegistry = lootContext.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
                List<Structure> structuresList = structureRegistry
                        .getTag(destination)
                        .map(holders -> holders
                            .stream()
                            .map(Holder::value)
                            .toList()
                        ).orElseGet(ArrayList::new);

                if (!structuresList.isEmpty()) {
                    structure = structureRegistry.getResourceKey(structuresList.get(lootContext.getRandom().nextInt(structuresList.size()))).get();
                }

                WeakReference<ItemStack> weakRefItemStack = new WeakReference<>(itemStack);
                if (structure != null) {
                    ThreadExecutor.locate(lootContext.getLevel(), searchId, structure, blockPos, 100, false)
                            .thenOnServerThread(foundPos -> setCompassFoundLocationData(weakRefItemStack, lootContext, foundPos));
                }
            }
            else if (itemStack.hasTag()) {
                itemStack.setTag(new CompoundTag());
            }
        }
        return itemStack;
    }

    private void setCompassFoundLocationData(WeakReference<ItemStack> itemStackWeakReference, LootContext lootContext, BlockPos blockPos) {
        ItemStack itemStack = itemStackWeakReference.get();
        if (itemStack != null && blockPos != null) {
            HoneyCompass.addFoundStructureLocation(lootContext.getLevel().dimension(), blockPos, itemStack.getOrCreateTag());
        }
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<HoneyCompassLocateStructure> {
        public void serialize(JsonObject jsonObject, HoneyCompassLocateStructure honeyCompassLocateStructure, JsonSerializationContext context) {
            super.serialize(jsonObject, honeyCompassLocateStructure, context);
            if (!honeyCompassLocateStructure.destination.equals(ExplorationMapFunction.DEFAULT_DESTINATION)) {
                jsonObject.addProperty("destination", honeyCompassLocateStructure.destination.location().toString());
            }

            if (honeyCompassLocateStructure.searchRadius != DEFAULT_SEARCH_RADIUS) {
                jsonObject.addProperty("search_radius", honeyCompassLocateStructure.searchRadius);
            }

            if (!honeyCompassLocateStructure.skipKnownStructures) {
                jsonObject.addProperty("skip_existing_chunks", honeyCompassLocateStructure.skipKnownStructures);
            }
        }

        public HoneyCompassLocateStructure deserialize(JsonObject jsonObject, JsonDeserializationContext context, LootItemCondition[] lootItemConditions) {
            TagKey<Structure> tagkey = readStructure(jsonObject);
            int searchRadiusInt = GsonHelper.getAsInt(jsonObject, "search_radius", DEFAULT_SEARCH_RADIUS);
            boolean skipExistingChunksBool = GsonHelper.getAsBoolean(jsonObject, "skip_existing_chunks", DEFAULT_SKIP_EXISTING);
            return new HoneyCompassLocateStructure(lootItemConditions, tagkey, searchRadiusInt, skipExistingChunksBool);
        }

        private static TagKey<Structure> readStructure(JsonObject jsonObject) {
            if (jsonObject.has("destination")) {
                String s = GsonHelper.getAsString(jsonObject, "destination");
                return TagKey.create(Registries.STRUCTURE, new ResourceLocation(s));
            }
            else {
                return BzTags.HONEY_COMPASS_DEFAULT_LOCATING;
            }
        }
    }
}