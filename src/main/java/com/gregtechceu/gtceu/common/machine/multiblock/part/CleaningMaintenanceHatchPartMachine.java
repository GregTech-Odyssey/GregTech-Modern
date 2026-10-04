package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.DummyCleanroom;

import net.minecraft.MethodsReturnNonnullByDefault;

import lombok.Getter;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.api.GTValues.*;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CleaningMaintenanceHatchPartMachine extends AutoMaintenanceHatchPartMachine {

    // must come after the static block
    private final ICleanroomProvider DUMMY_CLEANROOM;
    @Getter
    private final int cleanroomTier;

    public CleaningMaintenanceHatchPartMachine(MetaMachineBlockEntity metaTileEntityId, int cleanroomTier) {
        super(metaTileEntityId);
        this.cleanroomTier = cleanroomTier;
        DUMMY_CLEANROOM = DummyCleanroom.create(cleanroomTier);
    }

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        if (controller instanceof ICleanroomReceiver receiver) {
            receiver.setCleanroom(DUMMY_CLEANROOM);
        }
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        if (controller instanceof ICleanroomReceiver receiver && receiver.getCleanroom() == DUMMY_CLEANROOM) {
            receiver.setCleanroom(null);
        }
    }

    @Override
    public int getTier() {
        return cleanroomTier + ZPM;
    }
}
