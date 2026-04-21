package app.owlow.accsettings.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
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
        projectUrl = "https://github.com/poweredByPassion/AccSettings",
        onUrlClick = { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    )
}
