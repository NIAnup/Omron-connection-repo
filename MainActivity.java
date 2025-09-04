//
//package com.updevelop.wellness_z_mvvm;
//
//import android.os.Bundle;
//import android.content.SharedPreferences;
//import android.util.Log;
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.content.BroadcastReceiver;
//import android.location.LocationManager;
//import androidx.annotation.NonNull;
//import io.flutter.embedding.android.FlutterFragmentActivity;
//import io.flutter.embedding.engine.FlutterEngine;
//import io.flutter.plugin.common.MethodChannel;
//import com.omronhealthcare.OmronConnectivityLibrary.OmronLibrary.LibraryManager.OmronPeripheralManager;
//import com.omronhealthcare.OmronConnectivityLibrary.OmronLibrary.Model.OmronPeripheral;
//import com.omronhealthcare.OmronConnectivityLibrary.OmronLibrary.Model.OmronErrorInfo;
//import com.omronhealthcare.OmronConnectivityLibrary.OmronLibrary.DeviceConfiguration.OmronPeripheralManagerConfig;
//import com.omronhealthcare.OmronConnectivityLibrary.OmronLibrary.OmronUtility.OmronConstants;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class MainActivity extends FlutterFragmentActivity {
//    private static final String CHANNEL = "omron_channel";
//    private OmronPeripheral currentPeripheral;
//    private List<OmronPeripheral> lastScannedPeripherals;
//    private String currentUserHashId = "testuser@wellnessz.com"; // Default user hash ID
//    private static final String PREFS = "OMRON_PREFS";
//    private static final String KEY_PAIRED_ADDR = "paired_device_address";
//
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        Log.e("OMRON", "Initializing...");
//        OmronPeripheralManager.sharedManager(this)
//                .setAPIKey("B7231051-501E-4CD6-8589-5394985B9E41", null);
//        Log.e("OMRON", "API Key set");
//        OmronPeripheralManagerConfig config = new OmronPeripheralManagerConfig();
//        config.userHashId = currentUserHashId;
//        config.timeoutInterval = 90;
//        OmronPeripheralManager.sharedManager(this).setConfiguration(config);
//        try {
//            OmronPeripheralManager.sharedManager(this).startManager();
//            Log.e("OMRON", "Manager started");
//        } catch (Exception e) {
//            Log.e("OMRON", "startManager() failed: " + e.getMessage());
//        }
//        // ✅ Register Bluetooth pairing receiver
//        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
//        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
//        registerReceiver(mPairingRequestReceiver, filter);
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        // ✅ Unregister pairing receiver to avoid leaks
//        unregisterReceiver(mPairingRequestReceiver);
//    }
//
//    @Override
//    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
//        super.configureFlutterEngine(flutterEngine);
//        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
//                .setMethodCallHandler((call, result) -> {
//                    switch (call.method) {
//                        case "startScan":
//                            startScan(result);
//                            break;
//                        case "connectDevice":
//                            HashMap<String, Object> args = (HashMap<String, Object>) call.arguments;
//                            String identifier = (String) args.get("identifier");
//                            HashMap<String, Object> userSettings = (HashMap<String, Object>) args.get("userSettings");
//                            connectToDevice(result, identifier, userSettings);
//                            break;
//                        case "transferData":
//                            transferData(result);
//                            break;
//                        case "setUserHashId":
//                            String userHashId = (String) call.arguments;
//                            setUserHashId(userHashId, result);
//                            break;
//                        case "startMeasurement": // New method to trigger measurement if supported
//                            startMeasurement(result);
//                            break;
//                        default:
//                            result.notImplemented();
//                    }
//                });
//    }
//
//    private void startScan(MethodChannel.Result result) {
//        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
//            result.error("BLUETOOTH_ERROR", "Bluetooth not available/enabled.", null);
//            return;
//        }
//
//        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//        boolean gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
//        boolean networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
//        if (!gpsEnabled && !networkEnabled) {
//            result.error("LOCATION_OFF", "Location services are not enabled.", null);
//            return;
//        }
//
//        Log.e("OMRON", "Scanning...");
//        String knownAddr = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PAIRED_ADDR, null);
//
//        OmronPeripheralManagerConfig config = OmronPeripheralManager.sharedManager(this).getConfiguration();
//        config.userHashId = currentUserHashId;
//        OmronPeripheralManager.sharedManager(this).setConfiguration(config);
//
//        final boolean[] hasReplied = {false};
//
//        OmronPeripheralManager.sharedManager(this)
//                .startScanPeripherals((peripherals, errorInfo) -> {
//                    if (hasReplied[0]) return;
//
//                    // ✅ Auto-connect if known device saved
//                    if (knownAddr != null && peripherals != null) {
//                        for (OmronPeripheral p : peripherals) {
//                            if (p.getLocalName() != null && p.getLocalName().equalsIgnoreCase(knownAddr)) {
//                                Log.e("OMRON", "Auto-connecting to previously paired device: " + p.getLocalName());
//
//                                OmronPeripheralManager.sharedManager(this)
//                                        .connectPeripheral(p, true, (periph, err) -> {
//                                            if (err.isSuccess()) {
//                                                currentPeripheral = periph;
//
//                                                // Return device info list for Flutter
//                                                ArrayList<Map<String, String>> autoList = new ArrayList<>();
//                                                HashMap<String, String> deviceInfo = new HashMap<>();
//                                                deviceInfo.put("name", periph.getLocalName());
//                                                deviceInfo.put("identifier", periph.getLocalName());
//                                                autoList.add(deviceInfo);
//
//                                                result.success(autoList);
//                                            } else {
//                                                result.error("AUTO_CONNECT_FAILED", err.getMessageInfo(), null);
//                                            }
//                                        });
//
//                                hasReplied[0] = true;
//                                return;
//                            }
//                        }
//                    }
//
//                    // Normal scan result fallback
//                    if (errorInfo != null && !errorInfo.isSuccess()) {
//                        result.error("SCAN_ERROR", errorInfo.getMessageInfo(), null);
//                        hasReplied[0] = true;
//                        return;
//                    }
//                    if (peripherals == null || peripherals.isEmpty()) {
//                        result.error("NO_DEVICES", "No devices found.", null);
//                        hasReplied[0] = true;
//                        return;
//                    }
//
//                    ArrayList<Map<String, String>> deviceList = new ArrayList<>();
//                    for (OmronPeripheral p : peripherals) {
//                        HashMap<String, String> deviceInfo = new HashMap<>();
//                        deviceInfo.put("name", p.getLocalName());
//                        deviceInfo.put("identifier", p.getLocalName());
//                        deviceList.add(deviceInfo);
//                    }
//                    lastScannedPeripherals = peripherals;
//                    result.success(deviceList);
//                    hasReplied[0] = true;
//                });
//    }
//
//    private BluetoothDevice findBtDeviceByName(String deviceName) {
//        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
//        if (adapter == null || !adapter.isEnabled()) return null;
//
//        for (BluetoothDevice dev : adapter.getBondedDevices()) {
//            if (dev.getName() != null && dev.getName().equalsIgnoreCase(deviceName)) {
//                return dev; // ✅ return the bonded BluetoothDevice
//            }
//        }
//        return null;
//    }
//
//    private void connectToDevice(MethodChannel.Result result, String identifier, HashMap<String, Object> userSettings) {
//        if (lastScannedPeripherals == null || lastScannedPeripherals.isEmpty()) {
//            result.error("NO_DEVICE", "No scanned devices. You must scan before connecting.", null);
//            return;
//        }
//
//        OmronPeripheral targetPeripheral = null;
//        for (OmronPeripheral p : lastScannedPeripherals) {
//            if (p.getLocalName() != null && p.getLocalName().equalsIgnoreCase(identifier)) {
//                targetPeripheral = p;
//                break;
//            }
//        }
//
//        if (targetPeripheral == null) {
//            result.error("NO_DEVICE", "Device not found in scanned devices.", null);
//            return;
//        }
//
//        Log.e("OMRON", "Connecting to " + identifier);
//
//        OmronPeripheralManagerConfig config = OmronPeripheralManager.sharedManager(this).getConfiguration();
//        config.userHashId = currentUserHashId;
//
//        if (userSettings != null) {
//            HashMap<String, Object> settings = new HashMap<>();
//
//            if (userSettings.containsKey("height")) {
//                settings.put(OmronConstants.OMRONDevicePersonalSettings.UserHeightKey,
//                        userSettings.get("height"));
//            }
//            if (userSettings.containsKey("gender")) {
//                String gender = (String) userSettings.get("gender");
//                if ("Male".equalsIgnoreCase(gender)) {
//                    settings.put(OmronConstants.OMRONDevicePersonalSettings.UserGenderKey,
//                            OmronConstants.OMRONDevicePersonalSettingsUserGenderType.Male);
//                } else {
//                    settings.put(OmronConstants.OMRONDevicePersonalSettings.UserGenderKey,
//                            OmronConstants.OMRONDevicePersonalSettingsUserGenderType.Female);
//                }
//            }
//            if (userSettings.containsKey("dateOfBirth")) {
//                settings.put(OmronConstants.OMRONDevicePersonalSettings.UserDateOfBirthKey,
//                        userSettings.get("dateOfBirth"));
//            }
//
//            HashMap<String, HashMap> omronUserSettings = new HashMap<>();
//            omronUserSettings.put(OmronConstants.OMRONDevicePersonalSettingsKey, settings);
//
//            ArrayList<HashMap> deviceSettings = new ArrayList<>();
//            deviceSettings.add(omronUserSettings);
//
//            config.deviceSettings = deviceSettings;
//        }
//
//
//        OmronPeripheralManager.sharedManager(this).setConfiguration(config);
//
//        final boolean[] hasReplied = {false};
//        OmronPeripheralManager.sharedManager(this)
//                .connectPeripheral(targetPeripheral, true, (p, errorInfo) -> {
//                    if (hasReplied[0]) return;
//                    hasReplied[0] = true;
//
//                    if (errorInfo.isSuccess()) {
//                        currentPeripheral = p;
//                        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
//                        BluetoothDevice btDevice = findBtDeviceByName(p.getLocalName());
//                        if (btDevice != null) {
//                            prefs.edit()
//                                    .putString(KEY_PAIRED_ADDR, btDevice.getAddress()) // ✅ Save MAC instead of localName
//                                    .putString("user_height", (String) userSettings.get("height"))
//                                    .putString("user_gender", (String) userSettings.get("gender"))
//                                    .putString("user_dob", (String) userSettings.get("dateOfBirth"))
//                                    .apply();
//                        } else {
//                            Log.w("OMRON", "Could not find BluetoothDevice for " + p.getLocalName());
//                            prefs.edit()
//                                    .putString(KEY_PAIRED_ADDR, p.getLocalName()) // fallback to localName
//                                    .apply();
//                        }
//
//
//                        result.success("Connected to " + p.getLocalName());
//                    } else {
//                        result.error("CONNECT_FAILED", errorInfo.getMessageInfo(), null);
//                    }
//                });
//    }
//
//
//
//    private void startMeasurement(MethodChannel.Result result) {
//        if (currentPeripheral == null) {
//            result.error("NO_DEVICE", "No connected device to start measurement.", null);
//            return;
//        }
//        Log.e("OMRON", "Attempting to start measurement on " + currentPeripheral.getLocalName() + "...");
//        // This is a placeholder. The Omron SDK might have a specific method to trigger a measurement.
//        // If not, the user must manually trigger the measurement on the physical device.
//        // For HBF-222T, typically the user stands on the scale to initiate measurement.
//        // We will assume that standing on the machine will trigger the measurement and then data can be transferred.
//        result.success("Measurement initiated (user should stand on machine).");
//    }
//
//    private void transferData(MethodChannel.Result result) {
//        if (currentPeripheral == null) {
//            result.error("NO_DEVICE", "No connected device.", null);
//            return;
//        }
//        Log.e("OMRON", "Fetching stored measurement data for " + currentUserHashId + "...");
//        OmronPeripheralManagerConfig config = OmronPeripheralManager.sharedManager(this).getConfiguration();
//        config.userHashId = currentUserHashId; // Use current user hash ID
//        config.enableAllDataRead = true;
//        OmronPeripheralManager.sharedManager(this).setConfiguration(config);
//
//        ArrayList<Integer> userList = new ArrayList<>();
//        // The Omron SDK typically associates data with the userHashId set in the config.
//        // For HBF-222T, the user ID is often 1, 2, 3, or 4, selected on the device.
//        // If the SDK requires a specific user ID (e.g., an integer), you would need to map
//        // the currentUserHashId to an integer ID or find a way to get the correct user ID
//        // from the SDK or the device itself. For demonstration, we'll try to fetch for user 1.
//        userList.add(1); // Attempt to fetch data for user 1, as it's a common default
//
//        final boolean[] hasReplied = {false};
//        OmronPeripheralManager.sharedManager(this)
//                .startDataTransferFromPeripheral(currentPeripheral, userList, true,
//                        (peripheral, errorInfo) -> {
//                            if (hasReplied[0]) return;
//                            if (errorInfo.isSuccess()) {
//                                OmronPeripheralManager.sharedManager(this)
//                                        .endDataTransferFromPeripheral((p, e) -> {
//                                            if (e.isSuccess()) {
//                                                HashMap<String, Object> data = new HashMap<>();
//                                                data.put("vitalData", p.getVitalData());   // <-- This is not a List
//                                                data.put("deviceInfo", p.getDeviceInformation());
//                                                data.put("deviceSettings", new ArrayList<>(p.getDeviceSettings()));
//                                                result.success(data);
//                                            } else {
//                                                result.error("TRANSFER_ERROR", e.getMessageInfo(), null);
//                                            }
//                                        });
//                            } else {
//                                result.error("TRANSFER_ERROR", errorInfo.getMessageInfo(), null);
//                            }
//                            hasReplied[0] = true;
//                        });
//    }
//
//    private void setUserHashId(String userHashId, MethodChannel.Result result) {
//        this.currentUserHashId = userHashId; // Update the instance variable
//        OmronPeripheralManagerConfig config = OmronPeripheralManager.sharedManager(this).getConfiguration();
//        config.userHashId = userHashId;
//        OmronPeripheralManager.sharedManager(this).setConfiguration(config);
//        result.success("User hash ID set to " + userHashId);
//    }
//
//    private final BroadcastReceiver mPairingRequestReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            final String action = intent.getAction();
//            if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
//                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                int pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);
//                int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);
//
//                Log.d("OMRON_PAIRING", "Pairing request for: " + device.getName());
//
//                if (variant == BluetoothDevice.PAIRING_VARIANT_PIN) {
//                    device.setPin("0000".getBytes()); // many Omron devices use 0000
//                    abortBroadcast(); // auto-confirm
//                }
//                Log.d("OMRON_PAIRING", "Pairing request for device: " + device.getName() + " variant: " + pairingVariant);
//            }
//        }
//    };
//}
//
//
//
//
//
package com.updevelop.wellness_z_mvvm;

