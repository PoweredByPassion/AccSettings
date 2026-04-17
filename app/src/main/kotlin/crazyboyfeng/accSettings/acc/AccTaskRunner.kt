package crazyboyfeng.accSettings.acc

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AccTaskRunner {
    private val mutex = Mutex()

    suspend fun <T> runSerialized(block: suspend () -> T): T = mutex.withLock {
        block()
    }
}
