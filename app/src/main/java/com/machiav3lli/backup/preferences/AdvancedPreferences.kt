package com.machiav3lli.backup.preferences

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.machiav3lli.backup.MAIN_FILTER_DEFAULT
import com.machiav3lli.backup.PREFS_ALLOWDOWNGRADE
import com.machiav3lli.backup.PREFS_ALLOWSHADOWINGDEFAULT
import com.machiav3lli.backup.PREFS_ASSEMBLEFILELISTONESTEP
import com.machiav3lli.backup.PREFS_BACKUPTARCMD
import com.machiav3lli.backup.PREFS_CACHEFILELISTS
import com.machiav3lli.backup.PREFS_CACHEONUPDATE
import com.machiav3lli.backup.PREFS_CACHEPACKAGES
import com.machiav3lli.backup.PREFS_CACHEURIS
import com.machiav3lli.backup.PREFS_CANCELONSTART
import com.machiav3lli.backup.PREFS_CATCHUNCAUGHTEXCEPTION
import com.machiav3lli.backup.PREFS_COLUMNNAMESAF
import com.machiav3lli.backup.PREFS_DELAYBEFOREREFRESHAPPINFO
import com.machiav3lli.backup.PREFS_DISABLEVERIFICATION
import com.machiav3lli.backup.PREFS_ENABLESPECIALBACKUPS
import com.machiav3lli.backup.PREFS_FINDLS
import com.machiav3lli.backup.PREFS_INVALIDATESELECTIVE
import com.machiav3lli.backup.PREFS_MAXCRASHLINES
import com.machiav3lli.backup.PREFS_MAXRETRIESPERPACKAGE
import com.machiav3lli.backup.PREFS_PAUSEAPPS
import com.machiav3lli.backup.PREFS_PMSUSPEND
import com.machiav3lli.backup.PREFS_REFRESHAPPINFOTIMEOUT
import com.machiav3lli.backup.PREFS_RESTOREAVOIDTEMPCOPY
import com.machiav3lli.backup.PREFS_RESTORETARCMD
import com.machiav3lli.backup.PREFS_RESTOREWITHALLPERMISSIONS
import com.machiav3lli.backup.PREFS_SHADOWROOTFILE
import com.machiav3lli.backup.PREFS_STRICTHARDLINKS
import com.machiav3lli.backup.PREFS_USEALARMCLOCK
import com.machiav3lli.backup.PREFS_USEEXACTALARM
import com.machiav3lli.backup.PREFS_USEEXPEDITED
import com.machiav3lli.backup.PREFS_USEFOREGROUND
import com.machiav3lli.backup.R
import com.machiav3lli.backup.ui.compose.item.PreferencesGroupHeader
import com.machiav3lli.backup.ui.compose.item.SeekBarPreference
import com.machiav3lli.backup.ui.compose.item.SwitchPreference
import com.machiav3lli.backup.ui.compose.theme.AppTheme
import com.machiav3lli.backup.ui.compose.theme.ColorDeData
import com.machiav3lli.backup.ui.compose.theme.ColorSpecial
import com.machiav3lli.backup.ui.compose.theme.ColorUpdated
import com.machiav3lli.backup.ui.item.BooleanPref
import com.machiav3lli.backup.ui.item.IntPref
import com.machiav3lli.backup.ui.item.Pref
import com.machiav3lli.backup.utils.sortFilterModel

@Composable
fun AdvancedPrefsPage() {
    val context = LocalContext.current
    var (expanded, expand) = remember { mutableStateOf(false) }

    val prefs = Pref.preferences["adv"] ?: listOf()
    val devOptions = Pref.preferences["dev"] ?: listOf()

    AppTheme(
        darkTheme = isSystemInDarkTheme()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = prefs) {
                when (it) {
                    EnableSpecialsPref -> SwitchPreference(pref = it as BooleanPref) {
                        val newModel = context.sortFilterModel
                        newModel.mainFilter = newModel.mainFilter and MAIN_FILTER_DEFAULT
                        context.sortFilterModel = newModel
                    }
                    is BooleanPref -> SwitchPreference(pref = it)
                    is IntPref -> SeekBarPreference(pref = it)
                }
            }
            item {
                PreferencesGroupHeader(
                    titleId = R.string.prefs_dev_settings,
                    summaryId = R.string.prefs_dev_settings_summary,
                    iconId = R.drawable.ic_force_kill
                ) {
                    expand(!expanded)
                }
            }
            // TODO add Dev options expandable holder
            if (expanded) items(items = devOptions) {
                when (it) {
                    is BooleanPref -> SwitchPreference(pref = it)
                    is IntPref     -> SeekBarPreference(pref = it)
                }
            }
        }
    }
}

