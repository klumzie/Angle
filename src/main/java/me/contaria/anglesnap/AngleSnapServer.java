package me.contaria.anglesnap;

import net.fabricmc.api.DedicatedServerModInitializer;

public class AngleSnapServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        new ArrowTracker(); // Server-side arrow tracking logic
    }
}
