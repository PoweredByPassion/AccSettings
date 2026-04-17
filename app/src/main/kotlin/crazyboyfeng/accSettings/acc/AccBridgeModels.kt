package crazyboyfeng.accSettings.acc

import java.util.Properties

data class GroupedConfigRead(
    val current: Properties,
    val defaults: Properties
)