val EnableSpecialsPref = BooleanPref(
    key = "adv." + PREFS_ENABLESPECIALBACKUPS,
    titleId = R.string.prefs_enablespecial,
    summaryId = R.string.prefs_enablespecial_summary,
    iconId = R.drawable.ic_special,
    iconTint = ColorSpecial,
    defaultValue = false
)

val DisableVerificationPref = BooleanPref(
    key = "adv." + PREFS_DISABLEVERIFICATION,
    titleId = R.string.prefs_disableverification,
    summaryId = R.string.prefs_disableverification_summary,
    iconId = R.drawable.ic_andy,
    iconTint = ColorUpdated,
    defaultValue = true
)

val RestoreAllPermissionsPref = BooleanPref(
    key = "adv." + PREFS_RESTOREWITHALLPERMISSIONS,
    titleId = R.string.prefs_restoreallpermissions,
    summaryId = R.string.prefs_restoreallpermissions_summary,
    iconId = R.drawable.ic_de_data,
    iconTint = ColorDeData,
    defaultValue = false
)

val AllowDowngradePref = BooleanPref(
    key = "adv." + PREFS_ALLOWDOWNGRADE,
    titleId = R.string.prefs_allowdowngrade,
    summaryId = R.string.prefs_allowdowngrade_summary,
    iconId = R.drawable.ic_restore,
    defaultValue = false
)

// dev settings

val pref_tapToSelect = BooleanPref(
    key = "dev.tapToSelect",
    summary = "a short tap selects, otherwise a long tap starts selection mode [switch tabs to activate]",
    defaultValue = false
)

val pref_showInfoLogBar = BooleanPref(
    key = "dev.showInfoLogBar",
    summaryId = R.string.prefs_showinfologbar_summary,
    defaultValue = false
)

val CachePackagePref = BooleanPref(
    key = "dev." + PREFS_CACHEPACKAGES,
    summaryId = R.string.prefs_cachepackages_summary,
    defaultValue = true
)

val UsePackageCacheOnUpdatePref = BooleanPref(
    key = "dev." + PREFS_CACHEONUPDATE,
    summaryId = R.string.prefs_usepackagecacheonupdate_summary,
    defaultValue = false
)

val UseColumnNameSAFPref = BooleanPref(
    key = "dev." + PREFS_COLUMNNAMESAF,
    summaryId = R.string.prefs_usecolumnnamesaf_summary,
    defaultValue = true
)

val CancelOnStartPref = BooleanPref(
    key = "dev." + PREFS_CANCELONSTART,
    summaryId = R.string.prefs_cancelonstart_summary,
    defaultValue = false
)

val UseAlarmClockPref = BooleanPref(
    key = "dev." + PREFS_USEALARMCLOCK,
    summaryId = R.string.prefs_usealarmclock_summary,
    defaultValue = false
)

val UseExactAlarmPref = BooleanPref(
    key = "dev." + PREFS_USEEXACTALARM,
    summaryId = R.string.prefs_useexactalarm_summary,
    defaultValue = false
)

val PauseAppPref = BooleanPref(
    key = "dev." + PREFS_PAUSEAPPS,
    summaryId = R.string.prefs_pauseapps_summary,
    defaultValue = true
)

val SuspendAppPref = BooleanPref(
    key = "dev." + PREFS_PMSUSPEND,
    summaryId = R.string.prefs_pmsuspend_summary,
    defaultValue = false
)

val BackupTarCmdPref = BooleanPref(
    key = "dev." + PREFS_BACKUPTARCMD,
    summaryId = R.string.prefs_backuptarcmd_summary,
    defaultValue = true
)

val RestoreTarCmdPref = BooleanPref(
    key = "dev." + PREFS_RESTORETARCMD,
    summaryId = R.string.prefs_restoretarcmd_summary,
    defaultValue = true
)

