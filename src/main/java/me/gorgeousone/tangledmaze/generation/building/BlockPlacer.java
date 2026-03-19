package me.gorgeousone.tangledmaze.generation.building;

import me.gorgeousone.tangledmaze.util.BlockVec;
import me.gorgeousone.tangledmaze.util.MaterialUtil;
import me.gorgeousone.tangledmaze.util.blocktype.BlockLocType;
import me.gorgeousone.tangledmaze.util.blocktype.BlockType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class BlockPlacer {

	private final Set<BlockLocType> backupBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private final JavaPlugin plugin;
	private final World world;
	private final BlockPalette palette;
	private final Consumer<Set<BlockLocType>> callback;
	private final int blocksPerTick;
	private final Set<BlockVec> blocks;

	public BlockPlacer(JavaPlugin plugin,
	                   World world,
	                   Set<BlockVec> blocks,
	                   BlockPalette palette, int blocksPerTick, Consumer<Set<BlockLocType>> callback) {
		this.plugin = plugin;
		this.world = world;
		this.blocks = blocks;
		this.callback = callback;
		this.palette = palette;
		this.blocksPerTick = blocksPerTick;
	}

	public void start() {
		Map<Long, List<BlockVec>> blocksByChunk = new HashMap<>();
		for (BlockVec block : blocks) {
			long key = chunkKey(block.getX() >> 4, block.getZ() >> 4);
			blocksByChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(block);
		}

		if (blocksByChunk.isEmpty()) {
			if (callback != null) {
				callback.accept(backupBlocks);
			}
			return;
		}

		AtomicInteger remaining = new AtomicInteger(blocksByChunk.size());

		for (List<BlockVec> chunkBlocks : blocksByChunk.values()) {
			BlockVec first = chunkBlocks.get(0);
			Location loc = new Location(world, first.getX(), first.getY(), first.getZ());
			Iterator<BlockVec> iter = chunkBlocks.iterator();

			plugin.getServer().getRegionScheduler().runAtFixedRate(plugin, loc, task -> {
				long startTime = System.currentTimeMillis();
				int placed = 0;

				while (iter.hasNext()) {
					placeBlock(iter.next());
					++placed;

					if (blockLimitReached(placed, startTime)) {
						return;
					}
				}
				task.cancel();
				if (remaining.decrementAndGet() == 0 && callback != null) {
					callback.accept(backupBlocks);
				}
			}, 1, 1);
		}
	}

	void placeBlock(BlockVec blockVec) {
		Block block = world.getBlockAt(blockVec.getX(), blockVec.getY(), blockVec.getZ());

		if (MaterialUtil.canBeReplaced(block.getType())) {
			BlockType type = palette.getRndBlock();
			backupBlocks.add(new BlockLocType(block.getLocation(), type).updateBlock(false));
		}
	}

	boolean blockLimitReached(int placedBlocks, long startTime) {
		if (blocksPerTick > -1) {
			if (placedBlocks >= blocksPerTick) {
				return true;
			}
		}
		return System.currentTimeMillis() - startTime >= 25;
	}

	private static long chunkKey(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}
}
