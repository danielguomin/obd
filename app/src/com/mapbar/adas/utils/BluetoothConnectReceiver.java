package com.mapbar.adas.utils;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mapbar.hamster.ClsUtils;
import com.mapbar.hamster.log.Log;

public class BluetoothConnectReceiver extends BroadcastReceiver {

    String strPsw = "111111";

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO Auto-generated method stub
        if (intent.getAction().equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                try {
                    /**
                     * cancelPairingUserInput（）取消用户输入密钥框，
                     * 个人觉得一般情况下不要和setPin（setPasskey、setPairingConfirmation、
                     * setRemoteOutOfBandData）一起用，
                     * 这几个方法都会remove掉map里面的key:value（<<<<<也就是互斥的>>>>>>）。
                     */
                    Log.d("BluetoothConnectReceiver success ");
                    abortBroadcast();//如果没有将广播终止，则会出现一个一闪而过的配对框。
                    //1.确认配对
                    //ClsUtils.setPairingConfirmation(device.getClass(), device, true);
                    ClsUtils.setPin(device.getClass(), device, strPsw); // 手机和蓝牙采集器配对
                    //ClsUtils.setPasskey(device.getClass(), device, strPsw);
                    //ClsUtils.cancelPairingUserInput(device.getClass(), device); //一般调用不成功，前言里面讲解过了
                } catch (Exception e) {
                }
            }
        }
    }
}
