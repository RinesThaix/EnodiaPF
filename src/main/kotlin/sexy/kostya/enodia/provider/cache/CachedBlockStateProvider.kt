package sexy.kostya.enodia.provider.cache

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.chunk.ChunkUtils
import sexy.kostya.enodia.provider.BlockStateProvider

abstract class CachedBlockStateProvider(protected val instance: Instance) : BlockStateProvider {

    @Volatile
    protected var cache = Long2ObjectOpenHashMap<Int2ObjectMap<ChunkSectionCache>>()

    @Synchronized
    override fun loadChunk(chunk: Chunk) {
        val index = ChunkUtils.getChunkIndex(chunk.chunkX, chunk.chunkZ)
        if (cache.containsKey(index)) {
            return
        }
        val sections = Int2ObjectOpenHashMap<ChunkSectionCache>()
        for (sectionIndex in chunk.minSection until chunk.maxSection) {
            val section = chunk.getSection(sectionIndex)
            val palette = section.blockPalette()
            sections[sectionIndex] = if (palette.count() == 0) {
                EmptyChunkSectionCache
            } else {
                NotEmptyChunkSectionCache().apply {
                    palette.getAllPresent { x, y, z, blockStateId ->
                        val block = Block.fromStateId(blockStateId.toShort())
                        this[x, y, z] = if (block == null) BlockStateProvider.Solid else BlockStateProvider.createMaskFromBlock(block)
                    }
                }
            }
        }
        val copiedCache = Long2ObjectOpenHashMap(cache)
        copiedCache[index] = sections
        cache = copiedCache
    }

    @Synchronized
    override fun unloadChunk(chunk: Chunk) {
        val index = ChunkUtils.getChunkIndex(chunk.chunkX, chunk.chunkZ)
        if (!cache.containsKey(index)) {
            return
        }
        val copiedCache = Long2ObjectOpenHashMap(cache)
        copiedCache.remove(index)
        cache = copiedCache
    }

    override fun getBlockState(x: Int, y: Int, z: Int): Block {
        if (!instance.isChunkLoaded(ChunkUtils.getChunkCoordinate(x), ChunkUtils.getChunkCoordinate(z))) {
            return Block.STONE
        }
        return instance.getBlock(x, y, z)
    }

    override fun getBlockMask(x: Int, y: Int, z: Int): Int {
        val sections = cache[ChunkUtils.getChunkIndex(x shr 4, z shr 4)] ?: return BlockStateProvider.Solid
        val section = sections[ChunkUtils.getChunkCoordinate(y)] ?: return BlockStateProvider.Solid
        return section[x and 0xF, y and 0xF, z and 0xF]
    }
}