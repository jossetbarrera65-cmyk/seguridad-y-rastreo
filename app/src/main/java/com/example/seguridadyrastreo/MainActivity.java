package com.example.seguridadyrastreo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity {

    private TextInputEditText etNumeroEmergencia;
    private Button btnActivar;
    private SharedPreferences prefs;

    private String[] permisosNecesarios = {
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CALL_LOG
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etNumeroEmergencia = findViewById(R.id.etNumeroEmergencia);
        btnActivar = findViewById(R.id.btnActivar);
        prefs = getSharedPreferences("PreferenciasSeguridad", Context.MODE_PRIVATE);

        // Novedad: Pedimos los permisos inmediatamente al abrir la pantalla
        if (!verificarPermisos()) {
            pedirPermisos();
        }

        String numeroGuardado = prefs.getString("numero_emergencia", "");
        if (!numeroGuardado.isEmpty()) {
            etNumeroEmergencia.setText(numeroGuardado);
        }

        btnActivar.setOnClickListener(v -> {
            // Si el usuario intentó guardar sin dar permisos, se los volvemos a pedir
            if (!verificarPermisos()) {
                pedirPermisos();
                Toast.makeText(this, "Se requieren los permisos para activar el escudo.", Toast.LENGTH_SHORT).show();
                return;
            }
            guardarNumeroYActivar();
        });
    }

    private boolean verificarPermisos() {
        for (String permiso : permisosNecesarios) {
            if (ContextCompat.checkSelfPermission(this, permiso) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void pedirPermisos() {
        ActivityCompat.requestPermissions(this, permisosNecesarios, 100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (!verificarPermisos()) {
                Toast.makeText(this, "Advertencia: La app no funcionará sin todos los permisos.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void guardarNumeroYActivar() {
        String numeroIngresado = etNumeroEmergencia.getText().toString().trim();

        // Validación 1: Que no esté vacío
        if (numeroIngresado.isEmpty()) {
            etNumeroEmergencia.setError("Por favor ingresa un número");
            return;
        }

        // Validación 2: Que tenga exactamente 10 dígitos
        if (numeroIngresado.length() < 10) {
            etNumeroEmergencia.setError("Te faltan números. Deben ser exactamente 10 dígitos.");
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("numero_emergencia", numeroIngresado);
        editor.apply();

        Toast.makeText(this, "¡Escudo Activado! Monitoreando en segundo plano.", Toast.LENGTH_SHORT).show();
    }
}