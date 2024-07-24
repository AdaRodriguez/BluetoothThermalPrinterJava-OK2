package com.example.bluetooththermalprinterjava;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.bluetooththermalprinterjava.databinding.ActivityMainBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private boolean btPermission = false;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private BluetoothDevice bluetoothDevice;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Thread workerThread;
    private byte[] readBuffer;
    private int readBufferPosition;
    private volatile boolean stopWorker;
    private ConnectionClass connectionClass = new ConnectionClass();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setTitle("Bluetooth Printer example Java");

        // Set the default printer name
        binding.deviceName.setText("PT210_777D");
        connectionClass.setPrinterName("PT210_777D");

        binding.printButton.setOnClickListener(view -> {
            if (btPermission) {
                initPrinter();
            } else {
                checkPermission();
            }
        });
    }

    private void checkPermission() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blueToothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
            } else {
                blueToothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_ADMIN);
            }
        }
    }

    private final ActivityResultLauncher<String> blueToothPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::onPermissionResult);

    private final ActivityResultLauncher<Intent> btActivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    initPrinter();
                }
            });

    @SuppressLint("MissingPermission")
    private void initPrinter() {
        String prname = connectionClass.getPrinterName(); // Get printer name using getter
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        try {
            if (bluetoothAdapter != null) {
                if (!bluetoothAdapter.isEnabled()) {
                    Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    btActivityResultLauncher.launch(enableBluetooth);
                } else {
                    connectToDevice(prname);
                }
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Bluetooth Printer Not Connected", Toast.LENGTH_LONG).show();
            socket = null;
        }
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(String prname) {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (!pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals(prname)) {
                    bluetoothDevice = device;
                    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                    try {
                        BluetoothSocket tempSocket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", int.class).invoke(device, 1);
                        if (tempSocket != null) {
                            socket = tempSocket;
                            bluetoothAdapter.cancelDiscovery();
                            try {
                                socket.connect();
                                outputStream = socket.getOutputStream();
                                inputStream = socket.getInputStream();
                                beginListenForData();
                                printInvoice();
                            } catch (IOException e) {
                                Log.e("BluetoothError", "Error connecting to Bluetooth device", e);
                                try {
                                    socket.close();
                                } catch (IOException closeException) {
                                    Log.e("BluetoothError", "Error closing socket", closeException);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("BluetoothError", "Error creating Bluetooth socket", e);
                    }
                    break;
                }
            }
        } else {
            Toast.makeText(this, "No Devices found", Toast.LENGTH_LONG).show();
        }
    }

    private void beginListenForData() {
        try {
            final Handler handler = new Handler();
            final byte delimiter = 10;
            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];
            workerThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = inputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            inputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    String data = new String(encodedBytes, Charset.forName("US-ASCII"));
                                    readBufferPosition = 0;
                                    handler.post(() -> Log.d("Data", data));
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            });
            workerThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printInvoice() {
        fetchDataAndPrint();
    }

    private void fetchDataAndPrint() {
        String url = "http://enrol.lesterintheclouds.com/fetch-data/fetch-data3.php";

        RequestQueue queue = Volley.newRequestQueue(this);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONArray jsonArray = new JSONArray(response);
                            StringBuilder textData = new StringBuilder();
                            String header = " Roman Catholic Bishop of Daet\n"
                                    + " HOLY TRINITY COLLEGE SEMINARY \n"
                                    + "        FOUNDATION, Inc.\n"
                                    + " Holy Trinity College Seminary \n"
                                    + "    P.3 Bautista 4604, Labo \n"
                                    + " Camarines Norte, Philippines\n\n\n";
                            textData.append(header);

                            if (jsonArray.length() > 0) {
                                JSONObject firstObj = jsonArray.getJSONObject(0);
                                textData.append("STUDENT ID: ").append(firstObj.getString("STUDENT ID")).append("\n");
                                textData.append("NAME: ").append(firstObj.getString("NAME")).append("\n");
                                textData.append("DATE: ").append(firstObj.getString("DATE")).append("\n\n");
                            }

                            // Add table header
                            textData.append(String.format("%-20s %10s\n", "TITLE", "AMT PAID"));
                            textData.append(String.format("%-20s %10s\n", "-----", "--------"));

                            // Loop through all the rows and format them
                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject obj = jsonArray.getJSONObject(i);
                                String title = obj.getString("TITLE");
                                String amtPaid = obj.getString("AMT PAID");

                                textData.append(String.format("%-20s %10s\n", title, amtPaid));
                            }

                            // Add footer if needed
                            textData.append("\nOR Number:_______\n\n\n");
                            textData.append("\nThank you for your payment!\n");

                            printData(textData.toString());
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(MainActivity.this, "Error fetching data", Toast.LENGTH_SHORT).show();
                    }
                });

        queue.add(stringRequest);
    }

    private void printData(String data) {
        try {
            if (outputStream != null) {
                outputStream.write(data.getBytes(Charset.forName("US-ASCII")));
                outputStream.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onPermissionResult(Boolean result) {
        if (result != null && result) {
            btPermission = true;
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                btActivityResultLauncher.launch(enableBtIntent);
            } else {
                initPrinter();
            }
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
        }
    }
}
