package sexy.kostya.enodia.provider.cache

sealed interface ChunkSectionCache {

    operator fun set(x: Int, y: Int, z: Int, mask: Int)

    operator fun get(x: Int, y: Int, z: Int): Int

}