package sexy.kostya.enodia.provider.immutable

import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import sexy.kostya.enodia.provider.cache.CachedBlockStateProvider

class ImmutableBlockStateProvider(instance: Instance) : CachedBlockStateProvider(instance) {

    override fun onBlockChanged(x: Int, y: Int, z: Int, block: Block) {
        throw IllegalStateException("block can not be changed within immutable block state provider")
    }
}