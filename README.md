# Enodia Path Finding Engine

[![Watch preview video](https://img.youtube.com/vi/HmJu0Xa9F-0/0.jpg)](https://youtu.be/HmJu0Xa9F-0)

**EnodiaPF** is a highly optimized asynchronous [Minestom](https://github.com/Minestom/Minestom) pathfinding engine. Being inspired by [Hydrazine](https://github.com/MadMartian/hydrazine-path-finding) and [Krystilize's](https://github.com/KrystilizeNevaDies) work, it takes on many of their aspects. As for Hydrazine, this is an incrementally progressive A* pathfinding engine that trades-off accuracy for significant boosts in path-finding performance by distributing A* graph computation across time and then rotation the graph on an as-needed basis as the subject entity physically traverses through path points derived from it. Thanks to this, in the game world it can:
1. If the destination is a long distance from the start, the path can be calculated in parts, with a new part only starting to be calculated when the entity made a certain progression on the previous part.
2. If an entity follows another entity and the destination point is constantly changing, the path will be recalculating online, but only if is it really necessary.

Moreover, unlike its ancestors, the implementation of this engine focuses on maximizing its own performance, which is achieved by the following concepts:
1. Engine is asynchronous. Path recalculation for each entity is independent of each other. Moreover, for the same point of arrival of one entity, several pathfinding tasks can be running. Results of their executions will be accumulated together to ensure eventual consistency of the finalized path.
2. The process of path recalculation is not simple, and therefore may entail excessive use of non-infinite resources. All the code was written with an eye on that factor of influence: many internal things consist of primitive or pools of reusable objects to reduce memory allocations; and the number of synchronizations is minimized. For example, all the data from the instance (world) needed for the pathfinder is stored in a special cache, where exactly 3 bits are allocated per each block. This not only reduces the number of synchronizations to zero in case of immutable worlds, but also significantly reduces the amount of RAM required for the engine to function.

Despite the fact trading-off accuracy for performance seems to be working pretty well, inaccurate path-finding is not always desirable. This engine provides you with an opportunity of specifying the maximum execution time that could be taken by the engine to calculate a path. Beyond that, it leaves you with a possibility to enable entity teleportation to its destination in cases when path couldn't be calculated after certain amount of times or whether there is not valid path at all.

## Pathfinding
The engine could be used as a pure pathfinder or also as a movement processor.

To start with pathfinding, first initialize a `EnodiaPF` class by calling `EnodiaPF.forMutableWorlds()` or `EnodiaPF.forImmutableWorlds()`.
All you need to do next is to call `enodia#createPathfindingTask` with various arguments to initialize the pathfinding task, and after that - call `task.run()`.
You can do it in asynchronous environment, and the result will contain information about the path itself and also whether calculated path is complete, partial or if it was cancelled from the outside: yes, you can also asynchronously stop task execution at any moment of time by invoking `task.cancel()`.

### Capabilities
Engine supports the following pathfinding capabilities that could be setup for an entity:
- Fire Resistant - if set to true, entity will not be scared of going through the igniting environment (like lava, fire, magma).
- Aquaphobic - if set to true, entity will try to avoid any kind of liquids.
- Aquatic - if set to true, entity will not be able to move outside of liquids.
- Avian - if set to true, entity can fly.

### Issues
- Jumping is not supported.
- Climbing up on ladder/liana/etc is not supported.
- If it is the shortest way for the entity to go down, it does not take the height into account and therefore may die if falling damage is enabled.
- Instead of natural descent from high altitudes, levitation occurs.
- Sometimes entities can end up in extremely unnatural positions (for example, walk right through a mountain).
- Entities are "floating" over liquids.

## Movement Processing
Engine's movement processing part is what utilizes pathfinder and makes entities move.

Unlike Hydrazine, EnodiaPF does not have an inbuilt Minestom API to be used. Therefore, there are some requirements you must follow in order to ensure engine is functioning well:
1. You are not allowed to manipulate entity's no-gravity option. The engine will do it itself.
2. Movement processor is a `Tickable`, so you need to tick it within `Entity#update(time)` method.

The concept is as simple as follows: for every entity you can initialize a corresponding movement processor, which has the following methods:
- `MovementProcessor#goTo(Point destination, Importance importance, float range)` - a command to calculate the path to the destination point. The importance indicates how much time engine is allowed to take on path calculation, and the range is the maximum distance from the destination point path is allowed to be diverged in order to count as complete.
- `MovementProcessor#goTo(Entity target, Importance importance, float range)` - a command to start following the given entity.
- `MovementProcessor#isActive` - whether there's some path that's currently calculating for that entity or if it has already been calculated and entity is following it.
- `MovementProcessor#stop` - cancel all active path calculations and completely reset the movement state.

There is also an example server that's included within test sources. Enjoy it!