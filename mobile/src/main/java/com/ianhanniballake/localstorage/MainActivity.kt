package com.ianhanniballake.localstorage

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.ViewSwitcher
import androidx.core.database.getString
import androidx.core.view.isVisible

class MainActivity : AppCompatActivity() {
    companion object {
        private const val RC_PERMISSION_REQUEST = 1
        private const val RC_OPEN_DOCUMENT = 2
        private const val RC_OPEN_DOCUMENT_TREE = 3
        private const val LAST_RETURNED_DOCUMENT_URI = "LAST_RETURNED_DOCUMENT_URI"
        private const val LAST_RETURNED_DOCUMENT_TREE_URI = "LAST_RETURNED_DOCUMENT_TREE_URI"
    }

    private lateinit var description: TextView
    private lateinit var grantPermission: Button
    private lateinit var returnedName: TextView
    private lateinit var returnedImage: ImageView
    private lateinit var returnedDetailsSwitcher: ViewSwitcher
    private lateinit var returnedChildren: TextView

    private var lastReturnedDocumentUri: Uri? = null
    private var lastReturnedDocumentTreeUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        description = findViewById(R.id.description)
        grantPermission = findViewById(R.id.grant_permission)
        grantPermission.setOnClickListener {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this@MainActivity,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), RC_PERMISSION_REQUEST)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
        val openAll = findViewById<Button>(R.id.open_all_files)
        openAll.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, RC_OPEN_DOCUMENT)
        }
        val openImages = findViewById<Button>(R.id.open_images)
        openImages.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, RC_OPEN_DOCUMENT)
        }
        val createFile = findViewById<Button>(R.id.create_file)
        createFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                // Filter to only show results that can be "opened", such as
                // a file (as opposed to a list of contacts or timezones).
                addCategory(Intent.CATEGORY_OPENABLE)
                // Create a file with the requested MIME type.
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "test_file.txt")
            }
            startActivityForResult(intent, RC_OPEN_DOCUMENT)
        }
        val openDirectory = findViewById<Button>(R.id.open_directory)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            openDirectory.setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, RC_OPEN_DOCUMENT_TREE)
            }
        } else {
            openDirectory.isVisible = false
        }
        returnedName = findViewById(R.id.returned_name)
        returnedImage = findViewById(R.id.returned_image)
        returnedDetailsSwitcher = findViewById(R.id.returned_details_switcher)
        returnedChildren = findViewById(R.id.returned_children)
        val bottomBanner = findViewById<View>(R.id.bottom_banner)
        if (bottomBanner is Button) {
            bottomBanner.setOnClickListener {
                DonateDialogFragment().show(supportFragmentManager, "donate")
            }
        }
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    RC_PERMISSION_REQUEST)
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            description.setText(R.string.description)
            grantPermission.isVisible = false
        } else {
            description.setText(R.string.description_permission_rationale)
            grantPermission.isVisible = true
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Ensure we update the availability of our storage provider
            contentResolver.notifyChange(DocumentsContract.buildRootsUri(BuildConfig.DOCUMENTS_AUTHORITY), null)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val lastDocumentUri: Uri? = savedInstanceState.getParcelable(LAST_RETURNED_DOCUMENT_URI)
        val lastDocumentTreeUri: Uri? = savedInstanceState.getParcelable(LAST_RETURNED_DOCUMENT_TREE_URI)
        if (lastDocumentUri != null) {
            lastReturnedDocumentUri = lastDocumentUri
            handleOpenDocument(lastDocumentUri)
        } else if (lastDocumentTreeUri != null) {
            lastReturnedDocumentTreeUri = lastDocumentTreeUri
            handleOpenDocumentTree(lastDocumentTreeUri)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(LAST_RETURNED_DOCUMENT_URI, lastReturnedDocumentUri)
        outState.putParcelable(LAST_RETURNED_DOCUMENT_TREE_URI, lastReturnedDocumentTreeUri)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.menu_donate -> {
            DonateDialogFragment().show(supportFragmentManager, "donate")
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val url = data?.data
        if (resultCode != Activity.RESULT_OK || url == null) {
            returnedName.text = ""
            returnedImage.setImageURI(null)
            return
        }
        if (requestCode == RC_OPEN_DOCUMENT) {
            handleOpenDocument(url)
        } else if (requestCode == RC_OPEN_DOCUMENT_TREE) {
            handleOpenDocumentTree(url)
        }
    }

    private fun handleOpenDocument(documentUri: Uri) {
        returnedDetailsSwitcher.displayedChild = 0
        contentResolver.query(documentUri, null, null, null, null)?.use { cursor ->
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (cursor.moveToFirst()) {
                lastReturnedDocumentUri = documentUri
                lastReturnedDocumentTreeUri = null
                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                val displayName = cursor.getString(OpenableColumns.DISPLAY_NAME)
                returnedName.text = displayName
                returnedImage.setImageURI(documentUri)
                returnedImage.contentDescription = displayName
            } else {
                returnedName.text = ""
                returnedImage.setImageURI(null)
                returnedImage.contentDescription = ""
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun handleOpenDocumentTree(treeUri: Uri) {
        val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocumentUri = DocumentsContract.buildDocumentUri(BuildConfig.DOCUMENTS_AUTHORITY, parentDocumentId)
        val parentDisplayName = contentResolver.query(parentDocumentUri, null, null, null, null)?.use { cursor ->
            when {
                // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
                // "if there's anything to look at, look at it" conditionals.
                cursor.moveToFirst() -> {
                    lastReturnedDocumentUri = null
                    lastReturnedDocumentTreeUri = treeUri
                    // Note it's called "Display Name".  This is
                    // provider-specific, and might not necessarily be the file name.
                    cursor.getString(OpenableColumns.DISPLAY_NAME)
                }
                else -> null
            }
        } ?: parentDocumentId

        returnedDetailsSwitcher.displayedChild = 1

        // Now iterate through the tree URI's children
        val documentUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        contentResolver.query(documentUri, null, null, null, null).use { cursor ->
            if (cursor == null) {
                returnedName.text = ""
                returnedChildren.text = ""
                return
            }
            returnedName.text = getString(R.string.parent_document_title, cursor.count, parentDisplayName)
            val children = StringBuilder()
            while (cursor.moveToNext()) {
                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                val displayName = cursor.getString(OpenableColumns.DISPLAY_NAME)
                children.append(displayName)
                if (!cursor.isLast) {
                    children.append('\n')
                }
            }
            returnedChildren.text = children
        }
    }
}
