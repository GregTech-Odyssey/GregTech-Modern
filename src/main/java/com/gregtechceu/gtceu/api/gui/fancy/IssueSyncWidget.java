package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.issue.IssueSnapshot;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.UIChannel;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class IssueSyncWidget extends Widget implements UIChannel.Host {

    private final UIChannel channel = new UIChannel(this);
    private final List<RecipeLogic> logics;
    private final List<SyncValue<IssueSnapshot>> values;

    public IssueSyncWidget(List<RecipeLogic> logics) {
        super(0, 0, 0, 0);
        this.logics = List.copyOf(logics);
        this.values = new ArrayList<>(this.logics.size());
        for (var logic : this.logics) {
            values.add(channel.addSyncValue(SyncValue.of(logic::getIssueSnapshot, IssueSnapshot.STREAM_CODEC, IssueSnapshot.EMPTY)));
        }
    }

    @Nullable
    public static IssueSyncWidget of(@Nullable MetaMachine machine) {
        if (machine == null) return null;
        var logics = logicsOf(machine);
        return logics.isEmpty() ? null : new IssueSyncWidget(logics);
    }

    public static List<RecipeLogic> logicsOf(MetaMachine machine) {
        RecipeLogic main = machine instanceof IRecipeLogicMachine recipeLogicMachine ? recipeLogicMachine.getRecipeLogic() : null;
        var list = new ArrayList<RecipeLogic>(1);
        if (main != null) list.add(main);
        for (var trait : machine.getTraits()) {
            if (trait instanceof RecipeLogic logic && logic != main) list.add(logic);
        }
        return list;
    }

    @Nullable
    public Supplier<IssueSnapshot> source(RecipeLogic logic) {
        int index = logics.indexOf(logic);
        return index < 0 ? null : values.get(index)::getValue;
    }

    @Override
    public UIChannel getChannel() {
        return channel;
    }

    @Override
    public void initWidget() {
        super.initWidget();
        channel.prime();
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (!channel.handleClientAction(id, buffer)) super.handleClientAction(id, buffer);
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        channel.writeInitialData(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        channel.readInitialData(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        channel.detectAndSendChanges();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateScreen() {
        super.updateScreen();
        channel.pollClient();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (!channel.readUpdateInfo(id, buffer)) super.readUpdateInfo(id, buffer);
    }
}
