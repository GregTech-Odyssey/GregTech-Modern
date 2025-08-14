package com.gregtechceu.gtceu.common.machine.multiblock.primitive;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;

public class PrimitiveFancyUIWorkableMachine extends PrimitiveWorkableMachine implements IFancyUIMachine {

    public PrimitiveFancyUIWorkableMachine(MetaMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }
}
