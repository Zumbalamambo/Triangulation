package uk.co.alt236.btlescan.activities;

import android.app.Notification;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import butterknife.Bind;
import butterknife.ButterKnife;
import uk.co.alt236.bluetoothlelib.device.BluetoothLeDevice;
import uk.co.alt236.bluetoothlelib.device.beacon.BeaconType;
import uk.co.alt236.bluetoothlelib.device.beacon.BeaconUtils;
import uk.co.alt236.bluetoothlelib.device.beacon.ibeacon.IBeaconDevice;
import uk.co.alt236.btlescan.R;
import uk.co.alt236.btlescan.adapters.LeDeviceListAdapter;
import uk.co.alt236.btlescan.containers.BluetoothLeDeviceStore;
import uk.co.alt236.btlescan.util.BluetoothLeScanner;
import uk.co.alt236.btlescan.util.BluetoothUtils;
import uk.co.alt236.btlescan.util.Calculation;
import uk.co.alt236.btlescan.util.DrawObj;
import uk.co.alt236.btlescan.util.TimeFormatter;
import uk.co.alt236.btlescan.util.WriteFileData;
import uk.co.alt236.easycursor.objectcursor.EasyObjectCursor;
import uk.co.alt236.btlescan.util.CountObj;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Bind(R.id.tvItemCount)
    protected TextView mTvItemCount;
    @Bind(android.R.id.list)
    protected ListView mList;
    @Bind(android.R.id.empty)
    protected View mEmpty;
    @Bind(R.id.tv_estimates_x)
    protected TextView mestimates_x;
    @Bind(R.id.tv_estimates_y)
    protected TextView mestimates_y;
    @Bind(R.id.tv_ed)
    protected TextView merrord;
    @Bind(R.id.etSetTime)
    protected EditText mSetTime;
    @Bind(R.id.ed_realx)
    protected EditText mrealx;
    @Bind(R.id.ed_realy)
    protected EditText mrealy;
    @Bind(R.id.bt_calculate)
    protected Button mcalculate;
    @Bind(R.id.receive_bt)
    protected ToggleButton receive;
    @Bind(R.id.Ifwrite)
    protected CheckBox Ifwrite;
    @Bind(R.id.loop)
    protected CheckBox loopChk;

    CountObj c1 = new CountObj(), c2 = new CountObj(), c3 = new CountObj();

    Calculation calculation;

    private int COUNT = 5;

    public WriteFileData mWriteFileData = new WriteFileData();
    public List<WriteFileData> wtdArray = new ArrayList<>();
    public List<DrawObj> BArray = new ArrayList<>();

    public static SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH-mm", Locale.TAIWAN);
    private final static String FILE_PATH = Environment.getExternalStorageDirectory().getPath() + "/positioning";
    public static String FILENAME = "/RSSI_" + sdf.format(System.currentTimeMillis());

    private NotificationManager manger;
    private Notification notification;

    public int MINOR = 0;
    private boolean running = false;

    private BluetoothUtils mBluetoothUtils;
    private BluetoothLeScanner mScanner;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothLeDeviceStore mDeviceStore;

    private Handler handler;
    private HandlerThread handlerThread;

    private final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {

            final BluetoothLeDevice deviceLe = new BluetoothLeDevice(device, rssi, scanRecord, System.currentTimeMillis());

            if (BeaconUtils.getBeaconType(deviceLe) == BeaconType.IBEACON) {
                final IBeaconDevice beacon = new IBeaconDevice(deviceLe);

                if (device.getName().equals("USBeacon")) {//beacon過濾
                    mDeviceStore.addDevice(deviceLe);
                    if (receive.isChecked()) {
                        mWriteFileData = new WriteFileData();
                        mWriteFileData.TimeStamp = TimeFormatter.getIsoDateTime(deviceLe.getTimestamp());
                        mWriteFileData.Minor = beacon.getMinor();
                        mWriteFileData.RSSI = deviceLe.getRssi();
                        wtdArray.add(mWriteFileData);
                        mWriteFileData = null;
                        MINOR = beacon.getMinor();
                    }
                }
            }

            final EasyObjectCursor<BluetoothLeDevice> c = mDeviceStore.getDeviceCursor();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeDeviceListAdapter.swapCursor(c);
                    updateItemCount(mLeDeviceListAdapter.getCount());
                }
            });
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME);
        toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
        mSetTime.setText(COUNT + "");
        mcalculate.setOnClickListener(this);
        receive.setOnClickListener(this);
        receive.setText(COUNT + "");

        manger = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notification = new Notification();
        notification.defaults = Notification.DEFAULT_SOUND;

        mList.setEmptyView(mEmpty);
        mDeviceStore = new BluetoothLeDeviceStore();
        mBluetoothUtils = new BluetoothUtils(this);
        mScanner = new BluetoothLeScanner(mLeScanCallback, mBluetoothUtils);
        updateItemCount(0);
        startScan();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanner.isScanning()) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_progress_indeterminate);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                startScan();
                break;
            case R.id.menu_stop:
                mScanner.scanLeDevice(-1, false);
                invalidateOptionsMenu();
                break;
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mScanner.scanLeDevice(-1, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        receive.setText(COUNT + "");
        invalidateOptionsMenu();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handlerThread != null) {
            handlerThread.quit();
        }
        if (handler != null) {
            handler.removeCallbacks(r1);
        }
    }

    private void writeFile() {
        if (wtdArray != null) {
            File dir = new File(FILE_PATH);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(FILE_PATH + FILENAME + " " + mSetTime.getText() + "s" + " " + MINOR + ".csv");

            if (!file.exists()) {
                try {
                    file.createNewFile();
                    Toast.makeText(this, file.toString(), Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                FileOutputStream fOut = new FileOutputStream(file);
                OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);

                myOutWriter.append("No.1, No.2, No.3\n");//minor編號
                myOutWriter.append(c1.getSize() + "," + c2.getSize() + "," + c3.getSize() + "\n");//蒐集訊號數
                myOutWriter.append(c1.getAvg() + "," + c2.getAvg() + "," + c3.getAvg() + "\n");//訊號平均
                myOutWriter.append(c1.getStandardD() + "," + c2.getStandardD() + "," + c3.getStandardD() + "\n");//訊號標準差
                myOutWriter.append(c1.getMax() + "," + c2.getMax() + "," + c3.getMax() + "\n");//訊號最大值
                myOutWriter.append(c1.getMin() + "," + c2.getMin() + "," + c3.getMin() + "\n");//訊號最小值

                myOutWriter.append("\n\nNo.1\n");
                for (int i = 0; i < c1.getSize(); i++) {
                    myOutWriter.append(c1.getindex(i) + ",");
                }
                myOutWriter.append("\nNo.2\n");
                for (int i = 0; i < c2.getSize(); i++) {
                    myOutWriter.append(c2.getindex(i) + ",");
                }
                myOutWriter.append("\nNo.3\n");
                for (int i = 0; i < c3.getSize(); i++) {
                    myOutWriter.append(c3.getindex(i) + ",");
                }

                myOutWriter.close();
                fOut.close();

                Toast.makeText(this, "write", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            Toast.makeText(getApplicationContext(), "NO USBeacon Signal", Toast.LENGTH_SHORT).show();
        }
    }

    private void cleanCountObj() {
        c1 = null;
        c2 = null;
        c3 = null;
    }

    private void separateBeacon() {
        c1 = new CountObj();
        c2 = new CountObj();
        c3 = new CountObj();

        for (int i = 0; i < wtdArray.size(); i++) {
            if (wtdArray.get(i).Minor == 1) {
                c1.inputArray(wtdArray.get(i).RSSI);
            }
            if (wtdArray.get(i).Minor == 2) {
                c2.inputArray(wtdArray.get(i).RSSI);
            }
            if (wtdArray.get(i).Minor == 3) {
                c3.inputArray(wtdArray.get(i).RSSI);
            }
        }
    }

    int temp = 0;
    private Runnable r1 = new Runnable() {
        @Override
        public void run() {
            if (temp == 0) {
                calculation = new Calculation();
            }
            if (temp < COUNT) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        receive.setText(temp + "");
                    }
                });
                temp++;
            } else {
                temp = 0;
                separateBeacon();//初始化
                positioning();//定位

                manger.notify(1, notification);
                cleanCountObj();

                wtdArray = null;
                wtdArray = new ArrayList<>();
            }
            handler.postDelayed(this, 1000);
        }
    };

    private Runnable r2 = new Runnable() {
        @Override
        public void run() {
            if (COUNT > 1) {
                COUNT--;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        receive.setText(COUNT + "");
                    }
                });
            } else {
                separateBeacon();//初始化
                positioning();//定位

                if (Ifwrite.isChecked()) {//是否寫檔
                    writeFile();
                }

                running = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        receive.setChecked(false);
                        receive.setText(mSetTime.getText().toString());
                    }
                });
                manger.notify(1, notification);
                cleanCountObj();
                handler.sendEmptyMessage(2);
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_calculate:
                if (!mrealx.getText().toString().equals("") && !mrealy.getText().toString().equals("") && calculation != null) {
                    double tmp = Math.sqrt(Math.pow(Double.parseDouble(mrealx.getText().toString()) - calculation.getX(), 2) + Math.pow(Double.parseDouble(mrealy.getText().toString()) - calculation.getY(), 2));
                    if (tmp == 0) {
                        merrord.setText(0.1 + "");
                    } else {
                        merrord.setText(tmp + "");
                    }
                } else if (mrealx.getText().toString().equals("") && !mrealy.getText().toString().equals("")) {
                    Toast.makeText(getApplicationContext(), "Enter REAL position!", Toast.LENGTH_SHORT).show();
                } else if (calculation == null) {
                    Toast.makeText(getApplicationContext(), "Position First!", Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.receive_bt:
                if (receive.isChecked()) {
                    wtdArray = null;
                    wtdArray = new ArrayList<>();
                    calculation = new Calculation();

                    if (!running) {
                        running = true;

                        handlerThread = new HandlerThread("positionworker");
                        handlerThread.start();
                        handler = new Handler(handlerThread.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                super.handleMessage(msg);
                                switch (msg.what) {
                                    case 1:
                                        handler.removeCallbacks(r1);
                                        break;
                                    case 2:
                                        handler.removeCallbacks(r2);
                                        break;
                                }
                            }
                        };

                        COUNT = Integer.parseInt(mSetTime.getText().toString());

                        if (loopChk.isChecked()) {//持續定位
                            handler.post(r1);
                        } else {//一次定位
                            receive.setText(COUNT + "");
                            handler.postDelayed(r2, 1000);
                        }
                    }
                } else {
                    if (loopChk.isChecked()) {
                        handler.sendEmptyMessage(1);
                    }
                    handlerThread.quit();
                    receive.setChecked(false);
                    running = false;
                    temp = 0;
                }
                break;
        }
    }

    private void positioning() {
        double[] calbeacon = {c1.getAvg(), c2.getAvg(), c3.getAvg()};
        calculation.CustomSubspceFingerPrint(calbeacon); //整個場域的fingerprint加選子區域

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mestimates_x.setText(calculation.getX() + "");
                mestimates_y.setText(calculation.getY() + "");
            }
        });
    }

    private void startScan() {
        final boolean mIsBluetoothOn = mBluetoothUtils.isBluetoothOn();
        final boolean mIsBluetoothLePresent = mBluetoothUtils.isBluetoothLeSupported();
        mDeviceStore.clear();
        updateItemCount(0);

        FILENAME = "/RSSI_" + sdf.format(System.currentTimeMillis());
        Log.v("p", FILENAME);

        mLeDeviceListAdapter = new LeDeviceListAdapter(this, mDeviceStore.getDeviceCursor());
        mList.setAdapter(mLeDeviceListAdapter);

        mBluetoothUtils.askUserToEnableBluetoothIfNeeded();
        if (mIsBluetoothOn && mIsBluetoothLePresent) {
            mScanner.scanLeDevice(-1, true);
            invalidateOptionsMenu();
        }
    }

    private void updateItemCount(final int count) {
        mTvItemCount.setText(
                getString(
                        R.string.formatter_item_count,
                        String.valueOf(count)));
    }

}
