package com.gregtechceu.gtceu.uipro.flow;

import com.gregtechceu.gtceu.uipro.data.SyncValue;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class FlowLink {

    private final FlowChart chart;
    final FlowNode from;
    final FlowNode to;
    private FlowNode follow;
    @Nullable
    private SyncValue<Integer> explicit;

    FlowLink(FlowChart chart, FlowNode from, FlowNode to) {
        this.chart = chart;
        this.from = from;
        this.to = to;
        this.follow = from;
    }

    public FlowLink follow(FlowNode node) {
        this.follow = node;
        return this;
    }

    public FlowLink bindState(Supplier<FlowState> state) {
        this.explicit = chart.addSyncValue(SyncValue.ofInt(() -> state.get().ordinal(), FlowState.IDLE.ordinal()));
        return this;
    }

    public FlowNode getFrom() {
        return from;
    }

    public FlowNode getTo() {
        return to;
    }

    public FlowState getFlowState() {
        return explicit != null ? FlowState.of(explicit.getValue()) : follow.getFlowState();
    }
}
