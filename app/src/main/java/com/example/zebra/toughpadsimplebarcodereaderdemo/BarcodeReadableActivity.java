package com.example.zebra.toughpadsimplebarcodereaderdemo;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.TimeoutException;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.panasonic.toughpad.android.api.ToughpadApi;
import com.panasonic.toughpad.android.api.ToughpadApiListener;
import com.panasonic.toughpad.android.api.barcode.BarcodeData;
import com.panasonic.toughpad.android.api.barcode.BarcodeException;
import com.panasonic.toughpad.android.api.barcode.BarcodeListener;
import com.panasonic.toughpad.android.api.barcode.BarcodeReader;
import com.panasonic.toughpad.android.api.barcode.BarcodeReaderManager;

/**
 * BarcodeReadableActivity.
 * Extend this Activity and override {@code onRead(...)} to get reading result.
 */
public abstract class BarcodeReadableActivity extends AppCompatActivity
    implements ToughpadApiListener, BarcodeListener {

  EnableReaderTask enableReaderTask;
  private List<BarcodeReader> readers;
  private BarcodeReader selectedReader;

  @Override
  protected void onResume() {
    super.onResume();

    enableReaderTask = new EnableReaderTask(new WeakReference<>(this));
    initializeBarcodeReader();
  }

  @Override
  protected void onPause() {
    super.onPause();

    if (enableReaderTask.getStatus() == AsyncTask.Status.RUNNING
        || enableReaderTask.getStatus() == AsyncTask.Status.PENDING) {
      enableReaderTask.cancel(true);
    } else if (enableReaderTask.getStatus() == AsyncTask.Status.FINISHED) {
      switchSoftwareTrigger(false);
    }

    ToughpadApi.destroy();
  }

  public void initializeBarcodeReader() {
    if (ToughpadApi.isAlreadyInitialized()) {
      return;
    }

    readers = null;
    selectedReader = null;

    try {
      ToughpadApi.initialize(this, this);
    } catch (RuntimeException ex) {
      if (BuildConfig.DEBUG) {
        printDebugLog(ex.getMessage());
      }
    }
  }

  @Override
  public void onApiConnected(int version) {
    readers = BarcodeReaderManager.getBarcodeReaders();
    // XXX:
    if (readers == null || readers.size() == 0) {
      return;
    }

    selectedReader = readers.get(0);
    enableReaderTask.execute(selectedReader);
  }

  @Override
  public void onApiDisconnected() {
    releaseBarcodeReader();
  }

  /**
   * Callback when barcode is scanned.
   *
   * @param bsObj  Refers to the BarcodeReader object to which the data belongs to.
   * @param result Data which was scanned returned in BarcodeData object.
   */
  @Override
  public final void onRead(BarcodeReader bsObj, final BarcodeData result) {
    if (BuildConfig.DEBUG) {
      printDebugLog(
          String.format(getString(R.string.read_barcode_logging_format),
              bsObj.getDeviceName(), result.getSymbology()));
      printDebugLog(
          String.format(getString(R.string.read_barcode_text_logging_format), result.getTextData()));
    }

    runOnUiThread(new Runnable() {
      public void run() {
        BarcodeReadableActivity.this.onRead(result);
      }
    });
  }

  /**
   * Overridable callback when barcode is scanned.
   *
   * @param barcodeData Scanned data.
   */
  public void onRead(BarcodeData barcodeData) {
  }

  /**
   * Fire a software-trigger.
   */
  public void switchSoftwareTrigger(boolean status) {
    try {
      selectedReader.pressSoftwareTrigger(status);
    } catch (IllegalStateException | BarcodeException ex) {
      printDebugLog(ex.getMessage());
    }
  }

  public void toggleHardwareTrigger(boolean status) {
    try {
      selectedReader.setHardwareTriggerEnabled(status);
    } catch (BarcodeException | IllegalStateException ex) {
      printDebugLog(ex.getMessage());
    }
  }

  private void releaseBarcodeReader() {
    try {
      selectedReader.disable();
      selectedReader.clearBarcodeListener();
      printDebugLog(String.format(
          getString(R.string.barcode_reader_available_device_name),
          selectedReader.getDeviceName()));
    } catch (BarcodeException ex) {
      printDebugLog(ex.getMessage());
    }
  }

  public void printDebugLog(String text) {
    if (BuildConfig.DEBUG) {
      Log.d(this.getClass().getSimpleName(), text);
    }
  }

  private static class EnableReaderTask extends AsyncTask<BarcodeReader, Void, Boolean> {
    private WeakReference<BarcodeReadableActivity> activityWeakReference;
    private ProgressDialog dialog;

    EnableReaderTask(WeakReference<BarcodeReadableActivity> activityWeakReference) {
      this.activityWeakReference = activityWeakReference;
    }

    private boolean isActivityExist() {
      return this.activityWeakReference.get() != null;
    }

    @Override
    protected void onPreExecute() {
      if (!isActivityExist()) {
        return;
      }

      if (dialog == null) {
        dialog = new ProgressDialog(activityWeakReference.get());
        dialog.setCancelable(false);
        dialog.setIndeterminate(true);
        dialog.setMessage(
            activityWeakReference.get().getString(R.string.title_barcode_reader_connecting));
      }
      dialog.show();
    }

    @Override
    protected Boolean doInBackground(BarcodeReader... params) {
      if (!isActivityExist()) {
        return false;
      }

      try {
        params[0].enable(3000);
        params[0].addBarcodeListener(activityWeakReference.get());
        return true;
      } catch (BarcodeException | TimeoutException ex) {
        activityWeakReference.get().printDebugLog(ex.getMessage());
        return false;
      }
    }

    @Override
    protected void onPostExecute(Boolean result) {
      if (!isActivityExist()) {
        return;
      }

      if (dialog != null && dialog.isShowing()) {
        dialog.dismiss();
      }

      if (result) {
        activityWeakReference.get().printDebugLog(
            String.format(activityWeakReference.get().getString(
                R.string.barcode_reader_available_device_name),
                activityWeakReference.get().selectedReader.getDeviceName()));
        activityWeakReference.get().toggleHardwareTrigger(true);
      }
    }

    @Override
    protected void onCancelled() {
      if (dialog != null && dialog.isShowing()) {
        dialog.dismiss();
      }
    }
  }
}
