package dk.jens.backup;

import android.content.SharedPreferences;
import android.util.SparseIntArray;
import dk.jens.backup.adapters.AppInfoAdapter;

import java.util.Comparator;

public class Sorter
{
    private AppInfoAdapter adapter;
    private FilteringMethod filteringMethod = FilteringMethod.ALL;
    private SortingMethod sortingMethod = SortingMethod.LABEL;
    // SparseIntArray is more memory efficient than mapping integers to integers using a hashmap
    private static final SparseIntArray convertFilteringIdMap, convertSortingIdMap;
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEdit;
    private int oldBackups = 0;
    public Sorter(AppInfoAdapter adapter, SharedPreferences prefs)
    {
        this.adapter = adapter;
        this.prefs = prefs;
        this.prefsEdit = prefs.edit();
        try
        {
            oldBackups = Integer.valueOf(prefs.getString(
                Constants.PREFS_OLDBACKUPS, "0"));
        }
        catch(NumberFormatException e)
        {}
    }
    static
    {
        convertFilteringIdMap = new SparseIntArray(3);
        convertFilteringIdMap.put(FilteringMethod.ALL.ordinal(), R.id.showAll);
        convertFilteringIdMap.put(FilteringMethod.SYSTEM.ordinal(), R.id.showOnlySystem);
        convertFilteringIdMap.put(FilteringMethod.USER.ordinal(), R.id.showOnlyUser);
    }
    static
    {
        convertSortingIdMap = new SparseIntArray(4);
        convertSortingIdMap.put(SortingMethod.LABEL.ordinal(), R.id.sortByLabel);
        convertSortingIdMap.put(SortingMethod.PACKAGENAME.ordinal(), R.id.sortByPackageName);
        convertSortingIdMap.put(SortingMethod.LASTUPDATED.ordinal(), R.id.sortByLastUpdated);
        convertSortingIdMap.put(SortingMethod.BACKUP_DATE.ordinal(), R.id.sortByBackupDate);
    }
    public enum FilteringMethod
    {
        ALL(R.id.showAll),
        SYSTEM(R.id.showOnlySystem),
        USER(R.id.showOnlyUser),
        NOTBACKEDUP(R.id.showNotBackedup),
        NOTINSTALLED(R.id.showNotInstalled),
        NEWANDUPDATED(R.id.showNewAndUpdated),
        OLDBACKUPS(R.id.showOldBackups),
        ONLYAPK(R.id.showOnlyApkBackedUp),
        ONLYDATA(R.id.showOnlyDataBackedUp),
        APKDATA(R.id.showAPKnDataBackedUp),
        ONLYSPECIAL(R.id.showOnlySpecialBackups);
        int id;
        FilteringMethod(int id)
        {
            this.id = id;
        }
        int getId()
        {
            return id;
        }
    }
    public enum SortingMethod
    {
        LABEL(R.id.sortByLabel),
        PACKAGENAME(R.id.sortByPackageName),
        LASTUPDATED(R.id.sortByLastUpdated),
        BACKUP_DATE(R.id.sortByBackupDate);
        int id;
        SortingMethod(int id)
        {
            this.id = id;
        }
        int getId()
        {
            return id;
        }
    }

    public static Comparator<AppInfo> appInfoLabelComparator = (m1, m2) ->
            m1.getLabel().compareToIgnoreCase(m2.getLabel());
    public static Comparator<AppInfo> appInfoPackageNameComparator = (m1, m2) ->
            m1.getPackageName().compareToIgnoreCase(m2.getPackageName());
    public static Comparator<AppInfo> appInfoLastUpdatedComparator = (m1, m2) ->
            Long.compare(m2.getLastUpdateTime(), m1.getLastUpdateTime());
    public static Comparator<AppInfo> appInfoBackupDateComparator = (m1, m2) -> {
        LogFile logInfoM1 = m1.getLogInfo();
        LogFile logInfoM2 = m2.getLogInfo();
        long lastBackupMillisM1 = -1, lastBackupMillisM2 = -1;
        if (logInfoM1 != null) {
            lastBackupMillisM1 = logInfoM1.getLastBackupMillis();
        }
        if (logInfoM2 != null) {
            lastBackupMillisM2 = logInfoM2.getLastBackupMillis();
        }
        return Long.compare(lastBackupMillisM2, lastBackupMillisM1);
    };

