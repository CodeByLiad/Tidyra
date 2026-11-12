package digital.jinatra.tidyra

import android.app.Application
import digital.jinatra.tidyra.core.trash.TrashStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TidyraApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Trashed files still occupy storage, so the bin is swept on launch rather than
        // growing quietly until the user wonders where their space went.
        scope.launch {
            runCatching { TrashStore(this@TidyraApp).purgeExpired(DEFAULT_TRASH_RETENTION_DAYS) }
        }
    }

    companion object {
        const val DEFAULT_TRASH_RETENTION_DAYS = 14
    }
}
