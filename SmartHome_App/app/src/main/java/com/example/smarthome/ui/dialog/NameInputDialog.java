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

import java.util.Objects;

/**
 * Общий диалог ввода названия: новая комната, переименование комнаты и устройства.
 * Сам название не проверяет и не сохраняет: он передаёт введённый текст слушателю.
 * Если слушатель вернул текст ошибки, диалог остаётся открытым и показывает её.
 */
public class NameInputDialog
{
    public interface NameListener
    {
        /**
         * Вызывается при нажатии на кнопку подтверждения.
         * Верните null, если название принято, иначе текст ошибки для показа в диалоге.
         */
        String onNameEntered(String name);
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
        Objects.requireNonNull(dialog.getWindow()).setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        dialog.setOnShowListener(new DialogInterface.OnShowListener()
        {
            @Override
            public void onShow(DialogInterface dialogInterface)
            {
                Button confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
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

                        String errorText = listener.onNameEntered(enteredName);
                        if (errorText == null)
                        {
                            dialog.dismiss();
                        }
                        else
                        {
                            inputLayout.setError(errorText);
                        }
                    }
                });
                editName.requestFocus();
            }
        });

        dialog.show();
    }
}
