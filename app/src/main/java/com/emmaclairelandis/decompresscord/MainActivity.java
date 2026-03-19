package com.emmaclairelandis.decompresscord;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private Uri selectedImageUri = null;
    private String selectedFileName = null;

    private TextView txtStatus;

    // SAF launcher to pick save location
    private ActivityResultLauncher<Intent> createFileLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Button btnSelect = findViewById(R.id.btnSelectImage);
        Button btnConvert = findViewById(R.id.btnConvert);
        txtStatus = findViewById(R.id.txtStatus);

        // File picker for selecting JPG
        ActivityResultLauncher<String> filePicker =
                registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        selectedFileName = getFileName(uri);
                        txtStatus.setText("Selected: " + selectedFileName);
                    }
                });

        btnSelect.setOnClickListener(v -> filePicker.launch("image/jpeg"));

        // SAF launcher for creating file
        createFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri saveUri = result.getData().getData();
                        if (saveUri != null) {
                            convertAndSaveToUri(selectedImageUri, saveUri);
                        }
                    } else {
                        txtStatus.setText("Save cancelled");
                    }
                }
        );

        btnConvert.setOnClickListener(v -> {
            if (selectedImageUri == null) {
                Toast.makeText(this, "Select an image first", Toast.LENGTH_SHORT).show();
                return;
            }

            // Suggest file name with _decompresscord
            String name = selectedFileName;
            int dot = name.lastIndexOf('.');
            String base = (dot >= 0) ? name.substring(0, dot) : name;
            String extension = "png";
            String suggestedName = base + "_decompresscord." + extension;

            // Launch create file dialog
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
            createFileLauncher.launch(intent);
        });
    }

    // Convert bitmap and save to SAF URI
    private void convertAndSaveToUri(Uri inputUri, Uri outputUri) {
        txtStatus.setText("Converting...");

        new Thread(() -> {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), inputUri);
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();

                int attempts = 0;

                while (true) {
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, baos);
                    byte[] data = baos.toByteArray();

                    if (data.length <= 10 * 1024 * 1024) {
                        try (OutputStream out = getContentResolver().openOutputStream(outputUri)) {
                            if (out != null) {
                                out.write(data);
                                out.flush();
                            }
                        }
                        runOnUiThread(() ->
                                txtStatus.setText("Saved: " + outputUri.toString())
                        );
                        return;
                    }

                    // scale down
                    width *= 0.9;
                    height *= 0.9;

                    attempts++;
                    if (attempts > 20) {
                        throw new Exception("Could not reduce image below 10MB");
                    }
                }

            } catch (Exception e) {
                runOnUiThread(() ->
                        txtStatus.setText("Error: " + e.getMessage())
                );
            }
        }).start();
    }

    private String getFileName(Uri uri) {
        String result = "unknown.jpg";
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        return result;
    }
}