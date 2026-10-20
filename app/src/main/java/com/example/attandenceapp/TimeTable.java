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

public class TimeTable extends AppCompatActivity {

    private ImageView imageViewButton;
    private Button selectImageButton;

    private SharedPreferences preferences;

    private ActivityResultLauncher<Intent> imagePickerlauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_time_table);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        imageViewButton = findViewById(R.id.imageViewButton);
        selectImageButton = findViewById(R.id.selectImageButton);
        preferences = getSharedPreferences("timetable_data", MODE_PRIVATE);

        imagePickerlauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri imageUri = result.getData().getData();
                if (imageUri != null){

                    try {
                        int takeFlags = result.getData().getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                        getContentResolver().takePersistableUriPermission(imageUri,
                                takeFlags);

                        imageViewButton.setImageURI(imageUri);

                        preferences.edit().putString("image_uri", imageUri.toString()).apply();

                        Toast.makeText(TimeTable.this, "Photo Changed", Toast.LENGTH_SHORT).show();


                    } catch
                    (Exception e){
                        Toast.makeText(TimeTable.this, "Error" + e.getMessage(), Toast.LENGTH_LONG).show();
                    }

                }
            }

        });
        selectImageButton.setOnClickListener(v -> {

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

            imagePickerlauncher.launch(intent);
        });
        loadSavedImage();


    }

    private void loadSavedImage(){
        String savedUri = preferences.getString("image_uri", null);

        if (savedUri != null){
            try {
                Uri imageUri = Uri.parse(savedUri);
                imageViewButton.setImageURI(imageUri);
            } catch (Exception e) {
                Toast.makeText(this, "Could not load saved photo", Toast.LENGTH_SHORT).show();
            }
        }
    }
}