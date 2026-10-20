package com.example.attandenceapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class TimeTable extends AppCompatActivity {
    private ImageView imageViewButton;
    private Button selectImageButton, deleteImageButton;
    private SharedPreferences preferences;
    private ActivityResultLauncher<Intent> imagePickerlauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ThemeManager.applyTheme(this); // Feature 4
        setContentView(R.layout.activity_time_table);

        // ... (Keep your existing Insets code here) ...

        imageViewButton = findViewById(R.id.imageViewButton);
        selectImageButton = findViewById(R.id.selectImageButton);
        deleteImageButton = findViewById(R.id.deleteImageButton);
        preferences = getSharedPreferences("timetable_data", MODE_PRIVATE);

        imagePickerlauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri imageUri = result.getData().getData();
                if (imageUri != null) {
                    String internalPath = copyUriToInternalStorage(imageUri);
                    if (internalPath != null) {
                        imageViewButton.setImageURI(Uri.fromFile(new File(internalPath)));
                        preferences.edit().putString("image_path", internalPath).apply();
                        Toast.makeText(this, "Timetable Saved internally!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        selectImageButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            imagePickerlauncher.launch(intent);
        });

        deleteImageButton.setOnClickListener(v -> {
            String path = preferences.getString("image_path", "");
            File file = new File(path);
            if (file.exists()) file.delete();
            preferences.edit().remove("image_path").apply();
            imageViewButton.setImageDrawable(null); // Clears the image
            Toast.makeText(this, "Timetable Deleted", Toast.LENGTH_SHORT).show();
        });

        loadSavedImage();
    }

    private String copyUriToInternalStorage(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            File file = new File(getFilesDir(), "timetable_image.jpg");
            FileOutputStream out = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int read;
            while((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            in.close(); out.close();
            return file.getAbsolutePath();
        } catch (Exception e) { return null; }
    }

    private void loadSavedImage() {
        String savedPath = preferences.getString("image_path", null);
        if (savedPath != null) {
            File file = new File(savedPath);
            if (file.exists()) imageViewButton.setImageURI(Uri.fromFile(file));
        }
    }
}