package app.owlow.accsetting.ui.overview

data class OverviewUiState(
    val isLoading: Boolean = true,
    val statusHeadline: String = "",
    val runtimeFacts: List<OverviewFact> = emptyList(),
    val primaryActions: List<OverviewAction> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class OverviewFact(
    val label: String,
    val value: String
)

data class OverviewAction(
    val id: String,
    val label: String
)
