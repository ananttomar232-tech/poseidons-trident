package com.poseidon.trident;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Bookkeeping for one Great Tsunami: remembers every block it replaced with water
 * so the terrain can be put back when the power ends.
 */
final class Flood {
    /** Half-width of the flooded square. 25 -> a 51x51 block area (about 50x50). */
    static final int RADIUS = 25;
    /** How many water layers are placed on top of the ground. */
    static final int DEPTH = 4;
    /** The wave front travels this many blocks per tick. */
    static final int WAVE_SPEED = 2;

    final ResourceKey<Level> dimension;
    final int centerX;
    final int centerZ;
    final long startTick;
    int builtRadius = -1;

    final Map<BlockPos, BlockState> original = new LinkedHashMap<>();

    Flood(ServerLevel level, BlockPos center, long startTick) {
        this.dimension = level.dimension();
        this.centerX = center.getX();
        this.centerZ = center.getZ();
        this.startTick = startTick;
    }

    /** Grows the flood outward as a square wave. Returns the current wave radius. */
    int advance(ServerLevel level, long now) {
        int target = Math.min(RADIUS, (int) (now - startTick) * WAVE_SPEED + 1);
        for (int r = builtRadius + 1; r <= target; r++) {
            placeRing(level, r);
        }
        builtRadius = Math.max(builtRadius, target);
        return builtRadius;
    }

    private void placeRing(ServerLevel level, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                placeColumn(level, centerX + dx, centerZ + dz);
            }
        }
    }

    private void placeColumn(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int dy = 0; dy < DEPTH; dy++) {
            int y = surface + dy;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            boolean replaceable = state.isAir()
                    || (state.canBeReplaced() && state.getFluidState().isEmpty());
            if (!replaceable) continue;
            original.putIfAbsent(pos, state);
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Restores up to {@code budget} blocks. Returns true when everything is restored.
     */
    boolean restoreSome(ServerLevel level, int budget) {
        var it = original.entrySet().iterator();
        while (it.hasNext() && budget-- > 0) {
            var entry = it.next();
            BlockPos pos = entry.getKey();
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.WATER)) {
                level.setBlock(pos, entry.getValue(), Block.UPDATE_ALL);
            }
            it.remove();
        }
        return original.isEmpty();
    }
}
