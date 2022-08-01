package sexy.kostya.enodia.movement

import net.minestom.server.Tickable
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Entity
import sexy.kostya.enodia.movement.importance.MovementImportance

interface MovementProcessor : Tickable {

    /**
     * Dispatch a command to calculate the path to the destination point and to move to it.
     * @param destination - the destination point.
     * @param importance - indicates how much time engine is allowed to take on path calculation.
     * @param range - the maximum distance from the destination point path is allowed to be diverged in order to
     *                count as complete.
     * @return whether movement process has started.
     */
    fun goTo(destination: Point, importance: MovementImportance, range: Float = 1F): Boolean

    /**
     * Dispatch a command to start following the given entity.
     * @param target - the entity to follow.
     * @param importance - indicates how much time engine is allowed to take on path calculation.
     * @param range - the maximum distance from the destination point path is allowed to be diverged in order to
     *                count as complete.
     * @return whether movement process has started.
     */
    fun goTo(target: Entity, importance: MovementImportance, range: Float = 1F): Boolean

    /**
     * Check whether processor is doing something.
     * @return if there's some path that's currently calculating for that entity or if it has already been
     *         calculated and entity is following it.
     */
    fun isActive(): Boolean

    /**
     * Cancel all active path calculations and completely reset the movement state of the processor.
     * @param clearPath - if set to false, entity will continue following part of the previous path that has already
     *                    been calculated.
     */
    fun stop(clearPath: Boolean = true)

}