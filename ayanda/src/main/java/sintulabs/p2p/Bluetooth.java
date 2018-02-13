package sintulabs.p2p;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED;
import static android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_STARTED;
import static android.bluetooth.BluetoothAdapter.ACTION_SCAN_MODE_CHANGED;
import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.STATE_ON;


/**
 * Created by sabzo on 1/14/18.
 */

public class Bluetooth extends P2P {
    private Context context;
    BluetoothAdapter mBluetoothAdapter;
    private BroadcastReceiver receiver;
    private IntentFilter intentFilter;
    public static Integer REQUEST_ENABLE_BT = 1;
    public static Integer BT_PERMISSION_REQUEST_LOCATION = 4444;
    private Boolean discoveryInitiated = false;
    private Set<String> deviceNamesDiscovered;
    private HashMap<String, BluetoothDevice> deviceList;
    private Set<BluetoothDevice> pairedDevices;

    public static String UUID = "00001101-0000-1000-8000-00805F9B34AC"; // arbitrary
    public static String NAME = "AyandaSecure";
    public static String NAME_INSECURE = "AyandaInsecure";

    /* Bluetooth Events Interface */
    private IBluetooth iBluetooth;


    public Bluetooth(Context context, IBluetooth iBluetooth) {
        this.context = context;
        this.iBluetooth = iBluetooth;
        mBluetoothAdapter= BluetoothAdapter.getDefaultAdapter();
        deviceNamesDiscovered = new HashSet<>();
        deviceList = new HashMap<>();
        createIntentFilter();
        createReceiver();
        // ensure to register and unregister receivers
    }

    @Override
    public Boolean isSupported() {
       return  (mBluetoothAdapter == null)? false : true;
    }

    @Override
    public Boolean isEnabled() {
        return mBluetoothAdapter.isEnabled();
    }

