package crazyboyfeng.accSettings

import com.topjohnwu.superuser.Shell

object AppShell {
    private var configured = false
    private var configurator: () -> Unit = {
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(120)
        )
    }

    @Synchronized
    fun configureDefaultShell() {
        if (configured) {
            return
        }
        configurator()
        configured = true
    }

    internal fun resetForTesting(configurator: (() -> Unit)? = null) {
        configured = false
        this.configurator = configurator ?: {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(120)
            )
        }
    }
}
