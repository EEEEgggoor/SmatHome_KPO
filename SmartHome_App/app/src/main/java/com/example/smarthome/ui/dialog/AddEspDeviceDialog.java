package com.example.smarthome.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.smarthome.R;
import com.example.smarthome.integration.ControllerGateway;
import com.example.smarthome.model.Device;
import com.example.smarthome.model.Room;
import com.example.smarthome.service.DeviceControlService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/** Adds an existing Wi-Fi controller or provisions a new one over USB CDC. */
public final class AddEspDeviceDialog {
    private AddEspDeviceDialog() { }

    public static void show(Context context, List<Room> rooms, int selectedRoomId,
                            DeviceControlService service, Runnable onAdded) {
        if (rooms.isEmpty()) {
            Toast.makeText(context, R.string.esp_need_room, Toast.LENGTH_SHORT).show();
            return;
        }
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_add_esp, null);
        TextInputLayout nameLayout = content.findViewById(R.id.inputDeviceName);
        TextInputLayout ssidLayout = content.findViewById(R.id.inputSsid);
        TextInputEditText name = content.findViewById(R.id.editDeviceName);
        TextInputEditText ssid = content.findViewById(R.id.editSsid);
        TextInputEditText password = content.findViewById(R.id.editPassword);
        TextView status = content.findViewById(R.id.textConnectStatus);
        TextView scanStatus = content.findViewById(R.id.textScanStatus);
        Button scanButton = content.findViewById(R.id.buttonScanWifi);
        RadioGroup discoveredGroup = content.findViewById(R.id.groupDiscovered);
        Spinner room = content.findViewById(R.id.spinnerRoom);
        ArrayList<String> names = new ArrayList<>();
        int selected = 0;
        for (int index = 0; index < rooms.size(); index++) {
            names.add(rooms.get(index).getName());
            if (rooms.get(index).getId() == selectedRoomId) selected = index;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        room.setAdapter(adapter);
        room.setSelection(selected);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.esp_add_title)
                .setView(content)
                .setPositiveButton(R.string.esp_connect, null)
                .setNeutralButton(R.string.esp_wifi_add, null)
                .setNegativeButton(R.string.haven_cancel, null)
                .create();
        final ControllerGateway.ConnectionAttempt[] attempt = {null};
        final ControllerGateway.ConnectionAttempt[] scan = {null};
        final boolean[] completed = {false};
        final boolean[] dismissed = {false};
        final List<ControllerGateway.DiscoveredDevice> found = new ArrayList<>();

