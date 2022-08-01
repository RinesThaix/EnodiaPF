package sexy.kostya.enodia.pathfinding

enum class Passibility : Comparable<Passibility> {
    Safe,
    Undesirable,
    Dangerous,
    Impassible
}