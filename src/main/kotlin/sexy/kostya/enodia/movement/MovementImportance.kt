package sexy.kostya.enodia.movement

import java.time.Duration

data class MovementImportance(
    val averageExecutionTime: Long,
    val maxExecutionTime: Long,
    val teleportOnFail: Boolean
) {

    constructor(averageExecutionTime: Duration, maxExecutionTime: Duration, teleportOnFail: Boolean) : this(
        averageExecutionTime.toMillis(),
        maxExecutionTime.toMillis(),
        teleportOnFail
    )

    companion object {

        val UNIMPORTANT = MovementImportance(Duration.ofMillis(25), Duration.ofMillis(50), false)
        val IMPORTANT = MovementImportance(Duration.ofMillis(50), Duration.ofMillis(250), true)
        val EXTREME = MovementImportance(Duration.ofMillis(250), Duration.ofMillis(250), true)

    }

}