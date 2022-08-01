package sexy.kostya.enodia.provider.cache

import sexy.kostya.enodia.provider.BlockStateProvider

object EmptyChunkSectionCache : ChunkSectionCache {

    override fun set(x: Int, y: Int, z: Int, mask: Int) {}

    override fun get(x: Int, y: Int, z: Int) = BlockStateProvider.NotCollideable
}