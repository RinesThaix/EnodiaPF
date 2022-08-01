package sexy.kostya.enodia.provider.mutable

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.chunk.ChunkUtils
import sexy.kostya.enodia.provider.BlockStateProvider
import sexy.kostya.enodia.provider.cache.CachedBlockStateProvider
import sexy.kostya.enodia.provider.cache.EmptyChunkSectionCache
import sexy.kostya.enodia.provider.cache.NotEmptyChunkSectionCache

class MutableBlockStateProvider(instance: Instance) : CachedBlockStateProvider(instance) {

    private lateinit var blockPlaceNode: EventNode<InstanceEvent>
    private lateinit var blockBreakNode: EventNode<InstanceEvent>

    internal fun register() {
        blockPlaceNode = instance.eventNode().addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val pos = event.blockPosition
            onBlockChanged(pos.blockX(), pos.blockY(), pos.blockZ(), event.block)
        }
        blockBreakNode = instance.eventNode().addListener(PlayerBlockBreakEvent::class.java) { event ->
            val pos = event.blockPosition
            onBlockChanged(pos.blockX(), pos.blockY(), pos.blockZ(), event.resultBlock)
        }
    }

    internal fun unregister() {
        instance.eventNode().removeChild(blockPlaceNode)
        instance.eventNode().removeChild(blockBreakNode)
    }

    @Synchronized
    override fun onBlockChanged(x: Int, y: Int, z: Int, block: Block) {
        val chunkX = ChunkUtils.getChunkCoordinate(x)
        val chunkZ = ChunkUtils.getChunkCoordinate(z)
        val index = ChunkUtils.getChunkIndex(chunkX, chunkZ)
        if (!cache.containsKey(index)) {
            val chunk = instance.getChunk(chunkX, chunkZ) ?: throw IllegalStateException("block changed in unloaded chunk")
            loadChunk(chunk)
            return
        }
        val sections = cache[index]!!
        val sectionIndex = ChunkUtils.getChunkCoordinate(y)
        var section = sections[sectionIndex]
        if (section == null || section == EmptyChunkSectionCache) {
            section = NotEmptyChunkSectionCache()
            sections[sectionIndex] = section
        }
        section[
                x and 15,
                y % Chunk.CHUNK_SECTION_SIZE,
                z and 15
        ] = BlockStateProvider.createMaskFromBlock(block)

        val copiedCache = Long2ObjectOpenHashMap(cache)
        copiedCache[index] = sections
        cache = copiedCache
    }
}