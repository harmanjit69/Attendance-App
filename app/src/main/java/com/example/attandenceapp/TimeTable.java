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
    private Button selectImageButton;
    private Button deleteImageButton;

    private SharedPreferences preferences;

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    private static final String IMAGE_FILE_NAME = "timetable_image.jpg";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_time_table);

        ThemeManager.applyTheme(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        imageViewButton = findViewById(R.id.imageViewButton);
        selectImageButton = findViewById(R.id.selectImageButton);
        deleteImageButton = findViewById(R.id.deleteImageButton);

        preferences = getSharedPreferences("timetable_data", MODE_PRIVATE);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {

                        Uri imageUri = result.getData().getData();

                        if (imageUri != null) {
                            boolean saved = copyUriToInternalStorage(imageUri);

                            if (saved) {
                                loadSavedImage();
                                Toast.makeText(
                                        TimeTable.this,
                                        "Timetable saved inside app storage",
                                        Toast.LENGTH_SHORT
                                ).show();
                            } else {
                                Toast.makeText(
                                        TimeTable.this,
                                        "Could not save timetable image",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        }
                    }
                }
        );

        selectImageButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            imagePickerLauncher.launch(intent);
        });

        deleteImageButton.setOnClickListener(v -> {
            File file = getTimetableFile();

            if (file.exists()) {
                boolean deleted = file.delete();

                if (deleted) {
                    preferences.edit().remove("image_path").apply();
                    imageViewButton.setImageDrawable(null);
                    updateDeleteButtonState();

                    Toast.makeText(
                            this,
                            "Timetable deleted",
                            Toast.LENGTH_SHORT
                    ).show();
                } else {
                    Toast.makeText(
                            this,
                            "Could not delete timetable",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            } else {
                imageViewButton.setImageDrawable(null);
                updateDeleteButtonState();
            }
        });

        loadSavedImage();
        updateDeleteButtonState();
    }

    private File getTimetableFile() {
        return new File(getFilesDir(), IMAGE_FILE_NAME);
    }

    private boolean copyUriToInternalStorage(Uri uri) {
        File outFile = getTimetableFile();

        InputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            inputStream = getContentResolver().openInputStream(uri);

            if (inputStream == null) {
                return false;
            }

            outputStream = new FileOutputStream(outFile);

            byte[] buffer = new byte[8192];
            int read;

            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            outputStream.flush();

            preferences.edit()
                    .putString("image_path", outFile.getAbsolutePath())
                    .apply();

            return true;

        } catch (Exception e) {
            if (outFile.exists()) {
                outFile.delete();
            }

            return false;

        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
            }

            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void loadSavedImage() {
        File file = getTimetableFile();

        if (file.exists() && file.length() > 0) {
            imageViewButton.setImageURI(Uri.fromFile(file));
        } else {
            imageViewButton.setImageDrawable(null);
            preferences.edit().remove("image_path").apply();
        }
    }

    private void updateDeleteButtonState() {
        boolean hasImage = getTimetableFile().exists();

        deleteImageButton.setEnabled(hasImage);
        deleteImageButton.setAlpha(hasImage ? 1f : 0.5f);
    }
}