    private void enable() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        ((Activity)context).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    /**
     *  Announce Bluetooth service to Nearby Devices
     */
    @Override
    public void announce() {
        if (isSupported()) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            context.startActivity(discoverableIntent);
        }
    }

    /**
     * Create Intent Filters for Bluetooth events
     */
    private void createIntentFilter() {
        intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(ACTION_STATE_CHANGED);
        intentFilter.addAction(ACTION_SCAN_MODE_CHANGED);
    }

    /**
     * Broadcast receiver to handle Bluetooth events
     */
    private void createReceiver() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                switch (action) {
                    case ACTION_DISCOVERY_STARTED:
                        actionDiscoveryStarted(intent);
                        break;
                    case ACTION_DISCOVERY_FINISHED:
                        actionDiscoveryFinished(intent);
                        break;
                    case ACTION_SCAN_MODE_CHANGED:
                        scanModeChange(intent);
                        break;
                    case ACTION_STATE_CHANGED:
                        stateChanged(intent);
                        break;
                    case BluetoothDevice.ACTION_FOUND:
                        deviceFound(intent);
                        break;
                }
            }

            private void scanModeChange(Intent intent) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    // discoverable and can be connected to
                    case SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Toast.makeText(context, "Device is connectable and discoverable", Toast.LENGTH_SHORT).show();
                        Log.d(TAG_DEBUG, "Device is connectable and discoverable");
                        break;
                    // not discoverable but connectable from previously paired devices
                    case SCAN_MODE_CONNECTABLE:
                        break;
                }
            }

            // Discovery is quick and limited (about 12 seconds)
            private void actionDiscoveryStarted(Intent intent) {
                Log.d(TAG_DEBUG, "Discovery started");
                iBluetooth.actionDiscoveryStarted(intent);
            }
            // Calls after BT finishes scanning (12 seconds)
            private void actionDiscoveryFinished(Intent intent) {
                discoveryInitiated = false;
                Log.d(TAG_DEBUG, "Discovery finished");
                iBluetooth.actionDiscoveryFinished(intent);
            }

            /* Bluetooth enabled/disabled */
            private void stateChanged(Intent intent) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                   switch (state) {
                    // Bluetooth state changed: Is it On?
                    case STATE_ON:
                        if (discoveryInitiated && !mBluetoothAdapter.startDiscovery()) {
                            Log.d(TAG_DEBUG, "unable to start bluetooth discovery");
                        };
                        break;
                }
                iBluetooth.stateChanged(intent);
            }

        };
    }

    /**
     * Get nearby devices already paired with using Bluetooth
     */
    private void getPairedDevices() {
        pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                if (device != null && device.getName() != null &&
                        (device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA ||
                                device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA ||
                                device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_SMART)) {
                    Intent intent = new Intent();
                    intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
                    deviceFound(intent);
                }
            }
        }
    }

    /**
     * Event handler for when device is found. It performs some book-keeping and propagates event
     * to the IBluetooth interface.
     * @param intent
     */
    private void deviceFound(Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        Device d = new Device(device);
        String deviceName = d.getDeviceName() == null ? d.getDeviceAddress() : d.getDeviceName();
        deviceList.put(deviceName, device);
        deviceNamesDiscovered.add(deviceName);
        iBluetooth.actionFound(intent);
    }

    /* Register/unregister Receiver */

    public void registerReceivers() {
        context.registerReceiver(receiver, intentFilter);
    }

    public void unregisterReceivers() {
        context.unregisterReceiver(receiver);
    }

    @Override
    public void discover() {
        if ( isSupported()) {
            discoveryInitiated = true;
            if (!isEnabled()) {
                enable();
            } else {
                getPairedDevices();
                if (!mBluetoothAdapter.startDiscovery()) {
                    Log.d(TAG_DEBUG, "unable to start bluetooth discovery");
                }
            }
        }
    }

    public HashMap<String, BluetoothDevice> getDeviceList() {
        return deviceList;
    }

    public Set<String> getDeviceNamesDiscovered() {
       return deviceNamesDiscovered;
    }
    @Override
    public void disconnect() {
    }

    @Override
    public void send() {

    }

    @Override
    public void cancel() {

    }


    /**
     * Write's data to a connected device using a Bluetooth RFCOMM channel
     * @param bytes
     * @throws IOException if for any reason current device can't write to a client
     */
    public void write(byte [] bytes) throws IOException {
        announce();
        new ServerThread(bytes, true);
    }

    /**
     * Connects to a Bluetooth device and begins reading form bluetooth device
     * @param device A bluetooth Device
     * @throws IOException
     */
    public void read(BluetoothDevice device) throws IOException {
        new ClientThread(device).start();
    }

    /**
     * Represents a Bluetooth Device
     */
    public static class Device {
        private BluetoothDevice device;
        private String deviceName;
        private String deviceAddress; // MAC address

        public Device(BluetoothDevice device) {
            this.device = device;
            deviceName = device.getName();
            deviceAddress = device.getAddress(); // MAC address
        }

        public String getDeviceName() {
            return deviceName;
        }

        public String getDeviceAddress() {
            return deviceAddress;
        }

        public BluetoothDevice getDevice() {
            return device;
        }
    }

    /**
     *  Creates Sever to receive connections from other bluetooth devices
     */
    private class ServerThread extends Thread {
        // Server
        private BluetoothServerSocket btServerSocket;
        private BluetoothSocket btSocket;
        private DataTansfer dataTansfer = null;
        private String socketType;
        private byte[] bytes;

        public ServerThread(byte [] bytes, boolean secure) {
            this.bytes = bytes;
            socketType = secure ? "Secure" : "Insecure";
            BluetoothServerSocket tmp = null;
            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME,
                            java.util.UUID.fromString(UUID));
                } else {
                    tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            NAME_INSECURE,
                            java.util.UUID.fromString(UUID));
                }
            } catch (IOException e) {
                Log.e(TAG_DEBUG, "Socket Type: " + socketType + "listen() failed", e);
            }

            btServerSocket = tmp;
        }

        @Override
        public void run() {
            try {
                btSocket = btServerSocket.accept();
                //bluetooth server accepts 1 connection at a time so close after new connection
                btServerSocket.close();
                // begin writing data
                if (btSocket != null) {
                    new DataTansfer(btSocket).write(bytes);
                } else {
                    Log.d(TAG_DEBUG, "Unable to write data: btScocket null");
                }
                // client has connected
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
        // close open thread
        public void close() {
            try {
                btServerSocket.close();
                btSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *  Connect as a Client to a nearby Bluetooth Device acting as a server
     */
    private class ClientThread extends Thread {
        private BluetoothSocket socket = null;
        private DataTansfer dataTansfer = null;


        public ClientThread(BluetoothDevice device) {
            try {
                socket = device.createRfcommSocketToServiceRecord(
                        java.util.UUID.fromString(UUID));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            if (socket != null) {
                try {
                    socket.connect();
                    final byte[] bytes = new byte[1024];
                    final int numRead = new DataTansfer(socket).read(bytes);
                    socket.close();
                    // Bytes
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            iBluetooth.dataRead(bytes, numRead);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Represents an ative connection between this device and another device.
     */
    private class DataTansfer {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        /**
         * Creates InputStream & Output Stream from Bluetooth Scoket
         * @param socket Bluetooth socket representing active connection
         */
        public DataTansfer(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG_DEBUG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG_DEBUG, "Error occurred when creating output stream", e);
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        /**
         * write bytes to connected device
         * @param bytes
         * @throws IOException
         */
        public void write(byte[] bytes) throws IOException {
            outputStream.write(bytes);
        }

        /**
         * Read data from connected device
         * @param buffer A buffer to store data read
         * @return number of bytes read as an int
         * @throws IOException
         */
        public int read(byte[] buffer) throws IOException {
            return inputStream.read(buffer);
        }
    }

}
