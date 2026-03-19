package me.gorgeousone.tangledmaze.generation.building;

import me.gorgeousone.tangledmaze.util.blocktype.BlockLocType;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockResetter {

	private final JavaPlugin plugin;
	private final Runnable callback;
	private final int blocksPerTick;
	private final Set<BlockLocType> blocks;

	public BlockResetter(JavaPlugin plugin,
	                     Set<BlockLocType> blocks,
	                     int blocksPerTick,
	                     Runnable callback) {
		this.plugin = plugin;
		this.callback = callback;
		this.blocksPerTick = blocksPerTick;
		this.blocks = blocks;
	}

	public void start() {
		Map<Long, List<BlockLocType>> blocksByChunk = new HashMap<>();
		Location firstLoc = null;
		for (BlockLocType block : blocks) {
			Location loc = block.getLocation();
			if (firstLoc == null) {
				firstLoc = loc;
			}
			long key = chunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
			blocksByChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(block);
		}

		if (blocksByChunk.isEmpty()) {
			if (callback != null) {
				callback.run();
			}
			return;
		}

		final Location callbackLoc = firstLoc;
		AtomicInteger remaining = new AtomicInteger(blocksByChunk.size());

		for (List<BlockLocType> chunkBlocks : blocksByChunk.values()) {
			Location loc = chunkBlocks.get(0).getLocation();
			Iterator<BlockLocType> iter = chunkBlocks.iterator();

			plugin.getServer().getRegionScheduler().runAtFixedRate(plugin, loc, task -> {
				long startTime = System.currentTimeMillis();
				int placed = 0;

				while (iter.hasNext()) {
					iter.next().updateBlock(true);
					++placed;

					if (blockLimitReached(placed, blocksPerTick, startTime)) {
						return;
					}
				}
				task.cancel();
				if (remaining.decrementAndGet() == 0 && callback != null) {
					//why is this delay here?
					plugin.getServer().getRegionScheduler().runDelayed(plugin, callbackLoc, t -> callback.run(), 2);
				}
			}, 1, 1);
		}
	}

	boolean blockLimitReached(int placedBlocks, int bpt, long startTime) {
		if (bpt > -1) {
			if (placedBlocks >= bpt) {
				return true;
			}
		}
		return System.currentTimeMillis() - startTime >= 25;
	}

	private static long chunkKey(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}
}
