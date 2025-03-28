package com.example.flutter_serial_port;

import java.lang.Thread;
import java.lang.Runnable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import android.serialport.SerialPort;
import android.serialport.SerialPortFinder;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.EventChannel;
//import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * FlutterSerialPortPlugin
 */
public class FlutterSerialPortPlugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {

    private static final String TAG = "FlutterSerialPortPlugin";
    private SerialPortFinder mSerialPortFinder = new SerialPortFinder();
    protected SerialPort mSerialPort;
    protected OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;
    private EventChannel.EventSink mEventSink;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private MethodChannel methodChannel = null;

    private EventChannel eventChannel = null;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        this.methodChannel = new MethodChannel(binding.getBinaryMessenger(), "serial_port");
        this.methodChannel.setMethodCallHandler(this);

        this.eventChannel = new EventChannel(binding.getBinaryMessenger(), "serial_port/event");
        this.eventChannel.setStreamHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (this.methodChannel != null){
            this.methodChannel.setMethodCallHandler(null);
            this.methodChannel = null;
        }

        if (this.eventChannel != null){
            this.eventChannel.setStreamHandler(null);
            this.eventChannel = null;
        }
    }


    private class ReadThread extends Thread {
        @Override
        public void run() {
            super.run();
            while (!isInterrupted()) {
                int size;
                try {
                    byte[] buffer = new byte[64];
                    if (mInputStream == null)
                        return;
                    size = mInputStream.read(buffer);
                    // Log.d(TAG, "read size: " + String.valueOf(size));
                    if (size > 0) {
                        onDataReceived(buffer, size);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    protected void onDataReceived(final byte[] buffer, final int size) {
        if (mEventSink != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Log.d(TAG, "eventsink: " + buffer.toString());
                    mEventSink.success(Arrays.copyOfRange(buffer, 0, size));
                }
            });
        }
    }

    public FlutterSerialPortPlugin() {

    }

//    public FlutterSerialPortPlugin(Registrar registrar) {
//        final EventChannel eventChannel = new EventChannel(registrar.messenger(), "serial_port/event");
//        eventChannel.setStreamHandler(this);
//    }

    /**
     * Plugin registration.
     */
//    public static void registerWith(Registrar registrar) {
//        final MethodChannel channel = new MethodChannel(registrar.messenger(), "serial_port");
//        channel.setMethodCallHandler(new FlutterSerialPortPlugin(registrar));
//    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "open":
                final String devicePath = call.argument("devicePath");
                final int baudrate = call.argument("baudrate");
                Log.d(TAG, "Open " + devicePath + ", baudrate: " + baudrate);
                Boolean openResult = openDevice(devicePath, baudrate);
                result.success(openResult);
                break;
            case "close":
                Boolean closeResult = closeDevice();
                result.success(closeResult);
                break;
            case "write":
                writeData((byte[]) call.argument("data"));
                result.success(true);
                break;
            case "getAllDevices":
                ArrayList<String> devices = getAllDevices();
                Log.d(TAG, devices.toString());
                result.success(devices);
                break;
            case "getAllDevicesPath":
                ArrayList<String> devicesPath = getAllDevicesPath();
                Log.d(TAG, devicesPath.toString());
                result.success(devicesPath);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        mEventSink = eventSink;
    }

    @Override
    public void onCancel(Object o) {
        mEventSink = null;
    }

    private ArrayList<String> getAllDevices() {
        return new ArrayList<String>(Arrays.asList(mSerialPortFinder.getAllDevices()));
    }

    private ArrayList<String> getAllDevicesPath() {
        return new ArrayList<>(Arrays.asList(mSerialPortFinder.getAllDevicesPath()));
    }

    private Boolean openDevice(String devicePath, int baudrate) {
        if (mSerialPort == null) {
            /* Check parameters */
            if ((devicePath.isEmpty()) || (baudrate == -1)) {
                return false;
            }

            /* Open the serial port */
            try {
                mSerialPort = new SerialPort(new File(devicePath), baudrate, 0);
                mOutputStream = mSerialPort.getOutputStream();
                mInputStream = mSerialPort.getInputStream();
                mReadThread = new ReadThread();
                mReadThread.start();
                return true;
            } catch (Exception e) {
                Log.e(TAG, e.toString());
                return false;
            }
        }
        return false;
    }

    private Boolean closeDevice() {
        if (mSerialPort != null) {
            mSerialPort.close();
            mSerialPort = null;
            return true;
        }
        return false;
    }

    private void writeData(byte[] data) {
        try {
            mOutputStream.write(data);
            // mOutputStream.write('\n');
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }
}
