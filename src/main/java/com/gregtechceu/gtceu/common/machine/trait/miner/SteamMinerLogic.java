package com.gregtechceu.gtceu.common.machine.trait.miner;

import com.gregtechceu.gtceu.api.machine.feature.IExhaustVentMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.issue.GTIssues;

public class SteamMinerLogic extends MinerLogic {

    /**
     * Creates the logic for steam miners
     *
     * @param metaTileEntity the {@link IRecipeLogicMachine} this logic belongs to
     * @param fortune        the fortune amount to apply when mining ores
     * @param speed          the speed in ticks per block mined
     * @param maximumRadius  the maximum radius (square shaped) the miner can mine in
     */
    public SteamMinerLogic(IRecipeLogicMachine metaTileEntity, int fortune, int speed, int maximumRadius) {
        super(metaTileEntity, fortune, speed, maximumRadius);
    }

    @Override
    protected boolean checkCanMine() {
        IExhaustVentMachine machine = (IExhaustVentMachine) this.machine;
        if (!super.checkCanMine()) return false;
        if (machine.checkVenting()) return true;
        report(GTIssues.VENT_BLOCKED);
        return false;
    }

    @Override
    protected void onMineOperation() {
        super.onMineOperation();
        ((IExhaustVentMachine) machine).setNeedsVenting(true);
    }
}
