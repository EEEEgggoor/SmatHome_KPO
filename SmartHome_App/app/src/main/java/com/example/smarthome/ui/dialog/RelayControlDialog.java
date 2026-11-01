package com.example.smarthome.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.smarthome.R;
import com.example.smarthome.model.RelayDevice;
import com.example.smarthome.presentation.DeviceValueFormatter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Управление реле: включено или выключено.
 * Состояние контакта не показываем: схема подключения реле пока не согласована.
 * Переключатель — черновик, применяется по «Сохранить». После нажатия показывается ожидание,
 * диалог закрывается только по подтверждению. Модель диалог не меняет.
 */
public class RelayControlDialog
{
    public interface RelayControlListener
    {
        /**
         * Вызывается при нажатии «Сохранить». Результат нужно сообщить через result.
         */
        void onRelayApplyRequested(boolean poweredOn, ControlResultCallback result);
    }

    private final Context context;
    private final RelayDevice device;
    private final RelayControlListener listener;
    private final DeviceValueFormatter formatter;

    private MaterialSwitch switchPower;
    private TextView textError;
    private LinearProgressIndicator progressControl;
    private Button saveButton;
    private AlertDialog dialog;
    private boolean online;
    private boolean closed;

    public RelayControlDialog(Context context, RelayDevice device, RelayControlListener listener)
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
        View contentView = LayoutInflater.from(context).inflate(R.layout.dialog_relay_control, null);
        switchPower = contentView.findViewById(R.id.switchRelayPower);
        textError = contentView.findViewById(R.id.textControlError);
        progressControl = contentView.findViewById(R.id.progressControl);
        TextView textCurrentState = contentView.findViewById(R.id.textRelayCurrentState);
        TextView textNotice = contentView.findViewById(R.id.textControlNotice);

        // «Сейчас» — сохранённое состояние устройства, переключатель — черновик.
        switchPower.setChecked(device.isPoweredOn());
        String powerText = formatter.formatPowerState(device.isPoweredOn());
        textCurrentState.setText(context.getString(R.string.haven_relay_current_state, powerText));

        online = device.isOnline();
        if (!online)
        {
            textNotice.setVisibility(View.VISIBLE);
            switchPower.setEnabled(false);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(device.getName());
        builder.setView(contentView);
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

        listener.onRelayApplyRequested(switchPower.isChecked(), new ControlResultCallback()
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
        saveButton.setEnabled(editable);
    }
}
