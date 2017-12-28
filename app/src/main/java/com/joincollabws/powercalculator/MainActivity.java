package com.joincollabws.powercalculator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    // Debugging for LOGCAT
    private static final String TAG = "MainActivity";

    // Variables for settings menu and their initial conditions
    public static boolean setHp = false; // Settings for power unit
    public static boolean setDebug = false; // Settings for debug message

    private boolean dataExpected = false; // Whether new data from bt device is asked or not

    // Bluetooth stuffs
    Handler bluetoothIn;

    final int handlerState = 0; // Used to identify handler message
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder recDataString = new StringBuilder();

    private ConnectedThread mConnectedThread;

    // SPP UUID service - this should work for most devices
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static String address; // MAC address of connected bluetooth device

    // EXTRA string to send on to MainActivity
    public static String EXTRA_DEVICE_ADDRESS = "WS_DEVICE_ADDRESS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Apply settings variables to layout
        applySettings();

        // Connect Bluetooth device at the fresh start of application
        bluetoothMenu();

        bluetoothIn = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(android.os.Message msg) {
                if (msg.what == handlerState) {                      // If message is what we want
                    String readMessage = (String) msg.obj;           // msg.arg1 = bytes from connect thread
                    recDataString.append(readMessage);               // Keep appending to string until ~

                    // Input format: #avg/peak~

                    // Log & debug buffer of received data
                    Log.d(TAG, "Bluetooth Receive: " + recDataString);
                    setDebugMessages(recDataString.toString(),1);

                    // If no new data is asked by this app
                    if (!dataExpected) recDataString.delete(0, recDataString.length()); // Clear all string data

                    int endOfLineIndex = recDataString.indexOf("~"); // Determine the end-of-line

                    // Make sure there is data before ~
                    if (endOfLineIndex > 0) {

                        // Case of multiple start symbols
                        int startOfLineIndex = recDataString.lastIndexOf("#"); // Determine the start-of-line

                        if (startOfLineIndex > -1) {
                            recDataString.delete(0, startOfLineIndex);  // Clear data until the latest start index
                            endOfLineIndex -= startOfLineIndex;         // Important, don't forget to shift the indexes

                            if (startOfLineIndex < endOfLineIndex) {

                                // Extract whole string and display at debug message as valid data
                                setDebugMessages(recDataString.substring(0, endOfLineIndex), 2);

                                // Get the data without start and end, then split according to '/'
                                String[] data = recDataString.substring(1, endOfLineIndex).split("/");

                                // Set new value for power texts
                                if (data.length == 2)
                                    setPower(Integer.valueOf(data[0]), Integer.valueOf(data[1]));

                                // Notify that new data is received
                                Toast.makeText(MainActivity.this, getString(R.string.toast_receive),
                                        Toast.LENGTH_SHORT).show();

                                // No new data expected, since no new data recording started
                                dataExpected = false;
                            }
                        }
                    }
                }
                return true;
            }
        });

        btAdapter = BluetoothAdapter.getDefaultAdapter();   // Get Bluetooth adapter
        checkBTState();

        // Restart button
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setPower(-1,-1);      // Set power texts to unavailable state

                mConnectedThread.write("!");  // Send "!" via Bluetooth, asking for new data
                dataExpected = true;                // Now this app is expecting data from bt device

                Toast.makeText(MainActivity.this, getString(R.string.toast_restart),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_bluetooth) {
            bluetoothMenu();
            return true;
        }

        if (id == R.id.action_settings) {
            final boolean[] selectedItems = {setHp, setDebug}; // Map setting items to their corresponding variables

            // Trust me, this is the best template out there. Browsing again won't give better stuffs.
            // Just need to change mapping above and the corresponding effects below inside "onClick" & applySettings().
            // Also, see strings.xml to see the settings content (R.array.settings_content)
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle(getString(R.string.action_settings))
                .setMultiChoiceItems(R.array.settings_content, selectedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int indexSelected, boolean isChecked) {
                        if (isChecked) {
                            // If the user checked the item, set it to true in the items array
                            selectedItems[indexSelected] = true;
                        } else {
                            // Else, if the item is unchecked, set it to false in the items array
                            selectedItems[indexSelected] = false;
                        }
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // Apply settings to variables
                        setHp = selectedItems[0];
                        setDebug = selectedItems[1];

                        // Apply settings variables to layout
                        applySettings();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // Don't apply settings
                    }
                })
                .show();
                return true;
        }

        if (id == R.id.action_about) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle(getString(R.string.action_about))
                .setMessage(getString(R.string.about_content))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with delete
                    }
                })
                .show();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // check if the request code is same as what is passed  here it is 2
        if(requestCode == 0) {
            if (resultCode == Activity.RESULT_OK) {
                address = data.getStringExtra(EXTRA_DEVICE_ADDRESS);

                // Set debugging messages
                setDebugMessages(address, 3);
            }

            // On RESULT_CANCELED, don't do anything; keep prev device

        }
    }

    @Override
    public void onResume() {
        super.onResume();

        BluetoothDevice device;

        //create device and set the MAC address
        if (address != null) {
            device = btAdapter.getRemoteDevice(address);

            try {
                btSocket = createBluetoothSocket(device);
            } catch (IOException e) {
                Toast.makeText(getBaseContext(), getString(R.string.bt_socket_fail), Toast.LENGTH_LONG).show();
            }

            // Establish the Bluetooth socket connection.
            try {
                btSocket.connect();
            } catch (IOException e) {
                try {
                    btSocket.close();
                } catch (IOException e2) {
                    //insert code to deal with this
                }
            }
            mConnectedThread = new ConnectedThread(btSocket);
            mConnectedThread.start();

            // Send a character when resuming & beginning transmission to check if device is connected.
            // If not, an exception will be thrown in the write method and finish() will be called
            mConnectedThread.write("x");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            //Don't leave Bluetooth sockets open when leaving activity
            if (address != null) btSocket.close();
        } catch (IOException e2) {
            //insert code to deal with this
        }
    }

    //Checks that the Android device Bluetooth is available and prompts to be turned on if off
    private void checkBTState() {
        if (btAdapter == null) {
            Toast.makeText(getBaseContext(), getString(R.string.bt_none), Toast.LENGTH_LONG).show();
        } else {
            if (btAdapter.isEnabled()) {
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    private void bluetoothMenu(){
        Intent intent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(intent, 0); // Activity is started with requestCode 0
    }

    private void applySettings() {
        // Apply settings variables to layout
        View debugLayout = findViewById(R.id.layoutDebug);
        if (setDebug) {
            debugLayout.setVisibility(View.VISIBLE);
        } else {
            debugLayout.setVisibility(View.INVISIBLE);
        }

        TextView text = findViewById(R.id.textUnit);
        if (setHp) {
            text.setText(getString(R.string.unit) + getString(R.string.hp));
        } else {
            text.setText(getString(R.string.unit) + getString(R.string.watt));
        }
    }

    public void setPower(int valAvg, int valPeak) {
        // Power texts controller
        TextView text;

        text = findViewById(R.id.textAvg);
        if (valAvg == -1) text.setText("-");
        else if (setHp) text.setText(String.valueOf(valAvg * 0.00134102));
        else text.setText(String.valueOf(valAvg));

        text = findViewById(R.id.textPeak);
        if (valPeak == -1) text.setText("-");
        else if (setHp) text.setText(String.valueOf(valPeak * 0.00134102));
        else text.setText(String.valueOf(valPeak));
    }

    public void setDebugMessages(String message, int index) {
        // Immediately stop if no debugging, better performance
        if (!setDebug) return;

        TextView text;

        // Setting debug messages
        switch (index) {
            case 0:
                // Clear
                text = findViewById(R.id.textDebug1);
                text.setText(getString(R.string.debug_content_1));
                text = findViewById(R.id.textDebug2);
                text.setText(getString(R.string.debug_content_2));
                text = findViewById(R.id.textDebug3);
                text.setText(getString(R.string.debug_content_3));
                break;
            case 1:
                text = findViewById(R.id.textDebug1);
                text.setText(getString(R.string.debug_content_1) + message);
                break;
            case 2:
                text = findViewById(R.id.textDebug2);
                text.setText(getString(R.string.debug_content_2) + message);
                break;
            case 3:
                text = findViewById(R.id.textDebug3);
                text.setText(getString(R.string.debug_content_3) + message);
                break;
        }
    }

    // Create new class for connect thread
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        // Creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                // Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];
            int bytes;

            // Keep looping to listen for received messages
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);            // Read bytes from input buffer
                    String readMessage = new String(buffer, 0, bytes);

                    // Send the obtained bytes to the UI Activity via handler
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }
        //write method
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();    // Converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);   // Write bytes over BT connection via outstream
            } catch (IOException e) {
                //if you cannot write, close the application
                Toast.makeText(getBaseContext(), getString(R.string.bt_connect_fail), Toast.LENGTH_LONG).show();
                //finish();
            }
        }
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connecetion with BT device using UUID
    }
}
