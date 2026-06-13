package com.gregtechceu.gtceu.api.capability;

public final class GTCapability {

    public static final Object EMPTY = new Object();

    public static final Class<IEnergyContainer> ENERGY_CONTAINER = IEnergyContainer.class;

    public static final Class<IEnergyInfoProvider> ENERGY_INFO_PROVIDER = IEnergyInfoProvider.class;

    public static final Class<ILaserContainer> LASER = ILaserContainer.class;

    public static final Class<IOpticalComputationProvider> COMPUTATION_PROVIDER = IOpticalComputationProvider.class;

    public static final Class<IDataAccessHatch> DATA_ACCESS = IDataAccessHatch.class;
}
