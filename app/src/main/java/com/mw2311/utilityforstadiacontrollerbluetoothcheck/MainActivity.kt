package com.mw2311.utilityforstadiacontrollerbluetoothcheck

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mw2311.utilityforstadiacontrollerbluetoothcheck.ui.theme.UtilityForStadiaControllerBluetoothCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UtilityForStadiaControllerBluetoothCheckTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Main app content
                    AppContent(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun AppContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    // Check if user has seen welcome screen. Default is false (not seen).
    // Note: We use a State that is initialized from the SharedPrefs.
    var hasSeenWelcome by remember { 
        mutableStateOf(sharedPreferences.getBoolean("has_seen_welcome", false)) 
    }

    if (!hasSeenWelcome) {
        WelcomeScreen(
            onContinue = { 
                sharedPreferences.edit().putBoolean("has_seen_welcome", true).apply()
                hasSeenWelcome = true 
            }, 
            modifier = modifier
        )
    } else {
        StadiaCheckerApp(modifier = modifier)
    }
}

@Composable
fun WelcomeScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome!",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "This utility helps you determine if your Stadia Controller is in WiFi mode or Bluetooth mode.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "• Connect via USB to check if the device is recognized.\n" +
                   "• Use the Bluetooth scan while holding 'Y + Stadia' to verify Bluetooth mode.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }
    }
}


@Composable
fun StadiaCheckerApp(modifier: Modifier = Modifier) {
    var usbStatus by remember { mutableStateOf("USB: Not checked") }
    var bluetoothStatus by remember { mutableStateOf("Bluetooth: Not scanning") }
    // Store unique devices to avoid duplicates in the UI
    var foundDevices by remember { mutableStateOf(setOf<String>()) }
    
    val context = LocalContext.current
    
    // Permission launcher
    val permissionsToRequest = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(Manifest.permission.BLUETOOTH)
        permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            bluetoothStatus = "Permissions granted. Ready to scan."
        } else {
            bluetoothStatus = "Permissions denied."
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Stadia Controller Checker", style = MaterialTheme.typography.headlineMedium)
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "USB Check", style = MaterialTheme.typography.titleMedium)
                Text(text = usbStatus)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { 
                    usbStatus = checkUsbForStadia(context)
                }) {
                    Text("Check USB Device")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Bluetooth Check", style = MaterialTheme.typography.titleMedium)
                Text(text = bluetoothStatus)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    if (hasPermissions(context, permissionsToRequest)) {
                        bluetoothStatus = "Scanning..."
                        foundDevices = emptySet()
                        scanForStadiaBluetooth(context) { status, deviceName ->
                            bluetoothStatus = status
                            if (deviceName != null) {
                                foundDevices = foundDevices + deviceName
                            }
                        }
                    } else {
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }
                }) {
                    Text("Scan Bluetooth (Hold Y + Stadia)")
                }
            }
        }
        
        if (foundDevices.isNotEmpty()) {
            Text("Found Devices:", style = MaterialTheme.typography.titleSmall)
            LazyColumn {
                items(foundDevices.toList()) { device ->
                    Text(text = device, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

fun checkUsbForStadia(context: Context): String {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val deviceList = usbManager.deviceList
    
    // VID 0x18D1 = Google, PID 0x9400 = Stadia Controller
    val stadia = deviceList.values.find { it.vendorId == 6353 && it.productId == 37888 }
    
    return if (stadia != null) {
        "Valid Stadia Controller connected.\n" +
        "Product: ${stadia.productName}\n" +
        "ID: ${stadia.deviceId}\n\n" +
        "Note: Both WiFi and BT modes show as USB HID. If you can pair via Bluetooth (hold Y + Stadia for 2s until orange light flashes), it is in BT mode."
    } else {
        "No Stadia Controller found via USB."
    }
}

fun hasPermissions(context: Context, permissions: List<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

fun scanForStadiaBluetooth(context: Context, onUpdate: (String, String?) -> Unit) {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter
    
    if (adapter == null || !adapter.isEnabled) {
        onUpdate("Bluetooth not enabled", null)
        return
    }

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
         onUpdate("Missing permissions", null)
        return
    }

    val scanner = adapter.bluetoothLeScanner
    if (scanner == null) {
        onUpdate("BLE Scanner not available", null)
        return
    }

    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val name = device.name
                    // Stadia Controller usually advertises with "Stadia" in the name
                    if (name != null && name.contains("Stadia", ignoreCase = true)) {
                        onUpdate("Stadia Controller Found!", "$name (${device.address})")
                    }
                }
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            onUpdate("Scan failed: $errorCode", null)
        }
    }

    scanner.startScan(callback)
    onUpdate("Scanning for 10s...", null)

    Handler(Looper.getMainLooper()).postDelayed({
         // Check permission again before stopping to avoid crash
         if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
             try {
                 scanner.stopScan(callback)
                 onUpdate("Scan finished.", null)
             } catch (e: Exception) {
                 // Ignore
             }
         }
    }, 10000)
}