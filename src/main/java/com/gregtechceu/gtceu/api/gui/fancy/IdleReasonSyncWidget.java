package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.UIChannel;
import com.gregtechceu.gtceu.uiwidgets.icon.IdleReasonInfo;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class IdleReasonSyncWidget extends Widget implements UIChannel.Host {

    private final UIChannel channel = new UIChannel(this);
    private final List<RecipeLogic> logics;
    private final List<State> states;

    public IdleReasonSyncWidget(List<RecipeLogic> logics) {
        super(0, 0, 0, 0);
        this.logics = List.copyOf(logics);
        this.states = new ArrayList<>(this.logics.size());
        for (var logic : this.logics) states.add(new State(channel, logic));
    }

    @Nullable
    public static IdleReasonSyncWidget of(@Nullable MetaMachine machine) {
        if (machine == null) return null;
        var logics = logicsOf(machine);
        return logics.isEmpty() ? null : new IdleReasonSyncWidget(logics);
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
    public State state(RecipeLogic logic) {
        int index = logics.indexOf(logic);
        return index < 0 ? null : states.get(index);
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

    public static final class State {

        private final SyncValue<Integer> status;
        private final SyncValue<Boolean> available;
        private final SyncValue<Component> reason;

        private State(UIChannel channel, RecipeLogic logic) {
            this.status = channel.addSyncValue(SyncValue.ofInt(logic::getStatus, RecipeLogic.IDLE));
            this.available = channel.addSyncValue(SyncValue.ofBool(() -> IdleReasonInfo.available(logic), true));
            this.reason = channel.addSyncValue(SyncValue.ofComponent(() -> {
                var current = IdleReasonInfo.reasonOf(logic);
                return current == null ? IdleReasonInfo.NONE : current;
            }, IdleReasonInfo.NONE));
        }

        public int getStatus() {
            return status.getValue();
        }

        public boolean isAvailable() {
            return available.getValue();
        }

        @Nullable
        public Component getReason() {
            var value = reason.getValue();
            return IdleReasonInfo.isNone(value) ? null : value;
        }
    }
}
