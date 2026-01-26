package com.sdk.glassessdksample.ui.wifi;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import com.sdk.glassessdksample.ui.wifi.utils.SSIDUtils;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/* loaded from: classes.dex */
public final class ConnectorUtils {
    private static final int MAX_PRIORITY = 99999;

    private ConnectorUtils() {
    }

    public static boolean isHexWepKey(String wepKey) {
        int length = wepKey.length();
        if (length != 10 && length != 26 && length != 58) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char charAt = wepKey.charAt(i);
            if ((charAt < '0' || charAt > '9') && ((charAt < 'a' || charAt > 'f') && (charAt < 'A' || charAt > 'F'))) {
                return false;
            }
        }
        return true;
    }

    public static void reEnableNetworkIfPossible(final WifiManager wifiMgr, final ScanResult scanResult) {
        String str;
        if (wifiMgr == null || scanResult == null || (str = scanResult.BSSID) == null) {
            return;
        }
        enableNetwork(wifiMgr, str, false);
    }

    private static void enableNetwork(final WifiManager wifiMgr, final String bssid, final boolean disableOthers) {
        for (WifiConfiguration wifiConfiguration : wifiMgr.getConfiguredNetworks()) {
            String str = wifiConfiguration.BSSID;
            if (str != null && str.equals(bssid)) {
                wifiMgr.enableNetwork(wifiConfiguration.networkId, disableOthers);
            } else if (disableOthers) {
                wifiMgr.disableNetwork(wifiConfiguration.networkId);
            }
        }
    }

    public static void reEnableNetworkIfPossible(final WifiManager wifiMgr, final String bssid) {
        if (wifiMgr == null || bssid == null) {
            return;
        }
        enableNetwork(wifiMgr, bssid, false);
    }

    public static int getMaxPriority(final WifiManager wifiManager) {
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        int i = 0;
        if (configuredNetworks == null) {
            return 0;
        }
        for (WifiConfiguration wifiConfiguration : configuredNetworks) {
            if (wifiConfiguration.priority > i) {
                i = wifiConfiguration.priority;
            }
        }
        return i;
    }

    public static WifiConfiguration createWifiConfiguration(String security, final String ssid, final String password) {
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.SSID = SSIDUtils.convertToQuotedString(ssid);
        ConfigSecurities.setupSecurity(wifiConfiguration, security, password);
        return wifiConfiguration;
    }

    public static int saveNetwork(final WifiManager wifiManager, final WifiConfiguration config) {
        if (wifiManager == null || config == null) {
            return -1;
        }
        WifiConfiguration wifiConfiguration = ConfigSecurities.getWifiConfiguration(wifiManager, config);
        if (wifiConfiguration != null) {
            if (Build.VERSION.SDK_INT >= 23) {
                return wifiConfiguration.networkId;
            }
            config.networkId = wifiConfiguration.networkId;
            wifiManager.updateNetwork(config);
            return wifiConfiguration.networkId;
        }
        return wifiManager.addNetwork(config);
    }

    public static boolean isAlreadyConnected(WifiManager wifiManager, String bssid) {
        if (wifiManager == null || wifiManager.getConnectionInfo() == null || wifiManager.getConnectionInfo().getBSSID() == null || wifiManager.getConnectionInfo().getIpAddress() == 0 || bssid == null || !Objects.equals(bssid, wifiManager.getConnectionInfo().getBSSID())) {
            return false;
        }
        WifiUtils.wifiLog("Already connected to: " + wifiManager.getConnectionInfo().getSSID() + "  BSSID: " + wifiManager.getConnectionInfo().getBSSID());
        return true;
    }

    public static boolean isAlreadyConnected(WifiManager wifiManager, ScanResult scanResult) {
        if (scanResult == null) {
            return false;
        }
        return isAlreadyConnected(wifiManager, scanResult.BSSID);
    }

    public static void disconnectFromAll(final WifiManager wifiManager) {
        if (wifiManager == null) {
            return;
        }
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (configuredNetworks != null) {
            for (WifiConfiguration wifiConfiguration : configuredNetworks) {
                wifiManager.disableNetwork(wifiConfiguration.networkId);
            }
        }
    }

    public static WifiConfiguration disableOthers(final WifiManager wifiManager, final WifiConfiguration config) {
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (configuredNetworks != null && config.SSID != null) {
            Iterator<WifiConfiguration> it = configuredNetworks.iterator();
            while (it.hasNext()) {
                WifiConfiguration next = it.next();
                if (!config.SSID.equals(next.SSID)) {
                    wifiManager.disableNetwork(next.networkId);
                }
            }
        }
        return config;
    }
}
