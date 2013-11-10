Local Storage provides access to your local storage (SD Card) via the Android 4.4 [Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider.html).

The focus on this app is not on the included UI, but instead on the [LocalDocumentProvider](LocalStorage/src/main/java/com/ianhanniballake/localstorage/LocalStorageProvider.java), which provides read and write access to your SD card. In theory, this code can and should be integrated into a file explorer style app to replace the previous GET_CONTENT Intent used in lower API versions.

You can download the app from [Google Play](https://play.google.com/store/apps/details?id=com.ianhanniballake.localstorage).

![Picker](art/screenshot_phone_picker.png?raw=true)
![Picker 2](art/screenshot_phone_picker_2.png?raw=true)
![Phone UI](art/screenshot_phone_ui.png?raw=true)
