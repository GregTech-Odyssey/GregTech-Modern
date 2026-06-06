package com.gregtechceu.gtceu.api.capability.forge;

import com.gregtechceu.gtceu.api.capability.*;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

public class GTForgeCapability {

    public static final Capability<IMedicalConditionTracker> CAPABILITY_MEDICAL_CONDITION_TRACKER = CapabilityManager
            .get(new CapabilityToken<>() {});

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(IMedicalConditionTracker.class);
    }
}
