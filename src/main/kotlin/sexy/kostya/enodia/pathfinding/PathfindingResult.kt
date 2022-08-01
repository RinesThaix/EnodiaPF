package sexy.kostya.enodia.pathfinding

import sexy.kostya.enodia.util.ReusablePoint

data class PathfindingResult(
    val status: Status,
    val path: List<ReusablePoint>,
    val cancellationReason: CancellationReason? = null
) {

    enum class CancellationReason {
        TIMED_OUT,
        OUTDATED
    }

    enum class Status {
        FAILED,
        CANCELLED,
        PARTIAL,
        COMPLETED
    }

}