import android.os.Bundle;
import android.content.SharedPreferences;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.location.LocationManager;

import androidx.annotation.NonNull;

import io.flutter.embedding.android.FlutterFragmentActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

import com.omronhealthcare.OmronConnectivityLibrary.OmronLibrary.LibraryManager.OmronPeripheralManager;
import com.omronhealthcare.OmronConnectivityLibrary.OmronLibrary.Model.OmronPeripheral;
import com.omronhealthcare.OmronConnectivityLibrary.OmronLibrary.Model.OmronErrorInfo;
import com.omronhealthcare.OmronConnectivityLibrary.OmronLibrary.DeviceConfiguration.OmronPeripheralManagerConfig;
import com.omronhealthcare.OmronConnectivityLibrary.OmronLibrary.OmronUtility.OmronConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends FlutterFragmentActivity {
    private static final String CHANNEL = "omron_channel";
    private OmronPeripheral currentPeripheral;
    private List<OmronPeripheral> lastScannedPeripherals;
    private String currentUserHashId = "testuser@wellnessz.com"; // Default user hash ID

    private static final String PREFS = "OMRON_PREFS";
    private static final String KEY_PAIRED_ADDR = "paired_device_address";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e("OMRON", "Initializing...");

        OmronPeripheralManager.sharedManager(this)
                .setAPIKey("B7231051-501E-4CD6-8589-5394985B9E41", null);

        OmronPeripheralManagerConfig config = new OmronPeripheralManagerConfig();
        config.userHashId = currentUserHashId;
        config.timeoutInterval = 90;
        OmronPeripheralManager.sharedManager(this).setConfiguration(config);

        try {
            OmronPeripheralManager.sharedManager(this).startManager();
            Log.e("OMRON", "Manager started");
        } catch (Exception e) {
            Log.e("OMRON", "startManager() failed: " + e.getMessage());
        }

        // ✅ Register Bluetooth pairing receiver with high priority
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mPairingRequestReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mPairingRequestReceiver);
    }

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
                .setMethodCallHandler((call, result) -> {
                    switch (call.method) {
                        case "startScan":
                            startScan(result);
                            break;
                        case "connectDevice":
                            HashMap<String, Object> args = (HashMap<String, Object>) call.arguments;
                            String identifier = (String) args.get("identifier");
                            HashMap<String, Object> userSettings = (HashMap<String, Object>) args.get("userSettings");
                            connectToDevice(result, identifier, userSettings);
                            break;
                        case "transferData":
                            transferData(result);
                            break;
                        default:
                            result.notImplemented();
                    }
                });
    }

    // 🔹 Scan + Auto-connect if saved MAC exists
    private void startScan(MethodChannel.Result result) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            result.error("BLUETOOTH_ERROR", "Bluetooth not available/enabled.", null);
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            result.error("LOCATION_OFF", "Location services are not enabled.", null);
            return;
        }

        Log.e("OMRON", "Scanning...");
        String savedMac = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PAIRED_ADDR, null);

        OmronPeripheralManagerConfig config = OmronPeripheralManager.sharedManager(this).getConfiguration();
        config.userHashId = currentUserHashId;
        OmronPeripheralManager.sharedManager(this).setConfiguration(config);
        final boolean[] hasReplied = {false};

        OmronPeripheralManager.sharedManager(this)
                .startScanPeripherals((peripherals, errorInfo) -> {
                    if (hasReplied[0]) return;

                    // 🔹 Auto-connect if saved MAC exists

                    if (savedMac != null && peripherals != null) {
                        Log.e("OMRON", "Auto-connecting to saved device..." + savedMac);

                        for (OmronPeripheral p : peripherals) {

//                            BluetoothDevice btDevice = findBtDeviceByName(p.getLocalName());
//                            if (btDevice != null && savedMac.equalsIgnoreCase(btDevice.getAddress())) {
                                Log.e("OMRON", "Auto-connecting to saved device: " + p.getLocalName());

                                OmronPeripheralManager.sharedManager(this)
                                        .connectPeripheral(p, true, (periph, err) -> {
                                            if (err.isSuccess()) {
                                                currentPeripheral = periph;
                                                result.success("Auto-connected to " + periph.getLocalName());
                                            } else {
                                                result.error("AUTO_CONNECT_FAILED", err.getMessageInfo(), null);
                                            }
                                        });
                                hasReplied[0] = true;
                                return;
//                            }
                        }
                    }

                    // 🔹 Normal scan fallback
                    if (errorInfo != null && !errorInfo.isSuccess()) {
                        result.error("SCAN_ERROR", errorInfo.getMessageInfo(), null);
                        hasReplied[0] = true;
                        return;
                    }
                    if (peripherals == null || peripherals.isEmpty()) {
                        result.error("NO_DEVICES", "No devices found.", null);
                        hasReplied[0] = true;
                        return;
                    }

                    ArrayList<Map<String, String>> deviceList = new ArrayList<>();
                    for (OmronPeripheral p : peripherals) {
                        HashMap<String, String> deviceInfo = new HashMap<>();
                        deviceInfo.put("name", p.getLocalName());
                        deviceInfo.put("identifier", p.getLocalName());
                        deviceList.add(deviceInfo);
                    }
                    lastScannedPeripherals = peripherals;
                    result.success(deviceList);
                    hasReplied[0] = true;
                });
    }

    // 🔹 Connect to chosen device
    private void connectToDevice(MethodChannel.Result result, String identifier, HashMap<String, Object> userSettings) {
        if (lastScannedPeripherals == null || lastScannedPeripherals.isEmpty()) {
            result.error("NO_DEVICE", "No scanned devices. Scan first.", null);
            return;
        }

        OmronPeripheral targetPeripheral = null;
        for (OmronPeripheral p : lastScannedPeripherals) {
            if (p.getLocalName() != null && p.getLocalName().equalsIgnoreCase(identifier)) {
                targetPeripheral = p;
                break;
            }
        }
        if (targetPeripheral == null) {
            result.error("NO_DEVICE", "Device not found in scan.", null);
            return;
        }

        Log.e("OMRON", "Connecting to " + identifier);

        OmronPeripheralManagerConfig config = OmronPeripheralManager.sharedManager(this).getConfiguration();
        config.userHashId = currentUserHashId;

        if (userSettings != null) {
            HashMap<String, Object> settings = new HashMap<>();
            if (userSettings.containsKey("height")) {
                settings.put(OmronConstants.OMRONDevicePersonalSettings.UserHeightKey, userSettings.get("height"));
            }
            if (userSettings.containsKey("gender")) {
                String gender = (String) userSettings.get("gender");
                settings.put(OmronConstants.OMRONDevicePersonalSettings.UserGenderKey,
                        "Male".equalsIgnoreCase(gender)
                                ? OmronConstants.OMRONDevicePersonalSettingsUserGenderType.Male
                                : OmronConstants.OMRONDevicePersonalSettingsUserGenderType.Female);
            }
            if (userSettings.containsKey("dateOfBirth")) {
                settings.put(OmronConstants.OMRONDevicePersonalSettings.UserDateOfBirthKey, userSettings.get("dateOfBirth"));
            }
            HashMap<String, HashMap> omronUserSettings = new HashMap<>();
            omronUserSettings.put(OmronConstants.OMRONDevicePersonalSettingsKey, settings);
            ArrayList<HashMap> deviceSettings = new ArrayList<>();
            deviceSettings.add(omronUserSettings);
            config.deviceSettings = deviceSettings;
        }

        OmronPeripheralManager.sharedManager(this).setConfiguration(config);

        final boolean[] hasReplied = {false};
        OmronPeripheralManager.sharedManager(this)
                .connectPeripheral(targetPeripheral, true, (p, errorInfo) -> {
                    if (hasReplied[0]) return;
                    hasReplied[0] = true;

                    if (errorInfo.isSuccess()) {
                        currentPeripheral = p;

//                        BluetoothDevice btDevice = findBtDeviceByName(p.getLocalName());
//                        if (btDevice != null) {
//                            getSharedPreferences(PREFS, MODE_PRIVATE)
//                                    .edit()
//                                    .putString(KEY_PAIRED_ADDR, btDevice.getAddress()) // ✅ Save MAC
//                                    .apply();
//                        }

                        result.success("Connected to " + p.getLocalName());
                    } else {
                        result.error("CONNECT_FAILED", errorInfo.getMessageInfo(), null);
                    }
                });
    }

    // 🔹 Transfer data
    private void transferData(MethodChannel.Result result) {
        if (currentPeripheral == null) {
            result.error("NO_DEVICE", "No connected device.", null);
            return;
        }
        OmronPeripheralManagerConfig config = OmronPeripheralManager.sharedManager(this).getConfiguration();
        config.userHashId = currentUserHashId;
        config.enableAllDataRead = true;
        OmronPeripheralManager.sharedManager(this).setConfiguration(config);

        ArrayList<Integer> userList = new ArrayList<>();
        userList.add(1);

        OmronPeripheralManager.sharedManager(this)
                .startDataTransferFromPeripheral(currentPeripheral, userList, true,
                        (peripheral, errorInfo) -> {
                            if (errorInfo.isSuccess()) {
                                OmronPeripheralManager.sharedManager(this)
                                        .endDataTransferFromPeripheral((p, e) -> {
                                            if (e.isSuccess()) {
//                                                HashMap<String, Object> data = new HashMap<>();
//                                                data.put("vitalData", p.getVitalData());
//                                                result.success(data);
                                                HashMap<String, Object> data = new HashMap<>();
                                                data.put("vitalData", p.getVitalData());   // <-- This is not a List
                                                data.put("deviceInfo", p.getDeviceInformation());
                                                data.put("deviceSettings", new ArrayList<>(p.getDeviceSettings()));
                                                result.success(data);
                                            } else {
                                                result.error("TRANSFER_ERROR", e.getMessageInfo(), null);
                                            }
                                        });
                            } else {
                                result.error("TRANSFER_ERROR", errorInfo.getMessageInfo(), null);
                            }
                        });
    }

    // 🔹 Find bonded device by name
    private BluetoothDevice findBtDeviceByName(String deviceName) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return null;
        for (BluetoothDevice dev : adapter.getBondedDevices()) {
            if (dev.getName() != null && dev.getName().equalsIgnoreCase(deviceName)) {
                return dev;
            }
        }
        return null;
    }


    // 🔹 Auto-confirm pairing with PIN 0000
    private final BroadcastReceiver mPairingRequestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);

                if (variant == BluetoothDevice.PAIRING_VARIANT_PIN) {
                    device.setPin("0000".getBytes()); // auto-enter PIN
                    device.setPairingConfirmation(true); // auto-confirm
                    abortBroadcast(); // block popup
                    Log.d("OMRON_PAIRING", "Auto-PIN confirmed for " + device.getName());
                }
            }
        }
    };
}
