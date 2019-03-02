package com.feite.ble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by liujiafei on 13/9/18. 
 */

public class BleManager {

    private final static String TAG = BleManager.class.getSimpleName();

    public static final String INTENT_DEVICE_DETECTED = "INTENT_DEVICE_DETECTED";
    public static final String INTENT_STOP_SCAN = "INTENT_STOP_SCAN";
    public static final String INTENT_START_SCAN = "INTENT_START_SCAN";
    public static final String DEVICE_STATUS_CONNECT = "CONNECTED";
    public static final String DEVICE_STATUS_DISCONNCET = "DISCONNECTED";

    private Context context;

    private BluetoothAdapter mBluetoothAdapter;
    public final static int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;

    /**
     * 这里我限定来 只搜索 apple的 midi 设备，可以根据你自己的需求加减*/
    public final static String APPLE_BLE_MIDI_SERVICE_UUID = "03b80e5a-ede8-4b33-a751-6ce34ec4c700";
    public final String APPLE_BLE_MIDI_CHARACTERISTIC_UUID = "7772e5db-3868-4112-a1a9-f2669d106bf3";

    private BleCallBack bleCallBack;

    private Map< String, BluetoothDevice > deviceMap = new HashMap<String,BluetoothDevice>();

    private String selectedDeviceMac;
    private String selectedDeviceName;

    private boolean isScanning = false;

    public BleManager(Context context){
        this.context = context;
    }


    public BleCallBack getBleCallBack() {
        return bleCallBack;
    }

    public void setBleCallBack(BleCallBack bleCallBack) {
        this.bleCallBack = bleCallBack;
    }

    public BluetoothGatt getGatt(){
        return mGatt;
    }

    public boolean isBtEnable(){
       return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }


    /**
     * Activiy 生命周期 钩子 方法
     * 需要在Activiy 里面调用*/
    public void onActivityCreate(){
        mHandler = new Handler();
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(context, "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    /**
     * Activiy 生命周期 钩子 方法
     * 需要在Activiy 里面调用*/
    public void onActivityPause(){
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    /**
     * Activiy 生命周期 钩子 方法
     * 需要在Activiy 里面调用*/
    public  void onActivityResume(){

        if (Build.VERSION.SDK_INT >= 21) {
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            //filters = new ArrayList<ScanFilter>();
            filters = BleMidiDeviceUtils.getBleMidiScanFilters(context);
        }
        //这里看项目需求，需要不需要自动扫描
        scanLeDevice(true);
    }

    /**
     * Activiy 生命周期 钩子 方法
     * 需要在Activiy 里面调用*/
    public void onActivityDestory(){
        if (mGatt == null) {
            return;
        }
        mGatt.close();
        mGatt = null;
    }

    public List<String> getDeviceNameList(){
        List<String> keyList = new ArrayList<String>(deviceMap.keySet());
        Log.e(TAG,"device count:"+keyList.size());
        return keyList;
    }

    public void scanLeDevice(boolean enable){
        if (enable) {

            //如果正在扫描 就返回,否则会出现 scan failed, error code:1
            if(isScanning){
                //BuglyUtil.e(TAG,"BluetoothAdapter is scanning");
                return;
            }

            if(mBluetoothAdapter.isDiscovering()){
                //BuglyUtil.e(TAG,"BluetoothAdapter is isDiscovering");
                return;
            }


            //10秒后自动关闭扫描
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //BuglyUtil.e(TAG,"10 seconds time up, stop scan");
                    stopScan();
                }
            }, SCAN_PERIOD);

            startScan();

        } else {
            stopScan();
        }
    }

    private void startScan(){
        //deviceMap.clear();

        Intent intent = new Intent( INTENT_START_SCAN ) ;
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast( intent ) ;

        isScanning = true;

        if (Build.VERSION.SDK_INT < 21) {
            mBluetoothAdapter.startLeScan(mLeScanCallback);
            //BuglyUtil.e(TAG,"scanLeDevice<21");
        } else {
            //BuglyUtil.e(TAG,"scanLeDevice");
            mLEScanner.startScan(filters, settings, mScanCallback);
        }
    }


