package sexy.kostya.enodia.provider.cache

class NotEmptyChunkSectionCache : ChunkSectionCache {

    private val data = ByteArray(1536)

    override fun set(x: Int, y: Int, z: Int, mask: Int) {
        val index = index(x, y, z)
        val pos = index.shr(3) * 3
        when (index and 7) {
            0 -> data[pos] = data[pos].toInt().and(0b111.inv()).or(mask).toByte()
            1 -> data[pos] = data[pos].toInt().and(0b111000.inv()).or(mask shl 3).toByte()

            2 -> {
                data[pos] = data[pos].toInt().and(0b11000000.inv()).or(mask.and(0x3).shl(6)).toByte()
                data[pos + 1] = data[pos + 1].toInt().and(0b1.inv()).or(mask shr 2).toByte()
            }

            3 -> data[pos + 1] = data[pos + 1].toInt().and(0b1110.inv()).or(mask shl 1).toByte()
            4 -> data[pos + 1] = data[pos + 1].toInt().and(0b1110000.inv()).or(mask shl 4).toByte()

            5 -> {
                data[pos + 1] = data[pos + 1].toInt().and(0b10000000.inv()).or(mask.and(0x1).shl(7)).toByte()
                data[pos + 2] = data[pos + 2].toInt().and(0b11.inv()).or(mask shr 1).toByte()
            }

            6 -> data[pos + 2] = data[pos + 2].toInt().and(0b11100.inv()).or(mask shl 2).toByte()
            else -> data[pos + 2] = data[pos + 2].toInt().and(0b11100000.inv()).or(mask shl 5).toByte()
        }
    }

    override fun get(x: Int, y: Int, z: Int): Int {
        val index = index(x, y, z)
        val pos = index.shr(3) * 3
        return when (index and 7) {
            0 -> data[pos].toInt().and(0b111)
            1 -> data[pos].toInt().and(0b111000).shr(3)

            2 -> {
                data[pos].toInt().and(0b11000000).shr(6) or
                        data[pos + 1].toInt().and(0b1).shl(2)
            }

            3 -> data[pos + 1].toInt().and(0b1110).shr(1)
            4 -> data[pos + 1].toInt().and(0b1110000).shr(4)

            5 -> {
                data[pos + 1].toInt().and(0b10000000).shr(7) or
                        data[pos + 2].toInt().and(0b11).shl(1)
            }

            6 -> data[pos + 2].toInt().and(0b11100).shr(2)
            else -> data[pos + 2].toInt().and(0b11100000).shr(5)
        }
    }

    private fun index(x: Int, y: Int, z: Int) = x or (y shl 4) or (z shl 8)
}