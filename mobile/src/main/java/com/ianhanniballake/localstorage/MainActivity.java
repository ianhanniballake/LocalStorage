package com.ianhanniballake.localstorage;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

public class MainActivity extends AppCompatActivity {
    private static final int RC_OPEN_DOCUMENT = 1;
    private static final int RC_OPEN_DOCUMENT_TREE = 2;
    private static final String LAST_RETURNED_DOCUMENT_URI = "LAST_RETURNED_DOCUMENT_URI";
    private static final String LAST_RETURNED_DOCUMENT_TREE_URI = "LAST_RETURNED_DOCUMENT_TREE_URI";
    private TextView mReturnedName;
    private ImageView mReturnedImage;
    private ViewSwitcher mReturnedDetailsSwitcher;
    private TextView mReturnedChildren;
    private Uri mLastReturnedDocumentUri;
    private Uri mLastReturnedDocumentTreeUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button openAll = (Button) findViewById(R.id.open_all_files);
        openAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, RC_OPEN_DOCUMENT);
            }
        });
        Button openImages = (Button) findViewById(R.id.open_images);
        openImages.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, RC_OPEN_DOCUMENT);
            }
        });
        Button createFile = (Button) findViewById(R.id.create_file);
        createFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                // Filter to only show results that can be "opened", such as
                // a file (as opposed to a list of contacts or timezones).
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                // Create a file with the requested MIME type.
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, "test_file.txt");
                startActivityForResult(intent, RC_OPEN_DOCUMENT);
            }
        });
        Button openDirectory = (Button) findViewById(R.id.open_directory);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            openDirectory.setOnClickListener(new View.OnClickListener() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onClick(final View v) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    startActivityForResult(intent, RC_OPEN_DOCUMENT_TREE);
                }
            });
        } else {
            openDirectory.setVisibility(View.GONE);
        }
        mReturnedName = (TextView) findViewById(R.id.returned_name);
        mReturnedImage = (ImageView) findViewById(R.id.returned_image);
        mReturnedDetailsSwitcher = (ViewSwitcher) findViewById(R.id.returned_details_switcher);
        mReturnedChildren = (TextView) findViewById(R.id.returned_children);
        View bottomBanner = findViewById(R.id.bottom_banner);
        if (bottomBanner instanceof Button) {
            bottomBanner.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    startActivity(new Intent(MainActivity.this, DonateActivity.class));
                }
            });
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mLastReturnedDocumentUri = savedInstanceState.getParcelable(LAST_RETURNED_DOCUMENT_URI);
        mLastReturnedDocumentTreeUri = savedInstanceState.getParcelable(LAST_RETURNED_DOCUMENT_TREE_URI);
        if (mLastReturnedDocumentUri != null) {
            handleOpenDocument(mLastReturnedDocumentUri);
        } else if (mLastReturnedDocumentTreeUri != null) {
            handleOpenDocumentTree(mLastReturnedDocumentTreeUri);
        }
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(LAST_RETURNED_DOCUMENT_URI, mLastReturnedDocumentUri);
        outState.putParcelable(LAST_RETURNED_DOCUMENT_TREE_URI, mLastReturnedDocumentTreeUri);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_donate:
                startActivity(new Intent(this, DonateActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            mReturnedName.setText("");
            mReturnedImage.setImageURI(null);
            return;
        }
        if (requestCode == RC_OPEN_DOCUMENT) {
            handleOpenDocument(data.getData());
        } else if (requestCode == RC_OPEN_DOCUMENT_TREE) {
            handleOpenDocumentTree(data.getData());
        }
    }

    private void handleOpenDocument(Uri documentUri) {
        mReturnedDetailsSwitcher.setDisplayedChild(0);
        Cursor cursor = getContentResolver().query(documentUri, null, null, null, null);
        try {
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (cursor != null && cursor.moveToFirst()) {
                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                String displayName = cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                mReturnedName.setText(displayName);
                mReturnedImage.setImageURI(documentUri);
                mReturnedImage.setContentDescription(displayName);
                mLastReturnedDocumentUri = documentUri;
                mLastReturnedDocumentTreeUri = null;
            } else {
                mReturnedName.setText("");
                mReturnedImage.setImageURI(null);
                mReturnedImage.setContentDescription("");
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void handleOpenDocumentTree(Uri treeUri) {
        String parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri parentDocumentUri = DocumentsContract.buildDocumentUri(LocalStorageProvider.AUTHORITY, parentDocumentId);
        String parentDisplayName = parentDocumentId;
        Cursor cursor = getContentResolver().query(parentDocumentUri, null, null, null, null);
        try {
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (cursor != null && cursor.moveToFirst()) {
                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                parentDisplayName = cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                mLastReturnedDocumentUri = null;
                mLastReturnedDocumentTreeUri = treeUri;
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        mReturnedDetailsSwitcher.setDisplayedChild(1);
        Uri documentUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId);
        cursor = getContentResolver().query(documentUri, null, null, null, null);
        if (cursor == null) {
            mReturnedName.setText("");
            mReturnedChildren.setText("");
            return;
        }
        mReturnedName.setText(getString(R.string.parent_document_title, cursor.getCount(), parentDisplayName));
        StringBuilder children = new StringBuilder();
        while (cursor.moveToNext()) {
            // Note it's called "Display Name".  This is
            // provider-specific, and might not necessarily be the file name.
            String displayName = cursor.getString(
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            children.append(displayName);
            if (!cursor.isLast()) {
                children.append('\n');
            }
        }
        mReturnedChildren.setText(children);
        cursor.close();
    }
}
