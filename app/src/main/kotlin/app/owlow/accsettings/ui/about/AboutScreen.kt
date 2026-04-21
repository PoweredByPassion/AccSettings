package app.owlow.accsettings.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.owlow.accsettings.R
import app.owlow.accsettings.ui.theme.*

@Composable
fun AboutScreen(
    appVersion: String,
    packageName: String,
    projectUrl: String,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = AccBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = innerPadding.calculateTopPadding() + 40.dp,
                end = 24.dp,
                bottom = innerPadding.calculateBottomPadding() + 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.about),
                    style = AccTypography.headlineMedium,
                    color = Zinc950
                )
            }
            item {
                AboutInfoCard(
                    title = stringResource(R.string.about_app_section_title),
                    entries = listOf(
                        stringResource(R.string.about_app_name, stringResource(R.string.acc_settings)),
                        stringResource(R.string.about_app_version, appVersion),
                        stringResource(R.string.about_app_package, packageName)
                    )
                )
            }
            item {
                AboutInfoCard(
                    title = stringResource(R.string.about_project_section_title),
                    entries = listOf(
                        stringResource(R.string.about_project_repo, projectUrl),
                        stringResource(R.string.about_project_description)
                    ),
                    onUrlClick = onUrlClick,
                    projectUrl = projectUrl
                )
            }
        }
    }
}

@Composable
private fun AboutInfoCard(
    title: String,
    entries: List<String>,
    onUrlClick: ((String) -> Unit)? = null,
    projectUrl: String? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = AccTypography.titleLarge,
            color = Zinc900,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(1.dp, AccDivider, RoundedCornerShape(24.dp))
                .padding(vertical = 8.dp)
        ) {
            entries.forEachIndexed { index, entry ->
                val isUrl = projectUrl != null && entry.contains(projectUrl)
                Text(
                    text = entry,
                    style = AccTypography.bodyLarge,
                    color = if (isUrl) AccPrimary else Zinc600,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isUrl && onUrlClick != null) {
                                Modifier.clickable { onUrlClick(projectUrl!!) }
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
                if (index < entries.size - 1) {
                    HorizontalDivider(color = AccDivider, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}
