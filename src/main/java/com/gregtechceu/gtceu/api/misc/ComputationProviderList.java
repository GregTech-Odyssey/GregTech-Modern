package com.gregtechceu.gtceu.api.misc;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;

import java.util.List;

public final class ComputationProviderList implements IOpticalComputationProvider {

    public static final ComputationProviderList EMPTY = new ComputationProviderList();

    public final IOpticalComputationProvider[] providers;

    public ComputationProviderList(List<IOpticalComputationProvider> providers) {
        this.providers = providers.toArray(new IOpticalComputationProvider[0]);
    }

    public ComputationProviderList(IOpticalComputationProvider... providers) {
        this.providers = providers;
    }

    @Override
    public long requestCWU(long cwu, boolean simulate) {
        long result = 0;
        for (IOpticalComputationProvider provider : providers) {
            result += provider.requestCWU(cwu - result, simulate);
            if (result >= cwu) break;
        }
        return result;
    }

    @Override
    public boolean canBridge() {
        for (IOpticalComputationProvider provider : providers) {
            if (!provider.canBridge()) return false;
        }
        return true;
    }
}
