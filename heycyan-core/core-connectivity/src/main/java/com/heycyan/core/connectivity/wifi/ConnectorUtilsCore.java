package com.heycyan.core.connectivity.wifi;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import java.util.List;
import java.util.Objects;

public final class ConnectorUtilsCore {
    private ConnectorUtilsCore() {
    }

    /**
     * Legacy configured-network APIs remain usable for this library's supported Android 7-9
     * devices. Android 10+ blocks these calls for normal apps targeting Android 10 or newer.
     */
    static boolean supportsLegacyConfiguredNetworkApis() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    public static boolean isHexWepKey(String wepKey) {
        int length = wepKey.length();
        if (length != 10 && length != 26 && length != 58) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = wepKey.charAt(i);
            if ((c < '0' || c > '9') && ((c < 'a' || c > 'f') && (c < 'A' || c > 'F'))) {
                return false;
            }
        }
        return true;
    }

    public static void reEnableNetworkIfPossible(WifiManager wifiMgr, ScanResult scanResult) {
        String bssid;
        if (!supportsLegacyConfiguredNetworkApis()
                || wifiMgr == null
                || scanResult == null
                || (bssid = scanResult.BSSID) == null) {
            return;
        }
        enableNetwork(wifiMgr, bssid, false);
    }

    @SuppressWarnings("deprecation")
    private static void enableNetwork(WifiManager wifiMgr, String bssid, boolean disableOthers) {
        List<WifiConfiguration> configuredNetworks = wifiMgr.getConfiguredNetworks();
        if (configuredNetworks == null) {
            return;
        }
        for (WifiConfiguration config : configuredNetworks) {
            String currentBssid = config.BSSID;
            if (currentBssid != null && currentBssid.equals(bssid)) {
                wifiMgr.enableNetwork(config.networkId, disableOthers);
            } else if (disableOthers) {
                wifiMgr.disableNetwork(config.networkId);
            }
        }
    }

    public static void reEnableNetworkIfPossible(WifiManager wifiMgr, String bssid) {
        if (!supportsLegacyConfiguredNetworkApis() || wifiMgr == null || bssid == null) {
            return;
        }
        enableNetwork(wifiMgr, bssid, false);
    }

    @SuppressWarnings("deprecation")
    public static int getMaxPriority(WifiManager wifiManager) {
        if (!supportsLegacyConfiguredNetworkApis() || wifiManager == null) {
            return 0;
        }
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        int max = 0;
        if (configuredNetworks == null) {
            return 0;
        }
        for (WifiConfiguration config : configuredNetworks) {
            if (config.priority > max) {
                max = config.priority;
            }
        }
        return max;
    }

    /**
     * Creates the legacy saved-network representation used on Android 9 and older.
     * Callers on Android 10+ should use {@link #createWifiNetworkSpecifier(String, String, String)}
     * for an app-scoped local/infrastructure connection, or a suggestion/user-approved saved
     * network flow when persistent auto-connect semantics are required.
     */
    @SuppressWarnings("deprecation")
    public static WifiConfiguration createWifiConfiguration(String security, String ssid, String password) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = SSIDUtils.convertToQuotedString(ssid);
        ConfigSecuritiesCore.setupSecurity(config, security, password);
        return config;
    }

    /**
     * Builds an Android 10+ app-scoped Wi-Fi network request specifier.
     *
     * <p>This is an infrastructure/local-only Wi-Fi helper. It is intentionally separate from
     * the HeyCyan Wi-Fi Direct (P2P) transport in {@code com.heycyan.core.connectivity.p2p}.</p>
     */
    public static WifiNetworkSpecifier createWifiNetworkSpecifier(String security, String ssid, String password) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw new UnsupportedOperationException("WifiNetworkSpecifier requires Android 10 or newer");
        }
        if (ssid == null || ssid.isEmpty()) {
            throw new IllegalArgumentException("SSID must not be empty");
        }
        WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder().setSsid(ssid);
        ConfigSecuritiesCore.setupWifiNetworkSpecifierSecurities(builder, security, password);
        return builder.build();
    }

    @SuppressWarnings("deprecation")
    public static int saveNetwork(WifiManager wifiManager, WifiConfiguration config) {
        if (!supportsLegacyConfiguredNetworkApis() || wifiManager == null || config == null) {
            return -1;
        }
        WifiConfiguration existing = ConfigSecuritiesCore.getWifiConfiguration(wifiManager, config);
        if (existing != null) {
            // minSdk is 24, so the old pre-23 updateNetwork branch was unreachable. Preserve the
            // effective behavior: an existing matching configuration keeps its current network ID.
            return existing.networkId;
        }
        return wifiManager.addNetwork(config);
    }

    /**
     * Retains the legacy connection-info check without changing this long-standing API signature.
     * Modern callers that already own a ConnectivityManager Network should inspect that Network's
     * capabilities/transport info directly instead of using this compatibility helper.
     */
    @SuppressWarnings("deprecation")
    public static boolean isAlreadyConnected(WifiManager wifiManager, String bssid) {
        if (wifiManager == null || bssid == null) {
            return false;
        }
        WifiInfo connectionInfo = wifiManager.getConnectionInfo();
        if (connectionInfo == null
                || connectionInfo.getBSSID() == null
                || connectionInfo.getIpAddress() == 0
                || !Objects.equals(bssid, connectionInfo.getBSSID())) {
            return false;
        }
        WifiUtils.wifiLog(
                "Already connected to: "
                        + connectionInfo.getSSID()
                        + "  BSSID: "
                        + connectionInfo.getBSSID());
        return true;
    }

    public static boolean isAlreadyConnected(WifiManager wifiManager, ScanResult scanResult) {
        if (scanResult == null) {
            return false;
        }
        return isAlreadyConnected(wifiManager, scanResult.BSSID);
    }

    @SuppressWarnings("deprecation")
    public static void disconnectFromAll(WifiManager wifiManager) {
        if (!supportsLegacyConfiguredNetworkApis() || wifiManager == null) {
            return;
        }
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (configuredNetworks != null) {
            for (WifiConfiguration config : configuredNetworks) {
                wifiManager.disableNetwork(config.networkId);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static WifiConfiguration disableOthers(WifiManager wifiManager, WifiConfiguration config) {
        if (!supportsLegacyConfiguredNetworkApis() || wifiManager == null || config == null) {
            return config;
        }
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (configuredNetworks != null && config.SSID != null) {
            for (WifiConfiguration next : configuredNetworks) {
                if (!config.SSID.equals(next.SSID)) {
                    wifiManager.disableNetwork(next.networkId);
                }
            }
        }
        return config;
    }
}
