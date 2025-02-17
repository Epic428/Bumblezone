package com.telepathicgrunt.the_bumblezone.fabricbase;

import com.telepathicgrunt.the_bumblezone.events.AddCreativeTabEntriesEvent;
import com.telepathicgrunt.the_bumblezone.events.lifecycle.FinalSetupEvent;
import com.telepathicgrunt.the_bumblezone.events.lifecycle.RegisterEntityAttributesEvent;
import com.telepathicgrunt.the_bumblezone.events.lifecycle.SetupEvent;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.IdentityHashMap;
import java.util.Map;

public class FabricBaseEventManager {

    private static final Map<ResourceKey<CreativeModeTab>, AddCreativeTabEntriesEvent.Type> TYPES = Util.make(new IdentityHashMap<>(), map -> {
        map.put(CreativeModeTabs.BUILDING_BLOCKS, AddCreativeTabEntriesEvent.Type.BUILDING);
        map.put(CreativeModeTabs.COLORED_BLOCKS, AddCreativeTabEntriesEvent.Type.COLORED);
        map.put(CreativeModeTabs.NATURAL_BLOCKS, AddCreativeTabEntriesEvent.Type.NATURAL);
        map.put(CreativeModeTabs.FUNCTIONAL_BLOCKS, AddCreativeTabEntriesEvent.Type.FUNCTIONAL);
        map.put(CreativeModeTabs.REDSTONE_BLOCKS, AddCreativeTabEntriesEvent.Type.REDSTONE);
        map.put(CreativeModeTabs.TOOLS_AND_UTILITIES, AddCreativeTabEntriesEvent.Type.TOOLS);
        map.put(CreativeModeTabs.COMBAT, AddCreativeTabEntriesEvent.Type.COMBAT);
        map.put(CreativeModeTabs.FOOD_AND_DRINKS, AddCreativeTabEntriesEvent.Type.FOOD);
        map.put(CreativeModeTabs.INGREDIENTS, AddCreativeTabEntriesEvent.Type.INGREDIENTS);
        map.put(CreativeModeTabs.SPAWN_EGGS, AddCreativeTabEntriesEvent.Type.SPAWN_EGGS);
        map.put(CreativeModeTabs.OP_BLOCKS, AddCreativeTabEntriesEvent.Type.OPERATOR);
    });

    public static void init() {
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register((tab, entries) ->
                AddCreativeTabEntriesEvent.EVENT.invoke(new AddCreativeTabEntriesEvent(
                        TYPES.getOrDefault(BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab).orElse(null), AddCreativeTabEntriesEvent.Type.CUSTOM),
                        tab,
                        entries.shouldShowOpRestrictedItems(),
                        entries::accept)));

        RegisterEntityAttributesEvent.EVENT.invoke(new RegisterEntityAttributesEvent(FabricDefaultAttributeRegistry::register));
        SetupEvent.EVENT.invoke(new SetupEvent(Runnable::run));
        FinalSetupEvent.EVENT.invoke(new FinalSetupEvent(Runnable::run));
    }
}
