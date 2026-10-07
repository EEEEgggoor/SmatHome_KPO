package com.example.smarthome.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smarthome.R;
import com.example.smarthome.model.LightDevice;
import com.example.smarthome.presentation.DeviceValueFormatter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

/**
 * Управление светом: включение, яркость 0–100 и относительная температура света 0–100.
 * Значения в диалоге — черновик. Они применяются только по «Сохранить», «Отмена» ничего не меняет.
 * Диалог сам модель не меняет и оборудованию ничего не отправляет: он передаёт запрос слушателю.
 */
public class LightControlDialog
{
    public interface LightControlListener
    {
        /**
         * Вызывается при нажатии «Сохранить».
         * Верните null, если запрос принят, иначе текст ошибки для показа в диалоге.
         */
        String onLightApplyRequested(boolean poweredOn, int brightness, int lightTemperature);
    }

    private final Context context;
    private final LightDevice device;
    private final LightControlListener listener;
    private final DeviceValueFormatter formatter;

    private MaterialSwitch switchPower;
    private Slider sliderBrightness;
    private Slider sliderTemperature;
    private TextView textBrightnessValue;
    private TextView textTemperatureValue;
    private TextView textError;
    private AlertDialog dialog;

    public LightControlDialog(Context context, LightDevice device, LightControlListener listener)
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
        View contentView = LayoutInflater.from(context).inflate(R.layout.dialog_light_control, null);
        switchPower = contentView.findViewById(R.id.switchLightPower);
        sliderBrightness = contentView.findViewById(R.id.sliderBrightness);
        sliderTemperature = contentView.findViewById(R.id.sliderTemperature);
        textBrightnessValue = contentView.findViewById(R.id.textBrightnessValue);
        textTemperatureValue = contentView.findViewById(R.id.textTemperatureValue);
        textError = contentView.findViewById(R.id.textControlError);
        TextView textNotice = contentView.findViewById(R.id.textControlNotice);

        // Начальный черновик равен текущему состоянию устройства.
        switchPower.setChecked(device.isPoweredOn());
        sliderBrightness.setValue(device.getBrightness());
        sliderTemperature.setValue(device.getLightTemperature());
        showBrightnessValue(device.getBrightness());
        showTemperatureValue(device.getLightTemperature());

        sliderBrightness.addOnChangeListener(new Slider.OnChangeListener()
        {
            @Override
            public void onValueChange(Slider slider, float value, boolean fromUser)
            {
                showBrightnessValue((int) value);
            }
        });
        sliderTemperature.addOnChangeListener(new Slider.OnChangeListener()
        {
            @Override
            public void onValueChange(Slider slider, float value, boolean fromUser)
            {
                showTemperatureValue((int) value);
            }
        });

        // Без связи последнее состояние остаётся на экране, но изменить его нельзя.
        final boolean online = device.isOnline();
        if (!online)
        {
            textNotice.setVisibility(View.VISIBLE);
            switchPower.setEnabled(false);
            sliderBrightness.setEnabled(false);
            sliderTemperature.setEnabled(false);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(device.getName());
        builder.setView(contentView);
        // Обработчик «Сохранить» назначим после показа, иначе диалог закроется сам.
        builder.setPositiveButton(R.string.haven_save, null);
        builder.setNegativeButton(R.string.haven_cancel, null);
        dialog = builder.create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener()
        {
            @Override
            public void onShow(DialogInterface dialogInterface)
            {
                Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                saveButton.setEnabled(online);
                saveButton.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        applyDraft();
                    }
                });
            }
        });

        dialog.show();
    }

    private void applyDraft()
    {
        textError.setVisibility(View.GONE);

        boolean poweredOn = switchPower.isChecked();
        int brightness = (int) sliderBrightness.getValue();
        int lightTemperature = (int) sliderTemperature.getValue();

        String errorText = listener.onLightApplyRequested(poweredOn, brightness, lightTemperature);
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

    private void showBrightnessValue(int brightness)
    {
        textBrightnessValue.setText(formatter.formatBrightness(brightness));
    }

    private void showTemperatureValue(int lightTemperature)
    {
        textTemperatureValue.setText(formatter.formatLightTemperature(lightTemperature));
    }
}
