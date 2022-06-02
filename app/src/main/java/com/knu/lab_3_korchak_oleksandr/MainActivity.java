package com.knu.lab_3_korchak_oleksandr;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.ksoap2.SoapEnvelope;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


public class MainActivity extends AppCompatActivity {

    private static final UUID MY_UUID = UUID.randomUUID();

    private static byte[] key = new byte[256 / 8];
    private static byte[] iv = new byte[128 / 8];
    private static byte[] text;

    private static final String NAMESPACE = "https://api.authorize.net/soap/v1/";
    private static final String SEND = "Send";
    private static final String RECEIVE = "Receive";

    private static final int SELECT_DEVICE_REQUEST_CODE = 0;
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothDevice mmDevice;
    private UUID deviceUUID;
    ConnectedThread mConnectedThread;
    private Handler handler;

    String TAG = "MainActivity";
    String username = "user";
    EditText setUsername;
    EditText sendFriendUsername;
    EditText send_data;
    TextView view_data;
    Button connectButton;
    Button addButton;
    String message;

    Key keySpec;
    IvParameterSpec ivParameterSpec;
    Cipher cipher;

    public static String[] getPermissionList() {

        return new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        send_data = findViewById(R.id.sendText);
        view_data = findViewById(R.id.textView);
        setUsername = findViewById(R.id.firstUsernameText);
        sendFriendUsername = findViewById(R.id.secondUsernameText);
        connectButton = findViewById(R.id.connect);
        addButton = findViewById(R.id.add);

        System.arraycopy("labspecificationkey".getBytes(StandardCharsets.UTF_8), 0, key, 0, "labspecificationkey".getBytes(StandardCharsets.UTF_8).length);
        keySpec = new SecretKeySpec(key, "AES");

        System.arraycopy("labivparameter".getBytes(StandardCharsets.UTF_8), 0, iv, 0, "labivparameter".getBytes(StandardCharsets.UTF_8).length);
        ivParameterSpec = new IvParameterSpec(iv);

        try {
            cipher = Cipher.getInstance("AES/CTR/NoPadding");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }

       askAllPermissions();

    }

