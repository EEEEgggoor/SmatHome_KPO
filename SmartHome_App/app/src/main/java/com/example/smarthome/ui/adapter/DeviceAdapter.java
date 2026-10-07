package com.example.smarthome.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smarthome.R;
import com.example.smarthome.model.Device;
import com.example.smarthome.model.LightDevice;
import com.example.smarthome.model.RelayDevice;
import com.example.smarthome.model.WaterDevice;
import com.example.smarthome.presentation.DeviceValueFormatter;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Карточки устройств: свет, реле и датчик воды.
 * Адаптер только показывает состояние модели и ничего не отправляет оборудованию.
 */
public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.BaseDeviceViewHolder>
{
    private static final int VIEW_TYPE_LIGHT = 1;
    private static final int VIEW_TYPE_RELAY = 2;
    private static final int VIEW_TYPE_WATER = 3;

    // Для устройства, потерявшего связь, последнее значение остаётся, но выглядит устаревшим.
    private static final float STALE_ALPHA = 0.5f;

    public interface DeviceClickListener
    {
        void onDeviceClicked(Device device);

        void onDeviceMenuClicked(Device device, View anchor);
    }

    private final DeviceClickListener listener;
    private final DeviceValueFormatter formatter;
    private final List<Device> devices = new ArrayList<Device>();
    private final Map<Integer, String> roomNames = new HashMap<Integer, String>();

    public DeviceAdapter(Context context, DeviceClickListener listener)
    {
        if (context == null)
        {
            throw new IllegalArgumentException("Контекст не должен быть null.");
        }
        if (listener == null)
        {
            throw new IllegalArgumentException("Обработчик нажатий не должен быть null.");
        }
        this.listener = listener;
        this.formatter = new DeviceValueFormatter(context.getResources());
    }

    public void setData(List<Device> newDevices, Map<Integer, String> newRoomNames)
    {
        devices.clear();
        devices.addAll(newDevices);
        roomNames.clear();
        roomNames.putAll(newRoomNames);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position)
    {
        Device device = devices.get(position);

        if (device instanceof LightDevice)
        {
            return VIEW_TYPE_LIGHT;
        }
        else if (device instanceof RelayDevice)
        {
            return VIEW_TYPE_RELAY;
        }
        else if (device instanceof WaterDevice)
        {
            return VIEW_TYPE_WATER;
        }
        else
        {
            throw new IllegalStateException("Неизвестный тип устройства.");
        }
    }

    @NonNull
    @Override
    public BaseDeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE_LIGHT)
        {
            View view = inflater.inflate(R.layout.item_device_light, parent, false);
            return new LightViewHolder(view);
        }
        else if (viewType == VIEW_TYPE_RELAY)
        {
            View view = inflater.inflate(R.layout.item_device_relay, parent, false);
            return new RelayViewHolder(view);
        }
        else
        {
            View view = inflater.inflate(R.layout.item_device_water, parent, false);
            return new WaterViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull BaseDeviceViewHolder holder, int position)
    {
        final Device device = devices.get(position);

        String roomName = roomNames.get(device.getRoomId());
        if (roomName == null)
        {
            roomName = "";
        }

        Context context = holder.itemView.getContext();
        holder.textDeviceName.setText(device.getName());
        holder.textDeviceSubtitle.setText(
                context.getString(R.string.haven_device_subtitle, roomName, device.getControllerId()));

        if (holder instanceof LightViewHolder)
        {
            bindLight((LightViewHolder) holder, (LightDevice) device);
        }
        else if (holder instanceof RelayViewHolder)
        {
            bindRelay((RelayViewHolder) holder, (RelayDevice) device);
        }
        else if (holder instanceof WaterViewHolder)
        {
            bindWater((WaterViewHolder) holder, (WaterDevice) device);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                listener.onDeviceClicked(device);
            }
        });

        holder.buttonDeviceMenu.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                listener.onDeviceMenuClicked(device, view);
            }
        });
    }

    @Override
    public int getItemCount()
    {
        return devices.size();
    }

    private void bindLight(LightViewHolder holder, LightDevice light)
    {
        holder.textDeviceValue.setText(formatter.formatBrightness(light.getBrightness()));

        if (!light.isOnline())
        {
            showStale(holder, holder.textDeviceValue);
        }
        else if (light.isPoweredOn())
        {
            showStatus(holder.textDeviceStatus, formatter.formatPowerState(true), R.color.haven_primary);
            holder.textDeviceValue.setAlpha(1.0f);
        }
        else
        {
            showStatus(holder.textDeviceStatus, formatter.formatPowerState(false), R.color.haven_status_off);
            holder.textDeviceValue.setAlpha(1.0f);
        }
    }

    private void bindRelay(RelayViewHolder holder, RelayDevice relay)
    {
        // Переключатель только показывает состояние. Управление появится вместе с DeviceControlService.
        holder.switchRelay.setChecked(relay.isPoweredOn());

        if (!relay.isOnline())
        {
            showStale(holder, holder.switchRelay);
        }
        else if (relay.isPoweredOn())
        {
            showStatus(holder.textDeviceStatus, formatter.formatPowerState(true), R.color.haven_primary);
            holder.switchRelay.setAlpha(1.0f);
        }
        else
        {
            showStatus(holder.textDeviceStatus, formatter.formatPowerState(false), R.color.haven_status_off);
            holder.switchRelay.setAlpha(1.0f);
        }
    }

    private void bindWater(WaterViewHolder holder, WaterDevice water)
    {
        holder.textDeviceValue.setText(formatter.formatWaterLevel(water.getWaterLevel()));

        if (!water.isOnline())
        {
            showStale(holder, holder.textDeviceValue);
        }
        else
        {
            String levelStatus = water.getLevelStatus();
            String statusText = formatter.formatLevelStatus(levelStatus);

            if (levelStatus == null)
            {
                showStatus(holder.textDeviceStatus, statusText, R.color.haven_status_off);
            }
            else
            {
                showStatus(holder.textDeviceStatus, statusText, R.color.haven_primary);
            }
            holder.textDeviceValue.setAlpha(1.0f);
        }
    }

    // Нет связи: подпись «Не в сети», последнее значение остаётся, но бледнеет.
    private void showStale(BaseDeviceViewHolder holder, View valueView)
    {
        showStatus(holder.textDeviceStatus, formatter.formatOnlineState(false), R.color.haven_status_stale);
        valueView.setAlpha(STALE_ALPHA);
    }

    private void showStatus(TextView textView, String text, int colorResId)
    {
        int color = ContextCompat.getColor(textView.getContext(), colorResId);
        textView.setText(text);
        textView.setTextColor(color);
        TextViewCompat.setCompoundDrawableTintList(textView, ColorStateList.valueOf(color));
    }

    public abstract static class BaseDeviceViewHolder extends RecyclerView.ViewHolder
    {
        final TextView textDeviceName;
        final TextView textDeviceSubtitle;
        final TextView textDeviceStatus;
        final View buttonDeviceMenu;

        BaseDeviceViewHolder(@NonNull View itemView)
        {
            super(itemView);
            textDeviceName = itemView.findViewById(R.id.textDeviceName);
            textDeviceSubtitle = itemView.findViewById(R.id.textDeviceSubtitle);
            textDeviceStatus = itemView.findViewById(R.id.textDeviceStatus);
            buttonDeviceMenu = itemView.findViewById(R.id.buttonDeviceMenu);
        }
    }

    public static class LightViewHolder extends BaseDeviceViewHolder
    {
        final TextView textDeviceValue;

        LightViewHolder(@NonNull View itemView)
        {
            super(itemView);
            textDeviceValue = itemView.findViewById(R.id.textDeviceValue);
        }
    }

    public static class RelayViewHolder extends BaseDeviceViewHolder
    {
        final MaterialSwitch switchRelay;

        RelayViewHolder(@NonNull View itemView)
        {
            super(itemView);
            switchRelay = itemView.findViewById(R.id.switchRelay);
        }
    }

    public static class WaterViewHolder extends BaseDeviceViewHolder
    {
        final TextView textDeviceValue;

        WaterViewHolder(@NonNull View itemView)
        {
            super(itemView);
            textDeviceValue = itemView.findViewById(R.id.textDeviceValue);
        }
    }
}
