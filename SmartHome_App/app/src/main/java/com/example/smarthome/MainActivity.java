package com.example.smarthome;

import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smarthome.model.Device;
import com.example.smarthome.data.HomeRepository;
import com.example.smarthome.data.RepositoryCallback;
import com.example.smarthome.integration.ControllerGateway;
import com.example.smarthome.service.DeviceControlService;
import com.example.smarthome.model.LightDevice;
import com.example.smarthome.model.RelayDevice;
import com.example.smarthome.model.WaterDevice;
import com.example.smarthome.model.Room;
import com.example.smarthome.ui.adapter.DeviceAdapter;
import com.example.smarthome.ui.adapter.RoomAdapter;
import com.example.smarthome.ui.dialog.LightControlDialog;
import com.example.smarthome.ui.dialog.AddEspDeviceDialog;
import com.example.smarthome.ui.dialog.NameInputDialog;
import com.example.smarthome.ui.dialog.RelayControlDialog;
import com.example.smarthome.ui.dialog.WaterControlDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity
{
    // Ширина одной карточки в dp: от неё зависит число колонок.
    private static final int CARD_WIDTH_DP = 300;

    // Ограничение длины названия комнаты совпадает с проверкой в Room.setName.
    private static final int ROOM_NAME_MAX_LENGTH = 24;

    private TextView textDate;
    private TextView textGreeting;
    private TextView textDevicesCount;
    private RecyclerView recyclerRooms;
    private RoomAdapter roomAdapter;
    private DeviceAdapter deviceAdapter;
    private HomeRepository repository;
    private DeviceControlService controlService;
    private final Map<Integer, ControllerGateway.Subscription> subscriptions = new HashMap<>();
    private boolean started;

    private List<Room> rooms = new ArrayList<Room>();
    private List<Device> devices = new ArrayList<Device>();

    private int selectedRoomId = RoomAdapter.ALL_DEVICES_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        SmartHomeApplication app = (SmartHomeApplication) getApplication();
        repository = app.repository();
        controlService = app.controlService();
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        View mainView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, new OnApplyWindowInsetsListener()
        {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat insets)
            {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            }
        });

        textDate = findViewById(R.id.textDate);
        textGreeting = findViewById(R.id.textGreeting);
        textDevicesCount = findViewById(R.id.textDevicesCount);

        setupRoomList();
        setupDeviceList();
        setupAddDeviceButton();
        showDateAndGreeting();
        refreshScreen();
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        loadData();
    }

    @Override protected void onStop() {
        started = false;
        clearSubscriptions();
        super.onStop();
    }

    private void loadData() {
        repository.getRooms(new RepositoryCallback<List<Room>>() {
            @Override public void onSuccess(List<Room> saved) {
                if (!started) return;
                rooms = saved;
                refreshScreen();
            }
            @Override public void onError(Exception error) { showError(error); }
        });
        repository.getDevices(new RepositoryCallback<List<Device>>() {
            @Override public void onSuccess(List<Device> saved) {
                if (!started) return;
                devices = saved;
                refreshScreen();
                clearSubscriptions();
                for (Device device : devices) observeDevice(device);
            }
            @Override public void onError(Exception error) { showError(error); }
        });
    }

    private void observeDevice(Device device) {
        subscriptions.put(device.getId(), controlService.subscribeToDevice(device,
                new DeviceControlService.Callback<Device>() {
            @Override public void onSuccess(Device saved) { replaceDevice(saved); }
            @Override public void onError(Exception error) { /* The last state remains visible. */ }
            @Override public void onStateSaveError(Device confirmed, Exception error) {
                replaceDevice(confirmed);
                showError(error);
            }
        }));
    }

    private void clearSubscriptions() {
        for (ControllerGateway.Subscription subscription : subscriptions.values()) subscription.cancel();
        subscriptions.clear();
    }

    private void replaceDevice(Device updated) {
        if (!started) return;
        for (int index = 0; index < devices.size(); index++) {
            if (devices.get(index).getId() == updated.getId()) {
                devices.set(index, updated);
                refreshScreen();
                return;
            }
        }
    }

    private void showError(Exception error) {
        if (started) Toast.makeText(this, error.getMessage() == null ? "Ошибка" : error.getMessage(),
                Toast.LENGTH_LONG).show();
    }

    private void setupRoomList()
    {
        recyclerRooms = findViewById(R.id.recyclerRooms);

        roomAdapter = new RoomAdapter(new RoomAdapter.RoomClickListener()
        {
            @Override
            public void onAllDevicesClicked()
            {
                selectedRoomId = RoomAdapter.ALL_DEVICES_ID;
                refreshScreen();
            }

            @Override
            public void onRoomClicked(Room room)
            {
                selectedRoomId = room.getId();
                refreshScreen();
            }

            @Override
            public void onRoomLongClicked(Room room, View anchor)
            {
                showRoomMenu(room, anchor);
            }

            @Override
            public void onAddRoomClicked()
            {
                showAddRoomDialog();
            }
        });
        recyclerRooms.setAdapter(roomAdapter);
    }

    private void setupDeviceList()
    {
        RecyclerView recyclerDevices = findViewById(R.id.recyclerDevices);

        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        int columns = screenWidthDp / CARD_WIDTH_DP;
        if (columns < 1)
        {
            columns = 1;
        }
        recyclerDevices.setLayoutManager(new GridLayoutManager(this, columns));

        deviceAdapter = new DeviceAdapter(this, new DeviceAdapter.DeviceClickListener()
        {
            @Override
            public void onDeviceClicked(Device device)
            {
                showDeviceControlDialog(device);
            }

            @Override
            public void onDeviceMenuClicked(Device device, View anchor)
            {
                showDeviceMenu(device, anchor);
            }
        });
        recyclerDevices.setAdapter(deviceAdapter);
    }

    private void setupAddDeviceButton()
    {
        View buttonAddDevice = findViewById(R.id.buttonAddDevice);
        buttonAddDevice.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK")
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.ACCESS_LOCAL_NETWORK"}, 3101);
                } else {
                    showAddDeviceDialog();
                }
            }
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 3101) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showAddDeviceDialog();
        } else {
            Toast.makeText(this, R.string.esp_network_permission, Toast.LENGTH_LONG).show();
        }
    }

    private void showAddDeviceDialog() {
        AddEspDeviceDialog.show(this, rooms, selectedRoomId, controlService, this::loadData);
    }

    // Дата и приветствие считаются по текущему времени телефона.
    private void showDateAndGreeting()
    {
        Locale locale = getResources().getConfiguration().getLocales().get(0);

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d MMMM", locale);
        String dateText = dateFormat.format(new Date());
        textDate.setText(dateText.toUpperCase(locale));

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        int greetingResId;
        if (hour >= 5 && hour < 12)
        {
            greetingResId = R.string.haven_greeting_morning;
        }
        else if (hour >= 12 && hour < 18)
        {
            greetingResId = R.string.haven_greeting_day;
        }
        else if (hour >= 18 && hour < 23)
        {
            greetingResId = R.string.haven_greeting_evening;
        }
        else
        {
            greetingResId = R.string.haven_greeting_night;
        }
        textGreeting.setText(greetingResId);
    }

    // Пересчитывает счётчики и список карточек по выбранной комнате.
    private void refreshScreen()
    {
        Map<Integer, Integer> deviceCounts = new HashMap<Integer, Integer>();
        Map<Integer, String> roomNames = new HashMap<Integer, String>();

        for (Room room : rooms)
        {
            deviceCounts.put(room.getId(), 0);
            roomNames.put(room.getId(), room.getName());
        }

        List<Device> visibleDevices = new ArrayList<Device>();
        int onlineCount = 0;

        for (Device device : devices)
        {
            Integer oldCount = deviceCounts.get(device.getRoomId());
            if (oldCount != null)
            {
                deviceCounts.put(device.getRoomId(), oldCount.intValue() + 1);
            }

            boolean isVisible;
            if (selectedRoomId == RoomAdapter.ALL_DEVICES_ID)
            {
                isVisible = true;
            }
            else
            {
                isVisible = (device.getRoomId() == selectedRoomId);
            }

            if (isVisible)
            {
                visibleDevices.add(device);
                if (device.isOnline())
                {
                    onlineCount = onlineCount + 1;
                }
            }
        }

        roomAdapter.setData(rooms, deviceCounts, devices.size(), selectedRoomId);
        deviceAdapter.setData(visibleDevices, roomNames);
        textDevicesCount.setText(getString(R.string.haven_devices_online, onlineCount));
    }

    // Нажатие на карточку открывает диалог управления по типу устройства.
    private void showDeviceControlDialog(Device device)
    {
        if (device instanceof LightDevice)
        {
            showLightControlDialog((LightDevice) device);
        }
        else if (device instanceof RelayDevice)
        {
            showRelayControlDialog((RelayDevice) device);
        }
        else if (device instanceof WaterDevice)
        {
            showWaterControlDialog((WaterDevice) device);
        }
    }

    // Свет управляется TCP-командой 0x03: ID, питание и яркость.
    private void showLightControlDialog(LightDevice device)
    {
        LightControlDialog dialog = new LightControlDialog(this, device,
                new LightControlDialog.LightControlListener()
                {
                    @Override
                    public String onLightApplyRequested(boolean poweredOn, int brightness)
                    {
                        controlService.setLightState(device, poweredOn, brightness,
                                device.getLightTemperature(),
                                new DeviceControlService.Callback<LightDevice>()
                                {
                                    @Override
                                    public void onSuccess(LightDevice saved)
                                    {
                                        replaceDevice(saved);
                                    }

                                    @Override
                                    public void onError(Exception error)
                                    {
                                        showError(error);
                                    }

                                    @Override
                                    public void onStateSaveError(LightDevice confirmed, Exception error)
                                    {
                                        replaceDevice(confirmed);
                                        showError(error);
                                    }
                                });
                        return null;
                    }
                });
        dialog.show();
    }

    private void showRelayControlDialog(RelayDevice device)
    {
        RelayControlDialog dialog = new RelayControlDialog(this, device,
                new RelayControlDialog.RelayControlListener()
                {
                    @Override
                    public String onRelayApplyRequested(boolean poweredOn)
                    {
                        return getString(R.string.haven_control_unavailable);
                    }
                });
        dialog.show();
    }

    private void showWaterControlDialog(WaterDevice device)
    {
        WaterControlDialog dialog = new WaterControlDialog(this, device,
                new WaterControlDialog.WaterControlListener()
                {
                    @Override
                    public String onValveRequested(boolean open)
                    {
                        return getString(R.string.haven_control_unavailable);
                    }
                });
        dialog.show();
    }

    // Меню «⋮» на карточке: переименование и удаление устройства.
    private void showDeviceMenu(final Device device, View anchor)
    {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.menu_device_actions, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener()
        {
            @Override
            public boolean onMenuItemClick(MenuItem item)
            {
                int itemId = item.getItemId();

                if (itemId == R.id.action_rename_device)
                {
                    showRenameDeviceDialog(device);
                    return true;
                }
                else if (itemId == R.id.action_delete_device)
                {
                    showDeleteDeviceDialog(device);
                    return true;
                }
                else
                {
                    return false;
                }
            }
        });
        popupMenu.show();
    }

    private void showRenameDeviceDialog(final Device device)
    {
        NameInputDialog.show(this, R.string.haven_rename_device_title, R.string.haven_device_name_hint,
                device.getName(), 0, R.string.haven_save, new NameInputDialog.NameListener()
                {
                    @Override
                    public String onNameEntered(String name)
                    {
                        try
                        {
                            if (name.trim().isEmpty()) return getString(R.string.esp_need_name);
                            repository.renameDevice(device.getId(), name, new RepositoryCallback<Device>() {
                                @Override public void onSuccess(Device saved) { loadData(); }
                                @Override public void onError(Exception error) { showError(error); }
                            });
                        }
                        catch (IllegalArgumentException exception)
                        {
                            return getErrorText(exception);
                        }

                        return null;
                    }
                });
    }

    private void showDeleteDeviceDialog(final Device device)
    {
        String message = getString(R.string.haven_delete_device_message, device.getName());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.haven_delete_device_title);
        builder.setMessage(message);
        builder.setNegativeButton(R.string.haven_cancel, null);
        builder.setPositiveButton(R.string.haven_menu_delete, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                repository.deleteDevice(device.getId(), new RepositoryCallback<Boolean>() {
                    @Override public void onSuccess(Boolean result) { loadData(); }
                    @Override public void onError(Exception error) { showError(error); }
                });
            }
        });
        builder.show();
    }

    // Меню комнаты (долгое нажатие на чип): переименование и удаление вместе с устройствами.
    private void showRoomMenu(final Room room, View anchor)
    {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.menu_room_actions, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener()
        {
            @Override
            public boolean onMenuItemClick(MenuItem item)
            {
                int itemId = item.getItemId();

                if (itemId == R.id.action_rename_room)
                {
                    showRenameRoomDialog(room);
                    return true;
                }
                else if (itemId == R.id.action_delete_room)
                {
                    showDeleteRoomDialog(room);
                    return true;
                }
                else
                {
                    return false;
                }
            }
        });
        popupMenu.show();
    }

    private void showAddRoomDialog()
    {
        NameInputDialog.show(this, R.string.haven_add_room_title, R.string.haven_room_name_hint,
                "", ROOM_NAME_MAX_LENGTH, R.string.haven_create, new NameInputDialog.NameListener()
                {
                    @Override
                    public String onNameEntered(String name)
                    {
                        try
                        {
                            if (name.trim().isEmpty()) return getString(R.string.haven_error_name_invalid);
                            repository.addRoom(name, new RepositoryCallback<Room>() {
                                @Override public void onSuccess(Room saved) {
                                    loadData();
                                    recyclerRooms.smoothScrollToPosition(rooms.size());
                                }
                                @Override public void onError(Exception error) { showError(error); }
                            });
                        }
                        catch (IllegalArgumentException exception)
                        {
                            return getErrorText(exception);
                        }

                        return null;
                    }
                });
    }

    private void showRenameRoomDialog(final Room room)
    {
        NameInputDialog.show(this, R.string.haven_rename_room_title, R.string.haven_room_name_hint,
                room.getName(), ROOM_NAME_MAX_LENGTH, R.string.haven_save, new NameInputDialog.NameListener()
                {
                    @Override
                    public String onNameEntered(String name)
                    {
                        try
                        {
                            if (name.trim().isEmpty()) return getString(R.string.haven_error_name_invalid);
                            repository.renameRoom(room.getId(), name, new RepositoryCallback<Room>() {
                                @Override public void onSuccess(Room saved) { loadData(); }
                                @Override public void onError(Exception error) { showError(error); }
                            });
                        }
                        catch (IllegalArgumentException exception)
                        {
                            return getErrorText(exception);
                        }

                        return null;
                    }
                });
    }

    // Перед удалением показываем имя комнаты и число устройств, которые удалятся вместе с ней.
    private void showDeleteRoomDialog(final Room room)
    {
        int deviceCount = 0;
        for (Device device : devices)
        {
            if (device.getRoomId() == room.getId())
            {
                deviceCount = deviceCount + 1;
            }
        }

        String message = getString(R.string.haven_delete_room_message, room.getName(), deviceCount);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.haven_delete_room_title);
        builder.setMessage(message);
        builder.setNegativeButton(R.string.haven_cancel, null);
        builder.setPositiveButton(R.string.haven_menu_delete, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                removeRoomWithDevices(room);
            }
        });
        builder.show();
    }

    private void removeRoomWithDevices(Room room)
    {
        repository.deleteRoom(room.getId(), new RepositoryCallback<Boolean>() {
            @Override public void onSuccess(Boolean result) {
                if (selectedRoomId == room.getId()) selectedRoomId = RoomAdapter.ALL_DEVICES_ID;
                loadData();
            }
            @Override public void onError(Exception error) { showError(error); }
        });
    }

    private String getErrorText(IllegalArgumentException exception)
    {
        String message = exception.getMessage();
        if (message == null)
        {
            message = getString(R.string.haven_error_name_invalid);
        }
        return message;
    }

}
