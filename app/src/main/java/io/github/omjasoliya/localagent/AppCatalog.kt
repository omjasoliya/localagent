package io.github.omjasoliya.localagent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppEntry(val label: String, val packageName: String)

/** The set of apps the model is allowed to pick from — only ones with a launcher icon. */
object AppCatalog {
    fun list(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != ctx.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(ctx: Context, packageName: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
