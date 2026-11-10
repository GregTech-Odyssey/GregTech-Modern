package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.Direction;

import java.util.SplittableRandom;

/** Standalone conservation checks; run with gradlew energyBufferCheck. */
public final class EnergyBufferCheck {

    public static void main(String[] args) {
        equal(0, IBufferedEnergyMachine.bufferCapacity(0), "zero voltage capacity");
        equal(320, IBufferedEnergyMachine.bufferCapacity(32), "ten-voltage reserve");
        equal(Long.MAX_VALUE, IBufferedEnergyMachine.bufferCapacity(Long.MAX_VALUE), "capacity overflow");
        var virtual = new VirtualMachine();
        check(!virtual.isEnergyBufferEnabled(), "virtual supply must opt out");
        check(virtual.useEnergy(32, false), "virtual supply failed");
        equal(0, virtual.getEnergyBuffer(), "virtual resource acquired duplicate EU reserve");
        var machine = new Machine(1000, 320);
        check(machine.useEnergy(32, true), "simulation must see supply");
        equal(0, machine.energyBuffer, "simulation changed reserve");
        equal(0, machine.source.changes, "simulation touched supply");
        check(machine.useEnergy(32, false), "initial use failed");
        equal(288, machine.energyBuffer, "consumption must prefetch reserve");
        equal(680, machine.source.stored, "prefetch must debit supply");
        machine.source.stored = 0;
        for (int i = 0; i < 9; i++) check(machine.useEnergy(32, false), "reserve stopped early");
        check(!machine.useEnergy(1, false), "empty machine ran for free");
        equal(0, machine.energyBuffer, "reserve not exhausted");

        machine = new Machine(40, 320);
        machine.source.limit = 7;
        check(!machine.useEnergy(10, false), "partial transfer invented energy");
        equal(7, machine.energyBuffer, "failed request lost transferred reserve");
        equal(33, machine.source.stored, "partial source debit wrong");
        check(machine.useEnergy(10, false), "partial refill cannot finish request");
        equal(4, machine.energyBuffer, "partial refill accounting wrong");

        machine = new Machine(1000, 32);
        check(machine.useEnergy(500, false), "request larger than reserve capacity failed");
        equal(500, machine.source.stored, "large request debit wrong");
        equal(0, machine.energyBuffer, "large request residue wrong");
        machine.energyBuffer = 100;
        machine.capacity = 0;
        check(machine.useEnergy(70, false), "capacity shrink discarded reserve");
        equal(30, machine.energyBuffer, "shrunk reserve debit wrong");
        check(machine.useEnergy(40, false), "zero-capacity refill failed");
        equal(490, machine.source.stored, "zero-capacity fallback debit wrong");

        machine = new Machine(Long.MAX_VALUE, Long.MAX_VALUE);
        machine.energyBuffer = Long.MAX_VALUE - 2;
        check(machine.useEnergy(Long.MAX_VALUE, true), "simulation sum overflowed");
        check(machine.useEnergy(Long.MAX_VALUE, false), "max request failed");
        equal(0, machine.energyBuffer, "max request overflowed reserve");
        equal(Long.MAX_VALUE - 2, machine.source.stored, "max request source debit wrong");
        check(machine.useEnergy(0, false), "zero request failed");
        equal(1, machine.source.changes, "zero request refilled reserve");
        check(machine.useEnergy(-8, false), "generator path failed");
        equal(8, machine.generated, "generation did not delegate");
        equal(0, machine.energyBuffer, "generation changed reserve");

        machine = new Machine(1000, 320);
        machine.enabled = false;
        check(machine.useEnergy(32, false), "disabled buffer path failed");
        equal(968, machine.source.stored, "disabled path prefetched");
        equal(0, machine.energyBuffer, "disabled path created EU reserve");

        var random = new SplittableRandom(20261110);
        long consumed = 0;
        machine = new Machine(1_000_000, 320);
        for (int i = 0; i < 20_000; i++) {
            machine.capacity = random.nextInt(640);
            machine.source.limit = random.nextInt(1000);
            long request = random.nextInt(1200);
            long before = machine.source.stored + machine.energyBuffer;
            boolean simulated = machine.useEnergy(request, true);
            equal(before, machine.source.stored + machine.energyBuffer, "random simulation mutated storage");
            check(simulated == (before >= request), "random simulation wrong");
            if (machine.useEnergy(request, false)) consumed += request;
            equal(1_000_000, machine.source.stored + machine.energyBuffer + consumed, "random conservation failed");
        }
        System.out.println("EnergyBufferCheck PASS: edge cases and 20000 conservation steps");
    }

    private static void equal(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class Machine implements IBufferedEnergyMachine {

        final Source source;
        long energyBuffer, capacity, generated;
        boolean enabled = true;

        Machine(long supplied, long capacity) {
            source = new Source(supplied);
            this.capacity = capacity;
        }

        public long getEnergyBuffer() {
            return energyBuffer;
        }

        public void setEnergyBuffer(long value) {
            energyBuffer = value;
        }

        public long getEnergyBufferCapacity() {
            return capacity;
        }

        public boolean isEnergyBufferEnabled() {
            return enabled;
        }

        public IEnergyContainer getEnergyContainer() {
            return source;
        }

        public MetaMachine self() {
            return null;
        }

        public boolean generateEnergy(long eu, boolean simulate) {
            if (!simulate) generated += eu;
            return true;
        }
    }

    private static final class VirtualMachine implements IBufferedEnergyMachine, IDummyEnergyMachine {

        private final DummyContainer source = new DummyContainer(320);
        private long buffer;

        public long getEnergyBuffer() {
            return buffer;
        }

        public void setEnergyBuffer(long energy) {
            buffer = energy;
        }

        public IEnergyContainer getEnergyContainer() {
            return source;
        }

        public MetaMachine self() {
            return null;
        }
    }

    private static final class Source implements IEnergyContainer {

        long stored, limit = Long.MAX_VALUE;
        int changes;

        Source(long stored) {
            this.stored = stored;
        }

        public long changeEnergy(long amount) {
            changes++;
            long changed = amount < 0 ? -Math.min(-amount, Math.min(stored, limit)) : Math.min(amount, Long.MAX_VALUE - stored);
            stored += changed;
            return changed;
        }

        public long getEnergyStored() {
            return stored;
        }

        public long getEnergyCapacity() {
            return Long.MAX_VALUE;
        }

        public long getInputVoltage() {
            return 32;
        }

        public long getInputAmperage() {
            return 1;
        }

        public boolean inputsEnergy(Direction side) {
            return true;
        }

        public long acceptEnergyFromNetwork(Object sender, Direction side, long voltage, long energy) {
            return 0;
        }
    }
}
