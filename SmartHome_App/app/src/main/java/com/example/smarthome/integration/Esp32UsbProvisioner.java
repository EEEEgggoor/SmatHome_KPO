package com.example.smarthome.integration;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sends the two lines expected by main.c over a USB CDC ACM data interface. */
final class Esp32UsbProvisioner {
    private Esp32UsbProvisioner() { }

    static UsbDevice findSingleCdcDevice(UsbManager manager) throws IOException {
        UsbDevice found = null;
        for (UsbDevice device : manager.getDeviceList().values()) {
            if (dataInterface(device) == null) continue;
            if (found != null) throw new IOException("Подключено несколько USB CDC устройств. Оставьте один ESP32-S2.");
            found = device;
        }
        if (found == null) throw new IOException("Подключите ESP32-S2 кабелем USB-C/OTG.");
        return found;
    }

    static void provision(UsbManager manager, UsbDevice device, byte[] credentials,
                          AtomicBoolean cancelled) throws IOException {
        UsbInterface data = dataInterface(device);
        if (data == null) throw new IOException("Устройство не предоставляет USB CDC.");
        UsbEndpoint in = null;
        UsbEndpoint out = null;
        for (int index = 0; index < data.getEndpointCount(); index++) {
            UsbEndpoint endpoint = data.getEndpoint(index);
            if (endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
            if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) in = endpoint;
            else out = endpoint;
        }
        if (in == null || out == null) throw new IOException("У USB CDC нет каналов приёма и отправки.");
        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) throw new IOException("Нет доступа к ESP32-S2 через USB.");
        UsbInterface control = controlInterface(device);
        boolean controlClaimed = false;
        try {
            if (!connection.claimInterface(data, true)) throw new IOException("Не удалось открыть USB CDC.");
            if (control != null) {
                controlClaimed = connection.claimInterface(control, true);
                if (!controlClaimed) throw new IOException("Не удалось открыть управление USB CDC.");
                // CDC SET_CONTROL_LINE_STATE: DTR=1, required by tud_cdc_connected().
                int result = connection.controlTransfer(0x21, 0x22, 1,
                        control.getId(), null, 0, 1000);
                if (result < 0) throw new IOException("ESP32-S2 не принял сигнал USB CDC.");
            }
            int offset = 0;
            while (offset < credentials.length) {
                if (cancelled.get()) throw new IOException("Подключение отменено.");
                int count = Math.min(64, credentials.length - offset);
                int written = connection.bulkTransfer(out, credentials, offset, count, 3000);
                if (written <= 0) throw new IOException("Не удалось отправить параметры Wi-Fi по USB.");
                offset += written;
            }
            byte[] buffer = new byte[64];
            StringBuilder line = new StringBuilder();
            long deadline = System.currentTimeMillis() + 90000;
            while (!cancelled.get() && System.currentTimeMillis() < deadline) {
                int count = connection.bulkTransfer(in, buffer, buffer.length, 1000);
                if (count <= 0) continue;
                String chunk = new String(buffer, 0, count, StandardCharsets.US_ASCII);
                for (int i = 0; i < chunk.length(); i++) {
                    char character = chunk.charAt(i);
                    if (character == '\n') {
                        String response = line.toString().trim();
                        if ("WIFI_CONNECTED".equals(response)) return;
                        if ("WIFI_FAILED".equals(response)) {
                            throw new IOException("ESP32-S2 не подключился к Wi-Fi. Проверьте данные сети.");
                        }
                        if ("SERVICE_FAILED".equals(response)) {
                            throw new IOException("Wi-Fi подключён, но TCP-сервис ESP32-S2 не запустился.");
                        }
                        line.setLength(0);
                    } else if (line.length() < 80 && character != '\r') {
                        line.append(character);
                    }
                }
            }
            if (cancelled.get()) throw new IOException("Подключение отменено.");
            throw new IOException("ESP32-S2 не подтвердил подключение к Wi-Fi.");
        } finally {
            connection.releaseInterface(data);
            if (controlClaimed) connection.releaseInterface(control);
            connection.close();
        }
    }

    private static UsbInterface dataInterface(UsbDevice device) {
        for (int index = 0; index < device.getInterfaceCount(); index++) {
            UsbInterface candidate = device.getInterface(index);
            if (candidate.getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA) return candidate;
        }
        return null;
    }

    private static UsbInterface controlInterface(UsbDevice device) {
        for (int index = 0; index < device.getInterfaceCount(); index++) {
            UsbInterface candidate = device.getInterface(index);
            if (candidate.getInterfaceClass() == UsbConstants.USB_CLASS_COMM) return candidate;
        }
        return null;
    }
}
