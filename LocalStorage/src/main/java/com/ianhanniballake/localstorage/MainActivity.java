package com.ianhanniballake.localstorage;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int READ_REQUEST_CODE = 1;
    private TextView returnedName;
    private ImageView returnedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button openAll = (Button) findViewById(R.id.check_open_all);
        openAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, READ_REQUEST_CODE);
            }
        });
        Button openImages = (Button) findViewById(R.id.check_open_images);
        openImages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, READ_REQUEST_CODE);
            }
        });
        returnedName = (TextView) findViewById(R.id.returned_name);
        returnedImage = (ImageView) findViewById(R.id.returned_image);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != READ_REQUEST_CODE || resultCode != Activity.RESULT_OK || data == null || data.getData() ==
                null) {
            returnedName.setText("");
            returnedImage.setImageURI(null);
            return;
        }
        Cursor cursor = getContentResolver().query(data.getData(), null, null, null, null);
        try {
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (cursor != null && cursor.moveToFirst()) {
                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                String displayName = cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                returnedName.setText(displayName);
                returnedImage.setImageURI(data.getData());
            } else {
                returnedName.setText("");
                returnedImage.setImageURI(null);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }
}
