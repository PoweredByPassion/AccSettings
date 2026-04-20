package app.owlow.accsetting.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.owlow.accsetting.R

@Composable
fun AboutScreen(
    appVersion: String,
    packageName: String,
    projectUrl: String,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = innerPadding.calculateTopPadding() + 20.dp,
                end = 20.dp,
                bottom = innerPadding.calculateBottomPadding() + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.about),
                    style = MaterialTheme.typography.headlineMedium
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
                    )
                )
            }
        }
    }
}

@Composable
private fun AboutInfoCard(
    title: String,
    entries: List<String>
) {
    Card {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            entries.forEach { entry ->
                Text(
                    text = entry,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
