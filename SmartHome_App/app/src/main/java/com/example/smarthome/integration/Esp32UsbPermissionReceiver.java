package com.example.smarthome.integration;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Explicit receiver for UsbManager's permission result. */
public final class Esp32UsbPermissionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Esp32ControllerGateway.onUsbPermission(intent);
    }
}
