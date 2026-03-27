package com.wype.registerfirst;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private final RegisterFirstService registerFirstService = new RegisterFirstService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText userNameInput = findViewById(R.id.userNameInput);
        EditText deviceIdInput = findViewById(R.id.deviceIdInput);
        Button registerButton = findViewById(R.id.registerButton);
        TextView resultText = findViewById(R.id.resultText);

        registerButton.setOnClickListener(v -> {
            try {
                RegistrationResult result = registerFirstService.registerFirst(
                    userNameInput.getText().toString(),
                    deviceIdInput.getText().toString()
                );

                String message = getString(
                    R.string.result_template,
                    result.getStatus(),
                    result.getMessage(),
                    result.getRegistrationId(),
                    result.getUserName(),
                    result.getDeviceId()
                );
                resultText.setText(message);
            } catch (IllegalArgumentException ex) {
                String message = getString(
                    R.string.result_template,
                    getString(R.string.error_status),
                    ex.getMessage(),
                    getString(R.string.not_available),
                    getString(R.string.not_available),
                    getString(R.string.not_available)
                );
                resultText.setText(message);
            }
        });
    }
}
