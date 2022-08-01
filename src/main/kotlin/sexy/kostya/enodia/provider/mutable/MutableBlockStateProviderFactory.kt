package sexy.kostya.enodia.provider.mutable

import net.minestom.server.instance.Instance
import sexy.kostya.enodia.provider.BlockStateProviderFactory
import java.util.concurrent.ConcurrentHashMap

class MutableBlockStateProviderFactory : BlockStateProviderFactory<MutableBlockStateProvider>(ConcurrentHashMap()) {

    override fun initializeProvider(instance: Instance): MutableBlockStateProvider {
        val provider = MutableBlockStateProvider(instance)
        provider.register()
        return provider
    }

    override fun remove(instance: Instance): MutableBlockStateProvider? {
        val result = super.remove(instance)
        result?.unregister()
        return result
    }
}