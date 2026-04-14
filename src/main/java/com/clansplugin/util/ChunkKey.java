package com.clansplugin.util;

import org.bukkit.Chunk;

public final class ChunkKey {
    private ChunkKey() {
    }

    public static String of(Chunk chunk) {
        return of(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public static String of(String world, int x, int z) {
        return world + ":" + x + ":" + z;
    }
}
