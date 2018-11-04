package com.feite.ble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class BleActivity extends AppCompatActivity {

    private final static String TAG = BleActivity.class.getSimpleName();

    private int REQUEST_ENABLE_BT = 1;

    private BleManager bleManager;

    private Button stopscan, startscan;

    private TextView status, node;

    private ListView lv_device;

    private DeviceAdapter deviceAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stopscan = findViewById(R.id.stopscan);
        startscan = findViewById(R.id.startscan);

        stopscan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bleManager.scanLeDevice(false);
                bleManager.disconnect();
            }
        });

        startscan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bleManager.scanLeDevice(true);
            }
        });

        status = findViewById(R.id.status);

        node = findViewById(R.id.node);

        lv_device = findViewById(R.id.lv_device);

        lv_device.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bleManager.disconnect();//先断开之前一个蓝牙设备,再连接选中的设备
                bleManager.connectDevice( deviceAdapter.getItem(position));
            }
        });

        bleManager = new BleManager(this);

        deviceAdapter = new DeviceAdapter(new ArrayList<String>(),this);

        lv_device.setAdapter(deviceAdapter);

        bleManager.setBleCallBack(new BleManager.BleCallBack() {

            @Override
            public void deviceDetected(final String name) {
                Log.e(TAG,"deviceDetected:"+Thread.currentThread().getId()+">"+name);
                deviceAdapter.setList(bleManager.getDeviceNameList());
                deviceAdapter.notifyDataSetChanged();

            }

            @Override
            public void connectStatus(final String status) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        BleActivity.this.status.setText(status);
                    }
                });
            }

            @Override
            public void inCommingData(byte[] data) {
                //Log.e("onCharacteristicChanged","data.length:"+data.length);
                //Log.e("onCharacteristicChanged","byte str:"+HexConver.conver2HexStr(data));
                final String hexStr = HexConver.conver16HexStr(data);
                Log.e(TAG,"inCommingData hex str:"+hexStr);
                int[] array = HexConver.bytearray2intarray(data);
                for (int b :  array){
                    Log.e(TAG,"inCommingData >"+b);
                }

                //这个是另外的线程
                Log.e(TAG,"thread:" + Thread.currentThread().getId());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //这个是UI主线程
                        Log.e(TAG,"thread:" + Thread.currentThread().getId());
                        BleActivity.this.node.setText(hexStr);
                    }
                });
            }
        });

        bleManager.onActivityCreate();
    }


    @Override
    protected void onResume() {
        super.onResume();
        if ( !bleManager.isBtEnable()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            bleManager.onActivityResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        bleManager.onActivityPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bleManager.onActivityDestory();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        //System.exit(0);
    }
}
