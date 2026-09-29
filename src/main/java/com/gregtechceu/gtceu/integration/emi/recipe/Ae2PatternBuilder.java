package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.uiwidgets.patternbuilder.CarriedStock;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderModel;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.common.MEStorageMenu;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Ae2PatternBuilder {

    private static final long BUCKET = 1000;

    private Ae2PatternBuilder() {}

    public static PatternBuilderModel model(AbstractContainerMenu menu, @Nullable IClientRepo repo, PatternBuilderModel.Builder builder) {
        var stock = menu instanceof MEStorageMenu storage && !storage.isPowered() ? null : stockIndex(repo);
        boolean carried = CarriedStock.request();
        return builder.build(stack -> {
            var entry = stock == null ? null : stock.get(stockKey(stack));
            long have = carried ? CarriedStock.get(stack.getItem()) : -1;
            if (stock == null && !carried) return null;
            return new PatternBuilderModel.Stock(stock == null ? -1 : entry == null ? 0 : entry.stored(), entry != null && entry.craftable(), have);
        });
    }

    public static List<List<GenericStack>> inputs(PatternBuilderModel model) {
        var inputs = new ArrayList<List<GenericStack>>();
        for (var input : model.getInputs()) {
            var stack = input.stack();
            if (stack.getItem() instanceof BucketItem bucket && bucket.getFluid() != Fluids.EMPTY) {
                inputs.add(List.of(new GenericStack(AEFluidKey.of(bucket.getFluid()), input.count() * BUCKET)));
            } else {
                var key = AEItemKey.of(stack);
                if (key != null) inputs.add(List.of(new GenericStack(key, input.count())));
            }
        }
        return inputs;
    }

    private static Object stockKey(ItemStack stack) {
        if (stack.getItem() instanceof BucketItem bucket && bucket.getFluid() != Fluids.EMPTY) return bucket.getFluid();
        return stack.getItem();
    }

    @Nullable
    private static Reference2ObjectOpenHashMap<Object, PatternBuilderModel.Stock> stockIndex(@Nullable IClientRepo repo) {
        if (repo == null) return null;
        var index = new Reference2ObjectOpenHashMap<Object, PatternBuilderModel.Stock>();
        for (var entry : repo.getAllEntries()) {
            Object key;
            long stored = entry.getStoredAmount();
            if (entry.getWhat() instanceof AEItemKey item) {
                key = item.getItem();
            } else if (entry.getWhat() instanceof AEFluidKey fluid) {
                key = fluid.getFluid();
                stored /= BUCKET;
            } else {
                continue;
            }
            index.merge(key, new PatternBuilderModel.Stock(stored, entry.isCraftable(), -1),
                    (a, b) -> new PatternBuilderModel.Stock(a.stored() + b.stored(), a.craftable() || b.craftable(), -1));
        }
        for (var craftable : repo.getCraftableKeys()) {
            Object key;
            if (craftable instanceof AEItemKey item) key = item.getItem();
            else if (craftable instanceof AEFluidKey fluid) key = fluid.getFluid();
            else continue;
            index.merge(key, new PatternBuilderModel.Stock(0, true, -1), (a, b) -> new PatternBuilderModel.Stock(a.stored(), true, -1));
        }
        return index;
    }
}