    public void askAllPermissions() {
        String[] permissions = getPermissionList();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, permission)!=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, permissions,
                        1);
            }

        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == SELECT_DEVICE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                BluetoothDevice deviceToPair = data.getParcelableExtra(
                        CompanionDeviceManager.EXTRA_DEVICE
                );

                if (deviceToPair != null) {
                    ConnectThread connect = new ConnectThread(deviceToPair, MY_UUID);
                    connect.start();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
        }

    @Override
    protected void onRestart() {
        super.onRestart();
        setContentView(R.layout.activity_main);

    }

    public void onResume() {
        super.onResume();
    }

    public void onStop() {
        super.onStop();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1: {

                if (grantResults.length > 0) {

                    List<Integer> indexesOfPermissionsNeededToShow = new ArrayList<>();

                    for (int i = 0; i < permissions.length; ++i) {

                        if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                            // user rejected the permission
                            boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i]);
                            if (!showRationale) {
                                // user also CHECKED "never ask again"
                                return;
                            } else {
                                // user did NOT check "never ask again"
                                indexesOfPermissionsNeededToShow.add(i);
                            }
                        }
                    }
                    int size = indexesOfPermissionsNeededToShow.size();
                    if (size != 0) {
                        int i = 0;
                        boolean isPermissionGranted = true;

                        while (i < size && isPermissionGranted) {
                            isPermissionGranted = grantResults[indexesOfPermissionsNeededToShow.get(i)] == PackageManager.PERMISSION_GRANTED;
                            i++;
                        }

                        if (!isPermissionGranted) {
                            View.OnClickListener onClickListener = new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    askAllPermissions();
                                }
                            };
                        }
                    }else{
                        onRestart();
                    }
                }
            }
        }
    }

    public void SendMessage(View v) {
        message = send_data.getText().toString();
        SoapObject request = new SoapObject(NAMESPACE,SEND);
        request.addProperty("name", username);
        request.addProperty("text", message);
        try {
            mConnectedThread.write(encode(request.toString().getBytes(StandardCharsets.UTF_16)));
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    public void Start_Server(View view) {

        String friendsUsername = sendFriendUsername.getText().toString();
        CompanionDeviceManager deviceManager =
                (CompanionDeviceManager) getSystemService(
                        Context.COMPANION_DEVICE_SERVICE
                );


        // To skip filtering based on name and supported feature flags,
        // don't include calls to setNamePattern() and addServiceUuid(),
        // respectively. This example uses Bluetooth.
        BluetoothDeviceFilter deviceFilter;
        if (friendsUsername.equals("")){
            deviceFilter = new BluetoothDeviceFilter.Builder()
                            .build();

        }
        else {
            deviceFilter = new BluetoothDeviceFilter.Builder()
                    .setNamePattern(Pattern.compile(friendsUsername))
                    .build();

        }

        // The argument provided in setSingleDevice() determines whether a single
        // device name or a list of device names is presented to the user as
        // pairing options.
        AssociationRequest pairingRequest = new AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .setSingleDevice(false)
                .build();

        // When the app tries to pair with the Bluetooth device, show the
        // appropriate pairing request dialog to the user.
        deviceManager.associate(pairingRequest,
                new CompanionDeviceManager.Callback() {
                    @Override
                    public void onDeviceFound(IntentSender chooserLauncher) {
                        try {
                            startIntentSenderForResult(chooserLauncher,
                                    SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(CharSequence error) {
                        Toast toast = Toast.makeText(MainActivity.this, "Device not founded", Toast.LENGTH_SHORT);
                        toast.show();
                        // handle failure to find the companion device
                    }
                }, null);

    }

    private byte[] encode(byte[] text) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeyException {
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivParameterSpec);
        byte[] encryptedText = cipher.doFinal(text);
        encryptedText = Base64.getEncoder().encode(encryptedText);
        return encryptedText;
    }

    private byte[] decode(byte[] text) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeyException {
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivParameterSpec);
        text = Base64.getDecoder().decode(text);
        byte[] decryptedText = cipher.doFinal(text);
        return decryptedText;
    }

    public void AddDevice(View view) {
        username = send_data.getText().toString();
    }

    public SoapObject string2SoapObject(byte[] bytes)
    {
        SoapSerializationEnvelope envelope=new SoapSerializationEnvelope(SoapEnvelope.VER12);
        SoapObject soap=null;
        try {
            ByteArrayInputStream inputStream=new ByteArrayInputStream(bytes);
            XmlPullParser p= Xml.newPullParser();
            p.setInput(inputStream, "UTF16");
            envelope.parse(p);
            soap=(SoapObject)envelope.bodyIn;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return soap;
    }

    private void connected(BluetoothSocket mmSocket) {
        Log.d(TAG, "connected: Starting.");

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(mmSocket);
        mConnectedThread.start();
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            Log.d(TAG, "ConnectThread: started.");
            mmDevice = device;
            deviceUUID = uuid;
        }

        @SuppressLint("MissingPermission")
        public void run(){
            BluetoothSocket tmp = null;
            Log.i(TAG, "RUN mConnectThread ");

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                Log.d(TAG, "ConnectThread: Trying to create InsecureRfcommSocket using UUID: "
                        + MY_UUID);
                tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread: Could not create InsecureRfcommSocket " + e.getMessage());
            }

            mmSocket = tmp;

            // Make a connection to the BluetoothSocket

            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();

            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                    Log.d(TAG, "run: Closed Socket.");
                } catch (IOException e1) {
                    Log.e(TAG, "mConnectThread: run: Unable to close connection in socket " + e1.getMessage());
                }
                Log.d(TAG, "run: ConnectThread: Could not connect to UUID: " + MY_UUID);
            }

            //will talk about this in the 3rd video
            connected(mmSocket);
        }
        public void cancel() {
            try {
                Log.d(TAG, "cancel: Closing Client Socket.");
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel: close() of mmSocket in Connectthread failed. " + e.getMessage());
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "ConnectedThread: Starting.");

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;



            try {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run(){
            byte[] buffer = new byte[1024];  // buffer store for the stream


            // Keep listening to the InputStream until an exception occurs
            while (true) {
                // Read from the InputStream
                try {
                    byte[] incomingMessage = decode(buffer);

                    SoapObject response = string2SoapObject(incomingMessage);
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            view_data.setText("Name:" + response.getProperty("name") + "\n" +
                                    response.getProperty("text"));
                        }
                    });


                } catch (IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
                    Log.e(TAG, "write: Error reading Input Stream. " + e.getMessage() );
                    break;
                }
            }
        }


        public void write(byte[] bytes) {
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG, "write: Writing to outputstream: " + text);
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "write: Error writing to output stream. " + e.getMessage() );
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
}