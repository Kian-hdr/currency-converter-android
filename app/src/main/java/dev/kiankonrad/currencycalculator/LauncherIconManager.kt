package dev.kiankonrad.currencycalculator

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Selects this app's own transparent white launcher mark when Whicons is installed.
 * This does not inspect or alter launcher preferences, or modify the Whicons pack.
 * Android has no standard cross-launcher API that identifies the active icon pack.
 */
object LauncherIconManager {
    const val WHICONS_PACKAGE = "com.whicons.iconpack"
    const val NORMAL_ALIAS_SUFFIX = ".NormalLauncher"
    const val WHICONS_ALIAS_SUFFIX = ".WhiconsLauncher"

    enum class Mode { NORMAL, WHICONS }

    data class Selection(
        val mode: Mode,
        val whiconsInstalled: Boolean,
        val changed: Boolean,
        val error: String? = null
    )

    /** Recheck on activity resume so a later Whicons install/removal is reflected safely. */
    fun sync(context: Context): Selection {
        val packageManager = context.packageManager
        val whiconsInstalled = isWhiconsInstalled(packageManager)
        val mode = if (whiconsInstalled) Mode.WHICONS else Mode.NORMAL
        val normal = ComponentName(context.packageName, context.packageName + NORMAL_ALIAS_SUFFIX)
        val white = ComponentName(context.packageName, context.packageName + WHICONS_ALIAS_SUFFIX)
        return try {
            val normalEnabled = isEnabled(packageManager, normal)
            val whiteEnabled = isEnabled(packageManager, white)
            val desiredNormal = mode == Mode.NORMAL
            if (normalEnabled == desiredNormal && whiteEnabled != desiredNormal) {
                Selection(mode, whiconsInstalled, changed = false)
            } else {
                val enable = if (desiredNormal) normal else white
                val disable = if (desiredNormal) white else normal
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Atomic on current Android: the launcher never sees zero/both entries.
                    packageManager.setComponentEnabledSettings(
                        listOf(
                            PackageManager.ComponentEnabledSetting(
                                enable,
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                PackageManager.DONT_KILL_APP
                            ),
                            PackageManager.ComponentEnabledSetting(
                                disable,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP
                            )
                        )
                    )
                } else {
                    // Enable first on older releases, preserving a usable launcher entry.
                    packageManager.setComponentEnabledSetting(
                        enable,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    packageManager.setComponentEnabledSetting(
                        disable,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
                Selection(mode, whiconsInstalled, changed = true)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            Selection(mode, whiconsInstalled, changed = false, error = "Launcher icon aliases are not declared")
        } catch (_: IllegalArgumentException) {
            Selection(mode, whiconsInstalled, changed = false, error = "Launcher icon could not be changed")
        } catch (_: SecurityException) {
            Selection(mode, whiconsInstalled, changed = false, error = "Android did not allow the launcher icon change")
        }
    }

    @Suppress("DEPRECATION")
    fun isWhiconsInstalled(packageManager: PackageManager): Boolean = try {
        val info = packageManager.getApplicationInfo(WHICONS_PACKAGE, 0)
        info.enabled && info.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED != 0
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    @Suppress("DEPRECATION")
    private fun isEnabled(packageManager: PackageManager, component: ComponentName): Boolean =
        when (packageManager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            else -> packageManager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
        }
}