    public void sort(int id)
    {
        switch(id)
        {
            case R.id.showAll:
                filterShowAll();
                saveInPrefs("filteringId", filteringMethod.ordinal());
                break;
            case R.id.showOnlySystem:
                filteringMethod = FilteringMethod.SYSTEM;
                adapter.filterAppType(2);
                saveInPrefs("filteringId", filteringMethod.ordinal());
                break;
            case R.id.showOnlyUser:
                filteringMethod = FilteringMethod.USER;
                adapter.filterAppType(1);
                saveInPrefs("filteringId", filteringMethod.ordinal());
                break;
            case R.id.showNotBackedup:
                filteringMethod = FilteringMethod.NOTBACKEDUP;
                adapter.filterIsNotBackedup(prefs.getInt("filteringId", 0));
                break;
            case R.id.showNotInstalled:
                filteringMethod = FilteringMethod.NOTINSTALLED;
                adapter.filterIsInstalled();
                break;
            case R.id.showNewAndUpdated:
                filteringMethod = FilteringMethod.NEWANDUPDATED;
                adapter.filterNewAndUpdated();
                break;
            case R.id.showOldBackups:
                filteringMethod = FilteringMethod.OLDBACKUPS;
                adapter.filterOldApps(oldBackups);
                break;
            case R.id.showOnlyApkBackedUp:
                filteringMethod = FilteringMethod.ONLYAPK;
                adapter.filterPartialBackups(AppInfo.MODE_APK);
                break;
            case R.id.showOnlyDataBackedUp:
                filteringMethod = FilteringMethod.ONLYDATA;
                adapter.filterPartialBackups(AppInfo.MODE_DATA);
                break;
            case R.id.showAPKnDataBackedUp:
                filteringMethod = FilteringMethod.APKDATA;
                adapter.filterPartialBackups(AppInfo.MODE_BOTH);
                break;
            case R.id.showOnlySpecialBackups:
                filteringMethod = FilteringMethod.ONLYSPECIAL;
                adapter.filterSpecialBackups();
                break;
            case R.id.sortByLabel:
                sortingMethod = SortingMethod.LABEL;
                adapter.sortByLabel();
                sort(filteringMethod.getId());
                saveInPrefs("sortingId", sortingMethod.ordinal());
                break;
            case R.id.sortByPackageName:
                sortingMethod = SortingMethod.PACKAGENAME;
                adapter.sortByPackageName();
                sort(filteringMethod.getId());
                saveInPrefs("sortingId", sortingMethod.ordinal());
                break;
            case R.id.sortByLastUpdated:
                sortingMethod = SortingMethod.LASTUPDATED;
                adapter.sortByLastUpdated();
                sort(filteringMethod.getId());
                saveInPrefs("sortingId", sortingMethod.ordinal());
                break;
            case R.id.sortByBackupDate:
                sortingMethod = SortingMethod.BACKUP_DATE;
                adapter.sortByBackupDate();
                sort(filteringMethod.getId());
                saveInPrefs("sortingId", sortingMethod.ordinal());
                break;
        }
    }
    public void filterShowAll()
    {
        filteringMethod = FilteringMethod.ALL;
        adapter.filterAppType(0);
    }
    public FilteringMethod getFilteringMethod()
    {
        if(filteringMethod != null)
        {
            return filteringMethod;
        }
        return FilteringMethod.ALL;
    }
    public SortingMethod getSortingMethod()
    {
        if(sortingMethod != null)
        {
            return sortingMethod;
        }
        return SortingMethod.PACKAGENAME;
    }
    public void saveInPrefs(String prefName, int persistentId)
    {
        prefsEdit.putInt(prefName, persistentId);
        prefsEdit.commit();
    }
    // needs to be static as it is used in onPrepareOptionsMenu which is called before the sorter instance is created
    public static int convertFilteringId(int key)
    {
        // SparseIntArray returns 0 if the key is not found
        return convertFilteringIdMap.get(key);
    }
    public static int convertSortingId(int key)
    {
        return convertSortingIdMap.get(key);
    }
}