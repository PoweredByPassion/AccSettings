package app.owlow.accsetting.ui.about

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun AboutRoute(
    context: Context = LocalContext.current
) {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    AboutScreen(
        appVersion = packageInfo.versionName ?: "unknown",
        packageName = context.packageName,
        projectUrl = "https://github.com/poweredByPassion/AccSettings"
    )
}
