package sexy.kostya.enodia.provider

import net.minestom.server.collision.BoundingBox
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.block.Block
import sexy.kostya.enodia.pathfinding.Passibility
import sexy.kostya.enodia.pathfinding.PathfindingCapabilities
import kotlin.math.floor

interface BlockStateProvider {

    companion object {

        const val NotCollideable = 0x00
        const val Liquid = 0x01
        const val Solid = 0x02
        const val Complex = 0x03
        const val StateMask = 0x03

        const val FireBit = 0x04

        fun createMaskFromBlock(block: Block): Int {
            if (block == Block.AIR) {
                return NotCollideable
            }
            when (block.key().value()) {
                "water" -> return Liquid
                "lava" -> return Liquid or FireBit
                "fire", "soul_fire" -> return NotCollideable or FireBit
            }
            val inFire = if (block == Block.MAGMA_BLOCK) FireBit else 0
            val shape = block.registry().collisionShape()
            return if (Vec.ZERO.samePoint(shape.relativeStart()) && Vec.ONE.samePoint(shape.relativeEnd())) {
                if (block.isSolid) {
                    Solid
                } else {
                    NotCollideable
                }
            } else {
                Complex
            } or inFire
        }
    }

    fun loadChunk(chunk: Chunk)

    fun unloadChunk(chunk: Chunk)

    fun onBlockChanged(x: Int, y: Int, z: Int, block: Block)

    fun getBlockState(x: Int, y: Int, z: Int): Block

    fun getBlockMask(x: Int, y: Int, z: Int): Int

    fun getPassibility(
        entity: Entity,
        step: Float,
        capabilities: PathfindingCapabilities,
        x: Float,
        y: Float,
        z: Float
    ): Passibility {
        val bb = entity.boundingBox.contract(.1, 0.0, .1)
        return getPassibility(
            bb,
            step,
            capabilities,
            x, y, z,
            x, y, z
        )
    }

    fun getPassibility(
        bb: BoundingBox,
        step: Float,
        capabilities: PathfindingCapabilities,
        parentX: Float,
        parentY: Float,
        parentZ: Float,
        x: Float,
        y: Float,
        z: Float
    ): Passibility {
        val bbMin = bb.relativeStart()
        val bbMax = bb.relativeEnd()

        val fromX = floor(bbMin.x() + x + .01F).toInt()
        val fromY = floor(bbMin.y() + y + .01F).toInt()
        val fromZ = floor(bbMin.z() + z + .01F).toInt()
        val toX = floor(bbMax.x() + x - .01F).toInt()
        val toY = floor(bbMax.y() + y - .01F).toInt()
        val toZ = floor(bbMax.z() + z - .01F).toInt()

        var inFire = false
        var inLiquid = false
        var pos: Point? = null
        for (bx in fromX..toX) {
            for (by in fromY - 1..toY) {
                val within = by != fromY - 1
                for (bz in fromZ..toZ) {
                    val mask = getBlockMask(bx, by, bz)
                    val state = mask and StateMask
                    if (within && state == Solid) {
                        return Passibility.Impassible
                    }
                    if (state == Complex) {
                        val block = getBlockState(bx, by, bz)
                        val blockPos = Vec(bx.toDouble(), by.toDouble() - step, bz.toDouble())
                        if (pos == null) {
                            pos = Vec(x.toDouble(), y.toDouble(), z.toDouble())
                        }
                        if (block.registry().collisionShape().intersectBox(pos.sub(blockPos), bb)) {
                            return Passibility.Impassible
                        }
                    } else if (within && state == Liquid) {
                        inLiquid = true
                    }
                    if (within && !capabilities.fireResistant && (mask and FireBit) != 0) {
                        inFire = true
                    }
                }
            }
        }
        if (inLiquid && inFire) {
            return Passibility.Impassible
        }
        val passibility = when {
            inFire -> Passibility.Dangerous
            inLiquid -> when {
                capabilities.aquaphobic -> Passibility.Dangerous
                capabilities.aquatic -> Passibility.Safe
                else -> Passibility.Undesirable
            }
            else -> when {
                capabilities.aquatic -> return Passibility.Impassible
                else -> Passibility.Safe
            }
        }
        if (!inLiquid && parentY <= y && !capabilities.avian) {
            val by = floor(bb.minY() + y - step).toInt()
            for (bx in fromX..toX) {
                for (bz in fromZ..toZ) {
                    val mask = getBlockMask(bx, by, bz)
                    val state = mask and StateMask
                    if (state == Solid) {
                        return passibility
                    }
                    if (state == Complex) {
                        val block = getBlockState(bx, by, bz)
                        val blockPos = Vec(bx.toDouble(), by.toDouble() + step, bz.toDouble())
                        if (pos == null) {
                            pos = Vec(x.toDouble(), y.toDouble(), z.toDouble())
                        }
                        if (block.registry().collisionShape().intersectBox(pos.sub(blockPos), bb)) {
                            return passibility
                        }
                    }
                }
            }
            return Passibility.Impassible
        }
        return passibility
    }

}