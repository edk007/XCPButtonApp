package com.edtest.xcpbuttonapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.samsung.android.knox.EnterpriseDeviceManager;
import com.samsung.android.knox.custom.CustomDeviceManager;
import com.samsung.android.knox.custom.SystemManager;
import com.samsung.android.knox.kpcc.KPCCManager;
import com.samsung.android.knox.restriction.RestrictionPolicy;

import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "XCP_BUTTON_APP";
    public static final String TAG2 = "MAIN_ACTIVITY: ";

    ArrayList<String> buttonActions;
    ListView listView;
    ArrayAdapter arrayAdapter;

    //KNOX
    private static final int DEVICE_ADMIN_ADD_RESULT_ENABLE = 1;
    private ComponentName mDeviceAdmin;
    private DevicePolicyManager mDPM;
    private CustomDeviceManager cdm;
    private SystemManager kcsm;

    //variables to setup timing for the long press
    Runnable longPressRunnable;
    private Handler longPressHandler;
    int long_press_time = 2000; //milliseconds - 5 seconds = 5,000 milliseconds

    //variables for the task executed with a long press
    ScheduledThreadPoolExecutor exec;
    Runnable repeatRunnable;
    ScheduledFuture repeatTimerTask;
    int repeat_interval = 1000;  //milliseconds
    int repeatCounter = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.listView);

        buttonActions = new ArrayList<>();
        buttonActions.add("STARTING");

        arrayAdapter = new ArrayAdapter(this, R.layout.row_layout, R.id.label, buttonActions);

        listView.setAdapter(arrayAdapter);

        longPressHandler = new Handler(Looper.getMainLooper());

        //SAMSUNG KNOX
        if (Build.BRAND.equals("samsung")) {
            mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            mDeviceAdmin = new ComponentName(MainActivity.this, AdminReceiver.class);
        }

        exec = new ScheduledThreadPoolExecutor(1);

        longPressRunnable = new Runnable() {
            @Override
            public void run() {
                //should start something after the scan interval
                buttonActions.add("LONG_PRESS_RUNNABLE");
                arrayAdapter.notifyDataSetChanged();
                repeatTimerTask = exec.scheduleAtFixedRate(repeatRunnable,0,repeat_interval, TimeUnit.MILLISECONDS);

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        arrayAdapter.notifyDataSetChanged();
                    }
                });
            }
        };

        repeatRunnable = new Runnable() {
            @Override
            public void run() {
                buttonActions.add("REPEAT_COUNTER:" + repeatCounter);
                repeatCounter++;

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        arrayAdapter.notifyDataSetChanged();
                    }
                });
            }
        };

    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        boolean knox = true;
        boolean da = true;
        if (Build.BRAND.equals("samsung")) {
            knox = checkKnox();
            da = mDPM.isAdminActive(mDeviceAdmin);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || !da || !knox) {
            //if we don't have permission - need to get it granted
            Intent intent = new Intent(getApplicationContext(), PermissionsActivity.class);
            startActivity(intent);
        } else {
            //setup everything
            cdm = CustomDeviceManager.getInstance();
            String permission = "com.samsung.android.knox.permission.KNOX_CUSTOM_SYSTEM";
            if(cdm.checkEnterprisePermission(permission)) {
                Log.w(TAG, TAG2 + "KNOX_CUSTOM_SETTING_PERMISSION_GRANTED");
            } else {
                Log.w(TAG, TAG2 + "KNOX_CUSTOM_SETTING_PERMISSION_DENIED");
            }
            //register receiver
            kcsm = cdm.getSystemManager();
            kcsm.setHardKeyIntentState(CustomDeviceManager.ON, KPCCManager.KEYCODE_PTT,(CustomDeviceManager.KEY_ACTION_DOWN | CustomDeviceManager.KEY_ACTION_UP),CustomDeviceManager.ON);
            kcsm.setHardKeyIntentState(CustomDeviceManager.ON,KPCCManager.KEYCODE_EMERGENCY,(CustomDeviceManager.KEY_ACTION_DOWN | CustomDeviceManager.KEY_ACTION_UP),CustomDeviceManager.ON);
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(cdm.ACTION_HARD_KEY_REPORT);
            registerReceiver(broadcastReceiver,intentFilter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private boolean checkKnox() {
        EnterpriseDeviceManager enterpriseDeviceManager = EnterpriseDeviceManager.getInstance(this);
        RestrictionPolicy restrictionPolicy = enterpriseDeviceManager.getRestrictionPolicy();
        boolean isCameraEnabled = restrictionPolicy.isCameraEnabled(false);
        try {
            // this is a fake test - if it throws an exception we do not have DA or we do not have an active license
            boolean result = restrictionPolicy.setCameraState(!isCameraEnabled);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    } //checkKnox

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int keyPressed = intent.getIntExtra(cdm.EXTRA_KEY_CODE,0);
            int upDown = intent.getIntExtra(cdm.EXTRA_REPORT_TYPE,0);

            //XCP BUTTON / PTT BUTTON
            if (keyPressed == KPCCManager.KEYCODE_PTT && upDown == cdm.KEY_ACTION_DOWN) {
                buttonActions.add("PTT_DOWN");
                arrayAdapter.notifyDataSetChanged();
                //start the timer for long press
                longPressHandler.postDelayed(longPressRunnable, long_press_time);
            }

            if (keyPressed == KPCCManager.KEYCODE_PTT && upDown == cdm.KEY_ACTION_UP) {
                buttonActions.add("PTT_UP");
                arrayAdapter.notifyDataSetChanged();
                //if button was lifted before the long press timeout then we have a short press action to complete
                if (longPressHandler.hasCallbacks(longPressRunnable)) {
                    //we have a short press
                    buttonActions.add("SHORT_PRESS_END");
                    arrayAdapter.notifyDataSetChanged();
                    //remove the handler callback
                    longPressHandler.removeCallbacks(longPressRunnable);
                    //execute any short press command here:
                } else {
                    buttonActions.add("LONG_PRESS_END");
                    arrayAdapter.notifyDataSetChanged();
                    //cancel anything started in the long press
                    repeatTimerTask.cancel(true);
                }
            }

            //TOP BUTTON
            if (keyPressed == KPCCManager.KEYCODE_EMERGENCY && upDown == cdm.KEY_ACTION_DOWN) {
                buttonActions.add("TOP_DOWN");
                arrayAdapter.notifyDataSetChanged();
            }
            if (keyPressed == KPCCManager.KEYCODE_EMERGENCY && upDown == cdm.KEY_ACTION_UP) {
                buttonActions.add("TOP_UP");
                arrayAdapter.notifyDataSetChanged();
            }
        }
    };
}