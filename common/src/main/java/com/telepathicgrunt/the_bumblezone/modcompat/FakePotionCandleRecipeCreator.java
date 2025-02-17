package com.telepathicgrunt.the_bumblezone.modcompat;

import com.telepathicgrunt.the_bumblezone.Bumblezone;
import com.telepathicgrunt.the_bumblezone.items.recipes.PotionCandleRecipe;
import com.telepathicgrunt.the_bumblezone.modinit.BzTags;
import com.telepathicgrunt.the_bumblezone.utils.GeneralUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class FakePotionCandleRecipeCreator {

    public static List<CraftingRecipe> constructFakeRecipes(PotionCandleRecipe potionCandleRecipe, boolean oneRecipeOnly) {
        List<CraftingRecipe> extraRecipes = new ArrayList<>();
        int currentRecipe = 0;
        Set<MobEffect> effects = new HashSet<>();
        List<Potion> potions = new ArrayList<>();
        for (Potion potion : BuiltInRegistries.POTION) {
            if (oneRecipeOnly && potions.size() > 0) {
                break;
            }

            if (potion.getEffects().stream().allMatch(e -> effects.contains(e.getEffect()) || BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(BuiltInRegistries.MOB_EFFECT.getResourceKey(e.getEffect()).orElseThrow()).is(BzTags.DISALLOWED_POTION_CANDLE_EFFECTS))) {
                continue;
            }

            potion.getEffects().forEach(e -> effects.add(e.getEffect()));
            potions.add(potion);
        }
        potions.sort(Comparator.comparingInt(a -> a.getEffects().size()));
        for (Potion potion : potions) {
            if (potion.getEffects().stream().allMatch(e -> GeneralUtils.isInTag(BuiltInRegistries.MOB_EFFECT, BzTags.DISALLOWED_POTION_CANDLE_EFFECTS, e.getEffect()))) {
                continue;
            }

            addRecipeIfValid(extraRecipes, FakePotionCandleRecipeCreator.getFakeShapedRecipe(potionCandleRecipe, potion, Items.POTION.getDefaultInstance(), currentRecipe));
            currentRecipe++;
            addRecipeIfValid(extraRecipes, FakePotionCandleRecipeCreator.getFakeShapedRecipe(potionCandleRecipe, potion, Items.SPLASH_POTION.getDefaultInstance(), currentRecipe));
            currentRecipe++;
            addRecipeIfValid(extraRecipes, FakePotionCandleRecipeCreator.getFakeShapedRecipe(potionCandleRecipe, potion, Items.LINGERING_POTION.getDefaultInstance(), currentRecipe));
            currentRecipe++;
        }
        return extraRecipes;
    }

    private static void addRecipeIfValid(List<CraftingRecipe> extraRecipes, ShapedRecipe recipe) {
        if (!recipe.getResultItem(RegistryAccess.EMPTY).isEmpty()) {
            extraRecipes.add(recipe);
        }
    }

    private static ShapedRecipe getFakeShapedRecipe(PotionCandleRecipe recipe, Potion potion, ItemStack potionItem, int currentRecipe) {
        ItemStack potionStack = PotionUtils.setPotion(potionItem, potion);

        List<Ingredient> fakedShapedIngredientsMutable = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            fakedShapedIngredientsMutable.add(Ingredient.EMPTY);
        }

        int currentShapedIndex = 0;
        int shapedRecipeSize = recipe.getShapedRecipeItems().size();
        for (int x = 0; x < recipe.getWidth(); x++) {
            for (int z = 0; z < recipe.getHeight(); z++) {
                if (currentShapedIndex >= shapedRecipeSize) {
                    continue;
                }

                Ingredient ingredient = recipe.getShapedRecipeItems().get(currentShapedIndex);
                fakedShapedIngredientsMutable.set(x + (z * 3), ingredient);
                currentShapedIndex++;
            }
        }

        int currentShapelessIndex = 0;
        int shapelessRecipeSize = recipe.getShapelessRecipeItems().size();
        for (int i = 0; i < 9; i++) {
            Ingredient ingredient = fakedShapedIngredientsMutable.get(i);
            if (ingredient.isEmpty()) {
                if (currentShapelessIndex >= shapelessRecipeSize) {
                    fakedShapedIngredientsMutable.set(i, Ingredient.of(potionStack));
                    break;
                }

                fakedShapedIngredientsMutable.set(i, recipe.getShapelessRecipeItems().get(currentShapelessIndex));
                currentShapelessIndex++;
            }
        }

        NonNullList<Ingredient> fakedShapedIngredients = NonNullList.create();
        fakedShapedIngredients.addAll(fakedShapedIngredientsMutable);

        return new ShapedRecipe(
                new ResourceLocation(Bumblezone.MODID, recipe.getId().getPath() + "_" + currentRecipe),
                Bumblezone.MODID,
                CraftingBookCategory.MISC,
                3,
                3,
                fakedShapedIngredients,
                createResultStack(recipe, potionStack)
        );
    }

    private static ItemStack createResultStack(PotionCandleRecipe recipe, ItemStack potionStack) {
        List<MobEffect> effects = new ArrayList<>();
        AtomicInteger maxDuration = new AtomicInteger();
        AtomicInteger amplifier = new AtomicInteger();
        AtomicInteger potionEffectsFound = new AtomicInteger();

        PotionUtils.getMobEffects(potionStack).forEach(me -> {
            effects.add(me.getEffect());
            maxDuration.addAndGet(me.getEffect().isInstantenous() ? 200 : me.getDuration());
            amplifier.addAndGet(me.getAmplifier() + 1);
            potionEffectsFound.getAndIncrement();
        });

        if (effects.isEmpty()) {
            return ItemStack.EMPTY;
        }

        HashSet<MobEffect> setPicker = new HashSet<>(effects);
        MobEffect chosenEffect = setPicker.stream().toList().get(new Random().nextInt(setPicker.size()));
        if (chosenEffect == null) {
            return ItemStack.EMPTY;
        }

        PotionCandleRecipe.balanceStats(chosenEffect, maxDuration, amplifier, potionEffectsFound);
        amplifier.set(Math.min(amplifier.get(), recipe.getMaxLevelCap()));

        return PotionCandleRecipe.createTaggedPotionCandle(
                chosenEffect,
                maxDuration,
                amplifier,
                potionStack.getItem() instanceof SplashPotionItem ? 1 : 0,
                potionStack.getItem() instanceof LingeringPotionItem ? 1 : 0,
                recipe.getResultItem(RegistryAccess.EMPTY).getCount());
    }
}