val StrictHardLinksPref = BooleanPref(
    key = "dev." + PREFS_STRICTHARDLINKS,
    summaryId = R.string.prefs_stricthardlinks_summary,
    defaultValue = false
)

val RestoreAvoidTempCopyPref = BooleanPref(
    key = "dev." + PREFS_RESTOREAVOIDTEMPCOPY,
    summaryId = R.string.prefs_restoreavoidtempcopy_summary,
    defaultValue = false
)

val ShadowRootFilePref = BooleanPref(
    key = "dev." + PREFS_SHADOWROOTFILE,
    summaryId = R.string.prefs_shadowrootfile_summary,
    defaultValue = false
)

val AllowShadowingDefaultPref = BooleanPref(
    key = "dev." + PREFS_ALLOWSHADOWINGDEFAULT,
    summaryId = R.string.prefs_allowshadowingdefault_summary,
    defaultValue = false
)

val UseFindLsPref = BooleanPref(
    key = "dev." + PREFS_FINDLS,
    summaryId = R.string.prefs_usefindls_summary,
    defaultValue = true
)

val AssembleFileListOneStepPref = BooleanPref(
    key = "dev." + PREFS_ASSEMBLEFILELISTONESTEP,
    summaryId = R.string.prefs_assemblefilelistonestep_summary,
    defaultValue = true
)

val CatchUncaughtExceptionPref = BooleanPref(
    key = "dev." + PREFS_CATCHUNCAUGHTEXCEPTION,
    summaryId = R.string.prefs_catchuncaughtexception_summary,
    defaultValue = false
)

val MaxCrashLinesPref = IntPref(
    key = "dev." + PREFS_MAXCRASHLINES,
    summaryId = R.string.prefs_maxcrashlines_summary,
    entries = (10..200 step 10).toList(),
    defaultValue = 50
)

val InvalidateSelectivePref = BooleanPref(
    key = "dev." + PREFS_INVALIDATESELECTIVE,
    summaryId = R.string.prefs_invalidateselective_summary,
    defaultValue = true
)

val CacheUrisPref = BooleanPref(
    key = "dev." + PREFS_CACHEURIS,
    summaryId = R.string.prefs_cacheuris_summary,
    defaultValue = true
)

val CacheFileListsPref = BooleanPref(
    key = "dev." + PREFS_CACHEFILELISTS,
    summaryId = R.string.prefs_cachefilelists_summary,
    defaultValue = true
)

val MaxRetriesPerPackagePref = IntPref(
    key = "dev." + PREFS_MAXRETRIESPERPACKAGE,
    summaryId = R.string.prefs_maxretriesperpackage_summary,
    entries = (0..10).toList(),
    defaultValue = 1
)

val DelayBeforeRefreshAppInfoPref = IntPref(
    key = "dev." + PREFS_DELAYBEFOREREFRESHAPPINFO,
    summaryId = R.string.prefs_delaybeforerefreshappinfo_summary,
    entries = (0..30).toList(),
    defaultValue = 0
)

val RefreshAppInfoTimeoutPref = IntPref(
    key = "dev." + PREFS_REFRESHAPPINFOTIMEOUT,
    summaryId = R.string.prefs_refreshappinfotimeout_summary,
    entries = ((0..9 step 1) + (10..120 step 10)).toList(),
    defaultValue = 30
)

val pref_useWorkManagerForSingleManualJob = BooleanPref(
    key = "dev.useWorkManagerForSingleManualJob",
    summary = "queue single manual jobs",
    defaultValue = false
)

val UseForegroundPref = BooleanPref(
    key = "dev." + PREFS_USEFOREGROUND,
    summaryId = R.string.prefs_useforeground_summary,
    defaultValue = true
)

val UseExpeditedPref = BooleanPref(
    key = "dev." + PREFS_USEEXPEDITED,
    summaryId = R.string.prefs_useexpedited_summary,
    defaultValue = true
)

val pref_fakeBackupSeconds = IntPref(
    key = "dev.fakeBackupSeconds",
    summary = "[seconds] time for faked backups, 0 = do not fake",
    entries = ((0..9 step 1) + (10..50 step 10) + (60..1200 step 60)).toList(),
    defaultValue = 0
)
