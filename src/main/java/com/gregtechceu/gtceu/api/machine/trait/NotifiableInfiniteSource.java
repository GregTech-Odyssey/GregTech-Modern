package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.handler.PlanScratch;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.InfiniteKeySource;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.recipesearch.IntLongMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 无限源机器 trait：配置槽里的 key 视为无限供给，作为配方输入时可满足任意数量且不扣减。
 */
public class NotifiableInfiniteSource<K extends AEKey> extends NotifiableContentHandler implements IRecipeHandler, ICapabilityTrait, IKeyHandler<K> {

    @Getter
    public final IO capabilityIO;
    public final KeyInventory<K> config;
    @SaveToDisk(key = "config")
    @SuppressWarnings("rawtypes")
    private final KeyInventory persistedConfig;
    private final InfiniteKeySource<K> source;

    public NotifiableInfiniteSource(MetaMachine machine, KeyInventory<K> config, IO handlerIO, IO capabilityIO, boolean acceptInsert) {
        super(machine, handlerIO);
        this.config = config;
        this.persistedConfig = config;
        this.capabilityIO = capabilityIO;
        this.source = new InfiniteKeySource<>(config, acceptInsert);
        config.setOnChanged(this::onContentsChanged);
    }

    public InfiniteKeySource<K> source() {
        return source;
    }

    @Override
    public boolean handlesItems() {
        return config.keyType() == AEKeyTypes.ITEMS;
    }

    @Override
    public boolean handlesFluids() {
        return config.keyType() == AEKeyTypes.FLUIDS;
    }

    @Override
    public long available(AEKeyType type, KeyIngredient ingredient) {
        var c = config;
        int size = c.size();
        for (int i = 0; i < size; i++) {
            if (c.amountAt(i) > 0 && KeyIngredient.accepts(ingredient, c.uidAt(i), c.rawKeyAt(i))) return Long.MAX_VALUE;
        }
        return 0;
    }

    @Override
    public long reserveInput(PlanScratch plan, int member, AEKeyType type, int entry, KeyIngredient ingredient, long need, boolean consume) {
        return available(type, ingredient) > 0 ? need : 0;
    }

    @Override
    public boolean forEachKey(AEKeyType type, KeyVisitor visitor) {
        if (type != config.keyType()) return false;
        for (int i = 0; i < config.size(); i++) {
            if (config.amountAt(i) > 0 && visitor.visit(config.rawKeyAt(i), Long.MAX_VALUE)) return true;
        }
        return false;
    }

    @Override
    public void fillSearchMap(@NotNull GTRecipeType type, @NotNull IntLongMap map) {
        for (int i = 0; i < config.size(); i++) {
            if (config.amountAt(i) > 0) type.convertKey(config.rawKeyAt(i), Integer.MAX_VALUE, map);
        }
    }

    @Override
    public boolean updateEmpty() {
        return config.isEmpty();
    }

    @Override
    public AEKeyType keyType() {
        return config.keyType();
    }

    @Override
    public int size() {
        return source.size();
    }

    @Override
    public boolean fixedSize() {
        return source.fixedSize();
    }

    @Override
    public @Nullable K keyAt(int slot) {
        return source.keyAt(slot);
    }

    @Override
    public long amountAt(int slot) {
        return source.amountAt(slot);
    }

    @Override
    public long slotLimit(int slot) {
        return source.slotLimit(slot);
    }

    @Override
    public long insert(int slot, K key, long amount, boolean simulate) {
        return canCapInput() ? source.insert(slot, key, amount, simulate) : 0;
    }

    @Override
    public long extract(int slot, K key, long amount, boolean simulate) {
        return canCapOutput() ? source.extract(slot, key, amount, simulate) : 0;
    }

    @Override
    public long insert(K key, long amount, boolean simulate) {
        return canCapInput() ? source.insert(key, amount, simulate) : 0;
    }

    @Override
    public long extract(K key, long amount, boolean simulate) {
        return canCapOutput() ? source.extract(key, amount, simulate) : 0;
    }
}
