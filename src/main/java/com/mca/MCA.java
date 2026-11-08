package com.mca;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared entrypoint for the MCA Fabric mod.
 */
public final class MCA implements ModInitializer {
    public static final String MOD_ID = "mca";
    public static final Logger LOGGER = LogManager.getLogger("MCA");

    /**
     * Registers content and services shared by client/server initialization.
     */
    @Override
    public void onInitialize() {
        LOGGER.info("MCA initialized");
    }
}
