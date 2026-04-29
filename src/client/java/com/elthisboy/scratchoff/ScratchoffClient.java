package com.elthisboy.scratchoff;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScratchoffClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("scratch-off-client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("[Scratch-Off] Cliente inicializado!");
	}
}
