package com.example.smarthome.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;

import com.example.smarthome.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Общий диалог ввода названия: новая комната, переименование комнаты и устройства.
 * Сам название не проверяет и не сохраняет: он передаёт введённый текст слушателю.
 * Пока слушатель не сообщил результат, кнопка и поле недоступны.
 * При успехе диалог закрывается, при ошибке остаётся открытым и показывает её текст.
 */
public class NameInputDialog
{
    public interface NameListener
    {
        /**
         * Вызывается при нажатии на кнопку подтверждения. Результат нужно сообщить через result.
         */
        void onNameEntered(String name, ControlResultCallback result);
    }

    /**
     * @param maxLength ограничение длины; 0 или меньше — без ограничения
     */
    public static void show(Context context, int titleResId, int hintResId, String initialName,
                            int maxLength, int positiveButtonResId, final NameListener listener)
    {
        if (context == null || listener == null)
        {
            throw new IllegalArgumentException("Контекст и слушатель не должны быть null.");
        }

        View contentView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_name_input, null);
        final TextInputLayout inputLayout = contentView.findViewById(R.id.inputLayoutName);
        final TextInputEditText editName = contentView.findViewById(R.id.editName);

        inputLayout.setHint(hintResId);

        if (maxLength > 0)
        {
            InputFilter[] filters = new InputFilter[1];
            filters[0] = new InputFilter.LengthFilter(maxLength);
            editName.setFilters(filters);
            inputLayout.setCounterEnabled(true);
            inputLayout.setCounterMaxLength(maxLength);
        }

        editName.setText(initialName);
        editName.setSelection(editName.length());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(titleResId);
        builder.setView(contentView);
        // Обработчик кнопки подтверждения назначим после показа, иначе диалог закроется сам.
        builder.setPositiveButton(positiveButtonResId, null);
        builder.setNegativeButton(R.string.haven_cancel, null);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        // Результат, пришедший после закрытия диалога, игнорируем.
        final boolean[] closed = new boolean[1];
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
        {
            @Override
            public void onDismiss(DialogInterface dialogInterface)
            {
                closed[0] = true;
            }
        });

        dialog.setOnShowListener(new DialogInterface.OnShowListener()
        {
            @Override
            public void onShow(DialogInterface dialogInterface)
            {
                final Button confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                confirmButton.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        inputLayout.setError(null);

                        String enteredName = "";
                        if (editName.getText() != null)
                        {
                            enteredName = editName.getText().toString();
                        }

                        // Пока идёт сохранение, повторное нажатие и правка названия недоступны.
                        confirmButton.setEnabled(false);
                        editName.setEnabled(false);

                        listener.onNameEntered(enteredName, new ControlResultCallback()
                        {
                            @Override
                            public void onSuccess()
                            {
                                if (closed[0])
                                {
                                    return;
                                }
                                dialog.dismiss();
                            }

                            @Override
                            public void onFailure(String message)
                            {
                                if (closed[0])
                                {
                                    return;
                                }
                                confirmButton.setEnabled(true);
                                editName.setEnabled(true);
                                inputLayout.setError(message);
                            }
                        });
                    }
                });
                editName.requestFocus();
            }
        });

        dialog.show();
    }
}
