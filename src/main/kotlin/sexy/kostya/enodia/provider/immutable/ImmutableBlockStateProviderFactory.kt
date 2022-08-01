package sexy.kostya.enodia.provider.immutable

import net.minestom.server.instance.Instance
import sexy.kostya.enodia.provider.BlockStateProviderFactory

class ImmutableBlockStateProviderFactory : BlockStateProviderFactory<ImmutableBlockStateProvider>(HashMap()) {

    override fun initializeProvider(instance: Instance) = ImmutableBlockStateProvider(instance)
}