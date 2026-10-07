package com.example.smarthome.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smarthome.R;
import com.example.smarthome.model.Room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Горизонтальный список: «Все устройства», комнаты и «+ Новая комната».
 * «Все устройства» — режим фильтра, а не комната, поэтому для него служебный id 0.
 */
public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder>
{
    public static final int ALL_DEVICES_ID = 0;

    public interface RoomClickListener
    {
        void onAllDevicesClicked();

        void onRoomClicked(Room room);

        // Долгое нажатие на комнату открывает меню её действий.
        void onRoomLongClicked(Room room, View anchor);

        void onAddRoomClicked();
    }

    private final RoomClickListener listener;
    private final List<Room> rooms = new ArrayList<Room>();
    private final Map<Integer, Integer> deviceCounts = new HashMap<Integer, Integer>();
    private int totalDeviceCount = 0;
    private int selectedRoomId = ALL_DEVICES_ID;

    public RoomAdapter(RoomClickListener listener)
    {
        if (listener == null)
        {
            throw new IllegalArgumentException("Обработчик выбора комнаты не должен быть null.");
        }
        this.listener = listener;
    }

    public void setData(List<Room> newRooms, Map<Integer, Integer> newDeviceCounts,
                        int newTotalDeviceCount, int newSelectedRoomId)
    {
        rooms.clear();
        rooms.addAll(newRooms);
        deviceCounts.clear();
        deviceCounts.putAll(newDeviceCounts);
        totalDeviceCount = newTotalDeviceCount;
        selectedRoomId = newSelectedRoomId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_room, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position)
    {
        // Вся работа вынесена в отдельный метод: обработчики нажатий создаются там
        // и получают готовую комнату, а не позицию из onBindViewHolder.
        bindChip(holder, position);
    }

    private void bindChip(RoomViewHolder holder, int chipPosition)
    {
        Context context = holder.itemView.getContext();

        if (chipPosition == 0)
        {
            String allName = context.getString(R.string.haven_all_devices);
            String label = context.getString(R.string.haven_room_chip, allName, totalDeviceCount);
            boolean selected = (selectedRoomId == ALL_DEVICES_ID);
            showChip(holder.textRoomName, label, selected);

            holder.itemView.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    listener.onAllDevicesClicked();
                }
            });
            // Для «Все устройства» меню нет; сбрасываем обработчик от прежней комнаты.
            holder.itemView.setOnLongClickListener(null);
        }
        else if (chipPosition <= rooms.size())
        {
            final Room room = rooms.get(chipPosition - 1);

            int count = 0;
            Integer storedCount = deviceCounts.get(room.getId());
            if (storedCount != null)
            {
                count = storedCount.intValue();
            }

            String label = context.getString(R.string.haven_room_chip, room.getName(), count);
            boolean selected = (selectedRoomId == room.getId());
            showChip(holder.textRoomName, label, selected);

            holder.itemView.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    listener.onRoomClicked(room);
                }
            });
            holder.itemView.setOnLongClickListener(new View.OnLongClickListener()
            {
                @Override
                public boolean onLongClick(View view)
                {
                    listener.onRoomLongClicked(room, view);
                    return true;
                }
            });
        }
        else
        {
            holder.textRoomName.setText(R.string.haven_new_room);
            holder.textRoomName.setBackgroundResource(R.drawable.bg_chip_add);
            holder.textRoomName.setTextColor(
                    ContextCompat.getColor(context, R.color.haven_primary));

            holder.itemView.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    listener.onAddRoomClicked();
                }
            });
            holder.itemView.setOnLongClickListener(null);
        }
    }

    @Override
    public int getItemCount()
    {
        // «Все устройства» + комнаты + «Новая комната»
        return rooms.size() + 2;
    }

    private void showChip(TextView textView, String label, boolean selected)
    {
        Context context = textView.getContext();
        textView.setText(label);

        if (selected)
        {
            textView.setBackgroundResource(R.drawable.bg_chip_selected);
            textView.setTextColor(ContextCompat.getColor(context, R.color.haven_card));
        }
        else
        {
            textView.setBackgroundResource(R.drawable.bg_chip);
            textView.setTextColor(ContextCompat.getColor(context, R.color.haven_muted));
        }
    }

    public static class RoomViewHolder extends RecyclerView.ViewHolder
    {
        final TextView textRoomName;

        RoomViewHolder(@NonNull View itemView)
        {
            super(itemView);
            textRoomName = itemView.findViewById(R.id.textRoomName);
        }
    }
}
