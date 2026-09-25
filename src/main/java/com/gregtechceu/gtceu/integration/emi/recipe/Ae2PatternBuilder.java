package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderModel;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.items.PatternEncodingTermMenu;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Ae2PatternBuilder {

    private static final long BUCKET = 1000;
    private static final PatternBuilderModel.Stock NONE = new PatternBuilderModel.Stock(0, false);

    private Ae2PatternBuilder() {}

    public static void open(PatternEncodingTermMenu menu, PatternBuilderModel.Builder builder, Component title,
                            List<GenericStack> outputs, Runnable beforeEncode) {
        open(menu, menu.getClientRepo(), menu.getProcessingInputSlots().length, builder, title, inputs -> {
            beforeEncode.run();
            EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs);
        });
    }

    public static void open(AbstractContainerMenu menu, @Nullable IClientRepo repo, int inputLimit, PatternBuilderModel.Builder builder,
                            Component title, Consumer<List<List<GenericStack>>> encoder) {
        var stock = stockIndex(repo);
        var model = builder.build(stack -> stock == null ? null : stock.getOrDefault(stockKey(stack), NONE));
        PatternBuilderScreen.open(model, AEItems.BLANK_PATTERN.stack(), title, inputLimit, () -> {
            var player = Minecraft.getInstance().player;
            if (player == null || player.containerMenu != menu) return;
            encoder.accept(inputs(model));
        });
    }

    private static List<List<GenericStack>> inputs(PatternBuilderModel model) {
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
            index.merge(key, new PatternBuilderModel.Stock(stored, entry.isCraftable()),
                    (a, b) -> new PatternBuilderModel.Stock(a.stored() + b.stored(), a.craftable() || b.craftable()));
        }
        for (var craftable : repo.getCraftableKeys()) {
            Object key;
            if (craftable instanceof AEItemKey item) key = item.getItem();
            else if (craftable instanceof AEFluidKey fluid) key = fluid.getFluid();
            else continue;
            index.merge(key, new PatternBuilderModel.Stock(0, true), (a, b) -> new PatternBuilderModel.Stock(a.stored(), true));
        }
        return index;
    }
}
