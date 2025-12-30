package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateBlocks;

public final class ControllerPredicate extends TraceabilityPredicate {

    ControllerPredicate(MachineDefinition definition) {
        super(new PredicateBlocks(definition.get()));
    }
}