        dialog.setOnShowListener(ignored -> {
            Button usbAdd = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button wifiAdd = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            wifiAdd.setEnabled(false);
            DeviceControlService.ConnectionCallback connection = new DeviceControlService.ConnectionCallback() {
                @Override public void onProgress(ControllerGateway.ConnectionStage stage, Integer percent) {
                    if (dismissed[0]) return;
                    int stringId;
                    switch (stage) {
                        case SENDING_WIFI_SETTINGS: stringId = R.string.esp_sending; break;
                        case CONNECTING_TO_NETWORK: stringId = R.string.esp_network; break;
                        case READING_STATE: stringId = R.string.esp_reading; break;
                        default: stringId = R.string.esp_wifi_scanning;
                    }
                    status.setText(stringId);
                }

                @Override public void onSuccess(Device device) {
                    if (dismissed[0]) return;
                    completed[0] = true;
                    dialog.dismiss();
                    Toast.makeText(context, R.string.esp_connected, Toast.LENGTH_SHORT).show();
                    onAdded.run();
                }

                @Override public void onError(Exception error) {
                    if (dismissed[0]) return;
                    usbAdd.setEnabled(true);
                    ControllerGateway.DiscoveredDevice chosen = selectedDevice(discoveredGroup, found);
                    wifiAdd.setEnabled(chosen != null && chosen.type != 0);
                    status.setText(message(error));
                    attempt[0] = null;
                }

                @Override public void onStateSaveError(DeviceControlService.ConnectionInfo info,
                                                        Exception error) {
                    if (dismissed[0]) return;
                    usbAdd.setEnabled(true);
                    ControllerGateway.DiscoveredDevice chosen = selectedDevice(discoveredGroup, found);
                    wifiAdd.setEnabled(chosen != null && chosen.type != 0);
                    status.setText(context.getString(R.string.esp_save_failed, message(error)));
                    attempt[0] = null;
                }
            };

            discoveredGroup.setOnCheckedChangeListener((group, checkedId) -> {
                ControllerGateway.DiscoveredDevice chosen = selectedDevice(discoveredGroup, found);
                wifiAdd.setEnabled(chosen != null && chosen.type != 0 && attempt[0] == null);
                if (chosen != null && chosen.type == 0) {
                    scanStatus.setText(R.string.esp_wifi_light_unavailable);
                } else if (chosen != null) {
                    scanStatus.setText(R.string.esp_wifi_select);
                }
            });
            scanButton.setOnClickListener(view -> startScan(context, service, scanStatus,
                    scanButton, wifiAdd, discoveredGroup, found, scan, dismissed));

            usbAdd.setOnClickListener(view -> {
                if (!validName(context, name, nameLayout)) return;
                String networkName = ssid.getText() == null ? "" : ssid.getText().toString();
                String secret = password.getText() == null ? "" : password.getText().toString();
                ssidLayout.setError(null);
                if (networkName.isEmpty()) {
                    ssidLayout.setError(context.getString(R.string.esp_ssid));
                    return;
                }
                usbAdd.setEnabled(false);
                wifiAdd.setEnabled(false);
                status.setVisibility(View.VISIBLE);
                status.setText(R.string.esp_searching);
                attempt[0] = service.connectDevice(name.getText().toString(),
                        rooms.get(room.getSelectedItemPosition()).getId(),
                        networkName, secret, connection);
            });

            wifiAdd.setOnClickListener(view -> {
                ControllerGateway.DiscoveredDevice chosen = selectedDevice(discoveredGroup, found);
                if (chosen == null || chosen.type == 0 || !validName(context, name, nameLayout)) return;
                usbAdd.setEnabled(false);
                wifiAdd.setEnabled(false);
                status.setVisibility(View.VISIBLE);
                status.setText(R.string.esp_reading);
                attempt[0] = service.addDiscoveredDevice(name.getText().toString(),
                        rooms.get(room.getSelectedItemPosition()).getId(), chosen.controllerId,
                        connection);
            });
            startScan(context, service, scanStatus, scanButton, wifiAdd,
                    discoveredGroup, found, scan, dismissed);
        });
        dialog.setOnDismissListener(ignored -> {
            dismissed[0] = true;
            if (scan[0] != null) scan[0].cancel();
            if (!completed[0] && attempt[0] != null) attempt[0].cancel();
        });
        dialog.show();
    }

    private static void startScan(Context context, DeviceControlService service,
                                  TextView scanStatus, Button scanButton, Button wifiAdd,
                                  RadioGroup group, List<ControllerGateway.DiscoveredDevice> found,
                                  ControllerGateway.ConnectionAttempt[] scan, boolean[] dismissed) {
        if (scan[0] != null) scan[0].cancel();
        group.removeAllViews();
        found.clear();
        scanButton.setEnabled(false);
        wifiAdd.setEnabled(false);
        scanStatus.setText(R.string.esp_wifi_scanning);
        scan[0] = service.scanUnpairedDevices(new DeviceControlService.ScanCallback() {
            @Override public void onSuccess(List<ControllerGateway.DiscoveredDevice> devices) {
                if (dismissed[0]) return;
                scan[0] = null;
                scanButton.setEnabled(true);
                found.addAll(devices);
                scanStatus.setText(found.isEmpty() ? R.string.esp_wifi_empty : R.string.esp_wifi_select);
                for (int index = 0; index < found.size(); index++) {
                    ControllerGateway.DiscoveredDevice device = found.get(index);
                    RadioButton option = new RadioButton(context);
                    option.setId(View.generateViewId());
                    option.setTag(index);
                    int typeName = device.type == 1 ? R.string.esp_wifi_type_relay
                            : device.type == 2 ? R.string.esp_wifi_type_water
                            : R.string.esp_wifi_type_light;
                    option.setText(context.getString(R.string.esp_wifi_device_format,
                            context.getString(typeName), device.controllerId, device.address));
                    group.addView(option);
                }
            }

            @Override public void onError(Exception error) {
                if (dismissed[0]) return;
                scan[0] = null;
                scanButton.setEnabled(true);
                scanStatus.setText(message(error));
            }
        });
    }

    private static ControllerGateway.DiscoveredDevice selectedDevice(RadioGroup group,
            List<ControllerGateway.DiscoveredDevice> found) {
        View selected = group.findViewById(group.getCheckedRadioButtonId());
        if (selected == null || !(selected.getTag() instanceof Integer)) return null;
        int index = (Integer) selected.getTag();
        return index >= 0 && index < found.size() ? found.get(index) : null;
    }

    private static boolean validName(Context context, TextInputEditText name,
                                     TextInputLayout nameLayout) {
        nameLayout.setError(null);
        if (name.getText() != null && !name.getText().toString().trim().isEmpty()) return true;
        nameLayout.setError(context.getString(R.string.esp_need_name));
        return false;
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? "Ошибка подключения." : error.getMessage();
    }
}
