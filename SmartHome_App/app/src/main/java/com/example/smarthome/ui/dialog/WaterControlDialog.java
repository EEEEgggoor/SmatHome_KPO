package com.example.smarthome.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smarthome.R;
import com.example.smarthome.model.WaterDevice;
import com.example.smarthome.presentation.DeviceValueFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Датчик воды: показания только для чтения и ручное управление клапаном.
 * Состояние клапана может быть неизвестно (null), поэтому вместо переключателя
 * две кнопки «Открыть» и «Закрыть»: так не создаётся впечатление подтверждённого положения.
 * Пока диалог открыт, время измерения обновляется раз в несколько секунд.
 */
public class WaterControlDialog
{
    public interface WaterControlListener
    {
        /**
         * Вызывается при нажатии «Открыть» или «Закрыть».
         * Верните null, если запрос принят, иначе текст ошибки для показа в диалоге.
         */
        String onValveRequested(boolean open);
    }

    private static final long REFRESH_PERIOD_MS = 5000L;

    private final Context context;
    private final WaterDevice device;
    private final WaterControlListener listener;
    private final DeviceValueFormatter formatter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new ReadingsRefresher();

    private LinearProgressIndicator progressWaterLevel;
    private TextView textWaterLevel;
    private TextView textWaterStatus;
    private TextView textWaterTemperature;
    private TextView textWaterUpdated;
    private TextView textValveState;
    private TextView textError;
    private AlertDialog dialog;

    public WaterControlDialog(Context context, WaterDevice device, WaterControlListener listener)
    {
        if (context == null || device == null || listener == null)
        {
            throw new IllegalArgumentException("Контекст, устройство и слушатель не должны быть null.");
        }
        this.context = context;
        this.device = device;
        this.listener = listener;
        this.formatter = new DeviceValueFormatter(context.getResources());
    }

    public void show()
    {
        View contentView = LayoutInflater.from(context).inflate(R.layout.dialog_water_control, null);
        progressWaterLevel = contentView.findViewById(R.id.progressWaterLevel);
        textWaterLevel = contentView.findViewById(R.id.textWaterLevel);
        textWaterStatus = contentView.findViewById(R.id.textWaterStatus);
        textWaterTemperature = contentView.findViewById(R.id.textWaterTemperature);
        textWaterUpdated = contentView.findViewById(R.id.textWaterUpdated);
        textValveState = contentView.findViewById(R.id.textValveState);
        textError = contentView.findViewById(R.id.textControlError);
        TextView textNotice = contentView.findViewById(R.id.textControlNotice);
        MaterialButton buttonValveOpen = contentView.findViewById(R.id.buttonValveOpen);
        MaterialButton buttonValveClose = contentView.findViewById(R.id.buttonValveClose);

        showReadings();

        // Без связи показания остаются последними известными, но клапаном управлять нельзя.
        if (!device.isOnline())
        {
            textNotice.setVisibility(View.VISIBLE);
            buttonValveOpen.setEnabled(false);
            buttonValveClose.setEnabled(false);
        }

        buttonValveOpen.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                requestValve(true);
            }
        });
        buttonValveClose.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                requestValve(false);
            }
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(device.getName());
        builder.setView(contentView);
        builder.setPositiveButton(R.string.haven_close, null);
        dialog = builder.create();

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
        {
            @Override
            public void onDismiss(DialogInterface dialogInterface)
            {
                handler.removeCallbacks(refresher);
            }
        });

        dialog.show();
        handler.postDelayed(refresher, REFRESH_PERIOD_MS);
    }

    private void requestValve(boolean open)
    {
        textError.setVisibility(View.GONE);

        String errorText = listener.onValveRequested(open);
        if (errorText == null)
        {
            dialog.dismiss();
        }
        else
        {
            textError.setText(errorText);
            textError.setVisibility(View.VISIBLE);
        }
    }

    // Читает показания из модели и выводит их. Отсутствие данных не превращается в 0.
    private void showReadings()
    {
        Integer waterLevel = device.getWaterLevel();
        textWaterLevel.setText(formatter.formatWaterLevel(waterLevel));
        if (waterLevel == null)
        {
            progressWaterLevel.setVisibility(View.GONE);
        }
        else
        {
            progressWaterLevel.setVisibility(View.VISIBLE);
            progressWaterLevel.setProgressCompat(waterLevel.intValue(), false);
        }

        textWaterStatus.setText(formatter.formatLevelStatus(device.getLevelStatus()));
        textWaterTemperature.setText(formatter.formatTemperature(device.getTemperature()));
        textWaterUpdated.setText(formatter.formatLastUpdate(device.getLastUpdate()));

        String valveText = formatter.formatValveState(device.getValveOpen());
        textValveState.setText(context.getString(R.string.haven_valve_state, valveText));
    }

    private class ReadingsRefresher implements Runnable
    {
        @Override
        public void run()
        {
            showReadings();
            handler.postDelayed(this, REFRESH_PERIOD_MS);
        }
    }
}
