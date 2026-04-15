package com.gto.registrate.providers;

import net.minecraft.data.DataProvider;
import net.minecraftforge.fml.LogicalSide;

public interface RegistrateProvider extends DataProvider {

    LogicalSide getSide();
}