    /**
     * 手动点击按钮停止搜索 要call的方法
     * */
    private void stopScan(){

        //这里你可以广播事件来 更新你的UI
        Intent intent = new Intent( INTENT_STOP_SCAN ) ;
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast( intent ) ;

        if(!isScanning){
            //已经stop了，如果你还call的话，就会出现 log D/BluetoothLeScanner: could not find callback wrapper
            return;
        }

        isScanning = false;

        /*安卓官方文档，无论有没有直接call startDiscovery()，我们都要call cancelDiscovery
         * Discovery is not managed by
         * the  Activity, but is run as a system service, so an application should
         * always call cancel discovery even if it did not directly request a
         * discovery, just to be sure.*/
        mBluetoothAdapter.cancelDiscovery();

        mHandler.removeCallbacksAndMessages(null);

        if (Build.VERSION.SDK_INT < 21) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            //BuglyUtil.e(TAG,"stop scanLeDevice<21");
        } else if(mLEScanner!=null){
            mLEScanner.stopScan(mScanCallback);
            //BuglyUtil.e(TAG,"stop scanLeDevice");
        }
    }



    public void disconnect(){
        if(mGatt == null)
            return;

        mGatt.close();
        mGatt = null;

        if(bleCallBack != null){
            bleCallBack.connectStatus("DISCONNECTED");
        }
    }

    public void connectDevice(String name){
        connectToDevice(deviceMap.get(name));
    }


    private ScanCallback mScanCallback = new ScanCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            /**扫描到设备时，这边会被多次的call，需要注意下, 是同一个线程。
             * 当连接到设备后，你再扫描的话，如果没有新增的设备，是不会走到这里的。除非你断电蓝牙设备，或者重新启动app
             * 也就是说已经连接的设备不会出现在回调里
             * */
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            Log.e(TAG, "thread:"+Thread.currentThread().getId());

            BluetoothDevice btDevice = result.getDevice();
            if( deviceMap.get(btDevice.getName())== null){
                deviceMap.put(btDevice.getName(),btDevice);
                Intent intent = new Intent( INTENT_DEVICE_DETECTED ) ;
                LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast( intent ) ;
                Log.e(TAG,"bleCallBack: callbackType:"+callbackType+"\nresult:\n"+result.toString());
            }

            if(bleCallBack!=null){

                bleCallBack.deviceDetected(btDevice.getName());

                //bleCallBack.deviceDetected(btDevice.getName()+"("+btDevice.getAddress()+")");
            }

            //如果要自动连接 就用下面的语句
            //connectToDevice(btDevice);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("\n").append("ScanResult - Results");
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
                stringBuffer.append("\n").append(sr.toString());
            }
            Log.e(TAG,stringBuffer.toString());
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
            //BuglyUtil.e(TAG,"Scan Failed - Error Code: " + errorCode);
        }
    };



    /**
     * API < 21 用此 callback*/
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG,"onLeScan :"+ device.toString());
                            connectToDevice(device);
                        }
                    });
                }
            };


    public void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(context, false, gattCallback);//触发 BluetoothProfile.STATE_CONNECTED
            scanLeDevice(false);// will stop after first device detection
        }
    }

    /**
     * 连接蓝牙的顺序是 扫描 - 发现设备 - 连接 - 发现服务 - 发现Characteristics
     在发现服务中 选择你需要的服务 比如这里的 midi 服务的 UUID 就是 03b80e5a-ede8-4b33-a751-6ce34ec4c700
     在发现Characteristics中 选择你需要的Characteristics 比如这里的 midi UUID 7772e5db-3868-4112-a1a9-f2669d106bf3
     如果要需要接受蓝牙发送回来的数据 就需要选定某一个Characteristics 然后 setCharacteristicNotification 为 true，这样就打开了通道，蓝牙作为server， app作为 client
     蓝牙发送的数据都是通过 Characteristics来传送的基本都是二进制的。app 这里以回调的方法接受数据

     连接设备时会触发 BluetoothProfile.STATE_CONNECTED
     */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i(TAG,"onConnectionStateChange Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i(TAG,"gattCallback - STATE_CONNECTED");
                    Log.i(TAG,"gattCallback - devicename" + gatt.getDevice().getName());
                    selectedDeviceMac = gatt.getDevice().getAddress();
                    selectedDeviceName = gatt.getDevice().getName();
                    if(bleCallBack != null){
                        bleCallBack.connectStatus("CONNECTED");
                    }
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e(TAG,"gattCallback STATE_DISCONNECTED");
                    if(bleCallBack != null){
                        bleCallBack.connectStatus("DISCONNECTED");
                    }
                    break;
                default:
                    Log.e(TAG,"gattCallback STATE_OTHER");
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();
            Log.i(TAG,"onServicesDiscovered:" +  services.toString());
            //会触发 onCharacteristicRead 方法
            gatt.readCharacteristic(services.get(1).getCharacteristics().get
                    (0));

            for (BluetoothGattService service : services) {
                Log.i(TAG,"onServicesDiscovered:"+ service.getUuid().toString());
                if(APPLE_BLE_MIDI_SERVICE_UUID.equalsIgnoreCase(service.getUuid().toString())){
                    for(BluetoothGattCharacteristic characteristic :  service.getCharacteristics()){
                        Log.i(TAG,"characteristic:"+ characteristic.getProperties()+">"+characteristic.getUuid().toString());
                        if(APPLE_BLE_MIDI_CHARACTERISTIC_UUID.equalsIgnoreCase(characteristic.getUuid().toString())){
                            //打开通道 接受蓝牙钢琴按键返回的值 蓝牙那边弹琴就会触发 onCharacteristicChanged方法 可以在该方法加 callback
                            gatt.setCharacteristicNotification(characteristic, true);
                            gatt.readCharacteristic(characteristic);
                        }
                    }
                }
            }
        }

        /*调用 gatt.readCharacteristic 会 回调该方法*/
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic
                                                 characteristic, int status) {
            Log.i(TAG,"onCharacteristicRead:" + characteristic.toString());

            //gatt.disconnect();
        }

        /*蓝牙弹琴的时候触发该方法*/
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.i(TAG,"onCharacteristicChanged:" + characteristic.toString());

            byte[] data = characteristic.getValue();
            if( bleCallBack!=null ){
                bleCallBack.inCommingData(data);
            }
        }
    };

    /*主要是用户连接了设备，然后在设备列表页，设备断电了，那么要从列表里面删除，不过这个要看 disconnect 事件什么时候传过来*/
    private void removeDevice(String deviceName){
        deviceMap.remove(deviceName);
        Intent intent = new Intent( INTENT_DEVICE_DETECTED ) ;
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast( intent ) ;
    }


    /**
     * BLE 回调接口
     * */
    public interface BleCallBack{
        /**
         *检测到蓝牙设备 触发
         *用于在UI显示设备列表 */
        void deviceDetected(String name);

        /**
         * 蓝牙 断开或者连接 触发*/
        void connectStatus(String status);

        /**
         * 蓝牙发送数据过来 触发
         * 需要在连接的时候打开 某个 characteristic*/
        void inCommingData(byte[] data);
    }
}
