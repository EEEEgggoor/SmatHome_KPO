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
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;

/**
 * Управление светом: включение и яркость 0–100.
 * Значения в диалоге — черновик, применяются по «Сохранить».
 * После «Сохранить» показывается ожидание, повторное применение заблокировано.
 * Диалог закрывается только когда слушатель сообщил об успехе через ControlResultCallback.
 * Модель диалог не меняет и оборудованию ничего не отправляет сам.
 */
public class LightControlDialog
{
    public interface LightControlListener
    {
        /**
         * Вызывается при нажатии «Сохранить». Результат нужно сообщить через result.
         */
        void onLightApplyRequested(boolean poweredOn, int brightness, ControlResultCallback result);
    }

    private final Context context;
    private final LightDevice device;
    private final LightControlListener listener;
    private final DeviceValueFormatter formatter;

    private MaterialSwitch switchPower;
    private Slider sliderBrightness;
    private TextView textBrightnessValue;
    private TextView textError;
    private LinearProgressIndicator progressControl;
    private Button saveButton;
    private AlertDialog dialog;
    private boolean online;
    private boolean closed;

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
        textBrightnessValue = contentView.findViewById(R.id.textBrightnessValue);
        textError = contentView.findViewById(R.id.textControlError);
        progressControl = contentView.findViewById(R.id.progressControl);
        TextView textNotice = contentView.findViewById(R.id.textControlNotice);

        // Начальный черновик равен текущему состоянию устройства.
        switchPower.setChecked(device.isPoweredOn());
        sliderBrightness.setValue(device.getBrightness());
        showBrightnessValue(device.getBrightness());

        sliderBrightness.addOnChangeListener(new Slider.OnChangeListener()
        {
            @Override
            public void onValueChange(Slider slider, float value, boolean fromUser)
            {
                showBrightnessValue((int) value);
            }
        });

        // Без связи последнее состояние остаётся на экране, но изменить его нельзя.
        online = device.isOnline();
        if (!online)
        {
            textNotice.setVisibility(View.VISIBLE);
            switchPower.setEnabled(false);
            sliderBrightness.setEnabled(false);
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
                saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
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

        // Результат команды, пришедший после закрытия диалога, игнорируем.
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
        {
            @Override
            public void onDismiss(DialogInterface dialogInterface)
            {
                closed = true;
            }
        });

        dialog.show();
    }

    private void applyDraft()
    {
        textError.setVisibility(View.GONE);
        setPending(true);

        boolean poweredOn = switchPower.isChecked();
        int brightness = (int) sliderBrightness.getValue();

        listener.onLightApplyRequested(poweredOn, brightness, new ControlResultCallback()
        {
            @Override
            public void onSuccess()
            {
                if (closed)
                {
                    return;
                }
                dialog.dismiss();
            }

            @Override
            public void onFailure(String message)
            {
                if (closed)
                {
                    return;
                }
                setPending(false);
                textError.setText(message);
                textError.setVisibility(View.VISIBLE);
            }
        });
    }

    // Пока команда выполняется, элементы управления и «Сохранить» недоступны.
    private void setPending(boolean pending)
    {
        if (pending)
        {
            progressControl.setVisibility(View.VISIBLE);
        }
        else
        {
            progressControl.setVisibility(View.GONE);
        }

        boolean editable = online && !pending;
        switchPower.setEnabled(editable);
        sliderBrightness.setEnabled(editable);
        saveButton.setEnabled(editable);
    }

    private void showBrightnessValue(int brightness)
    {
        textBrightnessValue.setText(formatter.formatBrightness(brightness));
    }
}
