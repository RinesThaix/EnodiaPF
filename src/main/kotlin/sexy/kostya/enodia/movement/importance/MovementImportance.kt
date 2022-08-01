package sexy.kostya.enodia.movement.importance

import net.minestom.server.entity.Entity
import sexy.kostya.enodia.util.ReusablePoint
import java.time.Duration

abstract class MovementImportance(
    val averageExecutionTime: Long,
    val maxExecutionTime: Long
) {

    constructor(averageExecutionTime: Duration, maxExecutionTime: Duration) : this(
        averageExecutionTime.toMillis(),
        maxExecutionTime.toMillis()
    )

    companion object {

        val UNIMPORTANT: MovementImportance = UnimportantMovementImportance(
            Duration.ofMillis(25),
            Duration.ofMillis(50)
        )
        val IMPORTANT: MovementImportance = TeleportationMovementImportance(
            Duration.ofMillis(50),
            Duration.ofMillis(250)
        )
        val EXTREME: MovementImportance = TeleportationMovementImportance(
            Duration.ofMillis(250),
            Duration.ofMillis(250)
        )

    }

    /**
     * @param entity - entity for which path calculation failed.
     * @param destination - destination point that had to be the ending point of the path.
     * @return if the movement processor should stop trying to calculate the given path.
     */
    abstract fun onPathCalculationFailed(entity: Entity, destination: ReusablePoint): Boolean

}