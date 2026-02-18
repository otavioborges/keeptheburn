package ca.mopicaltechtronic.keeptheburn;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPreferences {

    private static final String PREF_NAME = "ble_hr_prefs";
    private static final String KEY_MAC_ADDRESS = "mac_address";
    private final SharedPreferences prefs;

    public AppPreferences(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveMacAddress(String mac) {
        prefs.edit().putString(KEY_MAC_ADDRESS, mac).apply();
    }

    public String getMacAddress() {
        return prefs.getString(KEY_MAC_ADDRESS, null);
    }

    public void clearMacAddress() {
        prefs.edit().remove(KEY_MAC_ADDRESS).apply();
    }
}
