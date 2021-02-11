package com.edtest.xcpbuttonapp;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.samsung.android.knox.EnterpriseDeviceManager;
import com.samsung.android.knox.license.KnoxEnterpriseLicenseManager;
import com.samsung.android.knox.restriction.RestrictionPolicy;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PermissionsActivity extends AppCompatActivity {
    public static final String TAG = "XCP_BUTTON_APP";
    public static final String TAG2 = "PERMISSIONS_SCREEN: ";

    Button locationButton;
    Button deviceAdminButton;
    Button knoxLicenseButton;
    Button closeButton;

    Activity activity;

    boolean locationGranted = false;
    boolean deviceAdminGranted = false;
    boolean knoxLicenseActivated = false;

    private static final int DEVICE_ADMIN_ADD_RESULT_ENABLE = 1;
    private ComponentName mDeviceAdmin;
    private DevicePolicyManager mDPM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.permissions_popup_layout);

        locationButton = findViewById(R.id.locationButton);
        deviceAdminButton = findViewById(R.id.deviceAdminButton);
        knoxLicenseButton = findViewById(R.id.knoxLicenseButton);
        closeButton = findViewById(R.id.closeButton);

        if (Build.BRAND.equals("samsung")) {
            deviceAdminButton.setEnabled(true);
            knoxLicenseButton.setEnabled(true);
            mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            mDeviceAdmin = new ComponentName(PermissionsActivity.this, AdminReceiver.class);
        } else {
            deviceAdminButton.setEnabled(false);
            knoxLicenseButton.setEnabled(false);
            deviceAdminGranted = true;
            knoxLicenseActivated = true;
        }
        closeButton.setEnabled(false);

        activity = this;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            //we have permission - disable this button
            locationButton.setElevation(0);
            locationButton.setBackground(getDrawable(R.drawable.button_permission_inactive_layer_list));
            locationButton.setEnabled(false);
            locationGranted = true;
        }

        Runnable periodicTask = new Runnable(){
            @Override
            public void run() {
                try {
                    if (!deviceAdminGranted) {
                        deviceAdminGranted = mDPM.isAdminActive(mDeviceAdmin);
                    }
                    if (!knoxLicenseActivated) {
                        knoxLicenseActivated = checkKnox();
                    }

                    PermissionsActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (deviceAdminGranted) {
                                //we already have DeviceAdmin
                                deviceAdminButton.setEnabled(false);
                                deviceAdminButton.setElevation(0);
                                deviceAdminButton.setBackground(getDrawable(R.drawable.button_permission_inactive_layer_list));
                            }

                            if (knoxLicenseActivated) {
                                knoxLicenseButton.setEnabled(false);
                                knoxLicenseButton.setElevation(0);
                                knoxLicenseButton.setBackground(getDrawable(R.drawable.button_permission_inactive_layer_list));
                            }

                            if (locationGranted && deviceAdminGranted && knoxLicenseActivated) {
                                closeButton.setEnabled(true);
                                closeButton.setElevation(4);
                                closeButton.setBackground(getDrawable(R.drawable.button_permission_active_layer_list));
                            }

                        }
                    });

                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
        ScheduledFuture checkerTask = exec.scheduleAtFixedRate(periodicTask,0,1, TimeUnit.SECONDS);

    }//onCreate

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                //got location permission
                Toast.makeText(this,"Location Permission Granted", Toast.LENGTH_SHORT).show();
                locationButton.setElevation(0);
                locationButton.setBackground(getDrawable(R.drawable.button_permission_inactive_layer_list));
                locationButton.setEnabled(false);
                locationGranted = true;
            }
        }
    }//onRequestPermissionResult

    public void getLocationPermission(View view) {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }//getLocationPermission

    public void enableDeviceAdmin(View view) {
        try {
            Log.w("DEVICE_ADMIN:","ACTIVATING_ADMIN");
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDeviceAdmin);
            startActivityForResult(intent, DEVICE_ADMIN_ADD_RESULT_ENABLE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }//enableDeviceAdmin

    public void activateKnoxLicense(View view) {
        KnoxEnterpriseLicenseManager licenseManager = KnoxEnterpriseLicenseManager.getInstance(this);
        try {
            //TODO - ED's Test License Key
            licenseManager.activateLicense("KLM06-CVGEI-SHKPE-P7306-I6BD2-49LDJ");
            Log.w(TAG,TAG2 + "ACTIVATING_KNOX_LICENSE");

        } catch (Exception e) {
            e.printStackTrace();
        }
    } //activateKnoxLicense

    public void closePermission(View view) {
        finish();
    } //closePermission

    @Override
    public void onResume() {
        super.onResume();
        if (locationGranted && deviceAdminGranted && knoxLicenseActivated) {
            closeButton.setEnabled(true);
            closeButton.setElevation(4);
            closeButton.setBackground(getDrawable(R.drawable.button_permission_active_layer_list));
        }
    } //onResume

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

}