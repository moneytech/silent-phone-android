/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.silentcircle.messaging.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.messaging.activities.AxoRegisterActivity;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the Axolotl (messaging) device management fragment.
 *
 * Created by werner on 21.06.15.
 */
public class AxoDevicesFragment extends Fragment implements View.OnClickListener {
    @SuppressWarnings("unused")
    private static final String TAG = AxoDevicesFragment.class.getSimpleName();

    private AxoRegisterActivity mParent;
    private ListView mDevicesList;
    private boolean mManage;
    private String mDeviceId;
    private ProgressBar mProgressBar;

    private DeviceData mSelectedDevData;

    private int mNumberPreKeys;

    private int mNormalTextColor;
    private int mThisDevTextColor;

    private static class DeviceData {
        final String name;
        final String devId;
        final String zrtpVerificationState;
        PopupMenu mExtendedMenu;

        DeviceData(String name, String id, String zrtpState) {
            this.name = name;
            this.devId = id;
            this.zrtpVerificationState = zrtpState;
        }
    }

    public static AxoDevicesFragment newInstance(Bundle args) {
        AxoDevicesFragment f = new AxoDevicesFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            mParent.finish();
            return;
        }
        String action = args.getString(AxoRegisterActivity.ACTION, AxoRegisterActivity.ACTION_MANAGE);
        mManage = AxoRegisterActivity.ACTION_MANAGE.equals(action);
        mNormalTextColor = ContextCompat.getColor(mParent,
                com.silentcircle.common.util.ViewUtil.getColorIdFromAttributeId(mParent,
                        R.attr.sp_activity_primary_text_color));
        mThisDevTextColor = ContextCompat.getColor(mParent, R.color.black_yellow);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        commonOnAttach(getActivity());
    }

    /*
     * Deprecated on API 23
     * Use onAttachToContext instead
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        commonOnAttach(activity);
    }

    private void commonOnAttach(Activity activity) {
        try {
            mParent = (AxoRegisterActivity) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must be AxoRegisterActivity.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mParent = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.axo_devices, container, false);
        if (rootView == null)
            return null;
        rootView.findViewById(R.id.ok).setOnClickListener(this);
        rootView.findViewById(R.id.no).setOnClickListener(this);
        rootView.findViewById(R.id.cancel).setOnClickListener(this);
        mDevicesList = (ListView)rootView.findViewById(R.id.AxoDeviceList);
        mProgressBar = (ProgressBar)rootView.findViewById(R.id.progressBar);
        getDevicesInfo();
        return rootView;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                mParent.registerDevice();
                break;

            case R.id.cancel:
                mParent.finish();
                break;

            case R.id.no:
                mParent.noRegistration();
                break;

            case R.id.menu:
                selectExtendedMenu(view);
                break;

            default:
                break;
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.delete_dev:
                if (mSelectedDevData == null)
                    return true;
                mProgressBar.setVisibility(View.VISIBLE);
                mDevicesList.setVisibility(View.INVISIBLE);
                AxoDeleteInBackground deleteBackground = new AxoDeleteInBackground();
                deleteBackground.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, IOUtils.encode(mSelectedDevData.devId));
                mSelectedDevData = null;
                return true;

            case R.id.call_dev:
                if (mSelectedDevData == null)
                    return true;
                String directDial = AxoMessaging.getInstance(mParent).getUserName() + ";xscdevid=" + mSelectedDevData.devId;
                Intent intent = ContactsUtils.getCallIntent(directDial);
                intent.putExtra(DialerActivity.NO_NUMBER_CHECK, true);
                startActivity(intent);
                mParent.finish();
                return true;

            default:
                break;
        }
        return false;
    }

    private void getNumberPreKeys() {
        AxoNumPreKeysInBackground getNumberPreKeys = new AxoNumPreKeysInBackground();
        getNumberPreKeys.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /** Request the device info from provisioning server and displays data if available */
    private void getDevicesInfo() {
        DevicesInBackground devicesBackground = new DevicesInBackground(mParent);
        devicesBackground.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

//        getNumberPreKeys();
    }

    private void emptyDeviceInfo() {

        View view = getView();
        if (view == null)
            return;
        if (!mManage) {                 // show the buttons only during registration action
            view.findViewById(R.id.buttons).setVisibility(View.VISIBLE);
            view.findViewById(R.id.AxoDeviceAsk).setVisibility(View.VISIBLE);
        }
        mProgressBar.setVisibility(View.GONE);
        view.findViewById(R.id.AxoDeviceListExplanation).setVisibility(View.VISIBLE);
        ((TextView)view.findViewById(R.id.AxoDeviceListHeader)).setText(getText(R.string.no_axo_device));
    }

    private void setupDeviceList(ArrayList<DeviceData> devData) {
        emptyDeviceInfo();

        View view = getView();
        if (view == null)
            return;

        TextView tv = (TextView) view.findViewById(R.id.AxoDeviceListHeader);
        Resources res = getResources();
        int count = devData.size();
        String axoDevices = res.getQuantityString(R.plurals.axo_devices, count, count);
        tv.setText(axoDevices);

        view.findViewById(R.id.AxoDeviceListExplanation).setVisibility(View.VISIBLE);

        // Set up the devices list view
        DevicesArrayAdapter devicesAdapter = new DevicesArrayAdapter(mParent, devData);
        mDevicesList.setAdapter(devicesAdapter);
        mDevicesList.setVisibility(View.VISIBLE);
    }

    private class DevicesArrayAdapter extends ArrayAdapter<DeviceData> {
        private final Context context;

        public DevicesArrayAdapter(Context context, List<DeviceData> values) {
            super(context, R.layout.axo_devices_line, values);
            this.context = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (convertView == null)
                convertView = inflater.inflate(R.layout.axo_devices_line, parent, false);
            View rowView = convertView;
            DeviceData devData = getItem(position);

            ImageView iv = (ImageView)rowView.findViewById(R.id.verificationState);
            iv.setTag(devData.devId);
            ImageView mv = (ImageView)rowView.findViewById(R.id.menu);

            TextView tv = (TextView)rowView.findViewById(R.id.dev_name);
            tv.setText(devData.name);
            tv.setTextColor(mNormalTextColor);
            if (devData.devId.equals(mDeviceId)) {
                tv.setTextColor(mThisDevTextColor);
                iv.setVisibility(View.INVISIBLE);
                mv.setVisibility(View.INVISIBLE);
                mv.setTag(null);
                devData.mExtendedMenu = null;
            }
            else {
                if (devData.zrtpVerificationState != null) {
                    switch (devData.zrtpVerificationState) {
                        case "0":
                            break;
                        case "1":
                            iv.setImageResource(R.drawable.ic_check_white_24dp);
                            iv.setVisibility(View.VISIBLE);
                            break;
                        case "2":
                            iv.setImageResource(R.drawable.ic_check_green_24dp);
                            iv.setVisibility(View.VISIBLE);
                            break;
                    }
                }
                mv.setVisibility(View.VISIBLE);
                mv.setTag(devData);
                mv.setOnClickListener(AxoDevicesFragment.this);
            }
            tv = (TextView)rowView.findViewById(R.id.dev_id);
            tv.setText(devData.devId);
            return rowView;
        }
    }

    private void selectExtendedMenu(View view) {
        DeviceData devData = (DeviceData) view.getTag();
        if (devData != null) {
            createPopupMenu(view, devData).show();
        }
        mSelectedDevData = devData;
    }

    private PopupMenu createPopupMenu(View view, DeviceData devData) {
        devData.mExtendedMenu = new PopupMenu(view.getContext(), view);
        devData.mExtendedMenu.inflate(R.menu.local_device_extended);
        devData.mExtendedMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return onOptionsItemSelected(item);
            }
        });
        return devData.mExtendedMenu;
    }

    // Parse the device info string and update the matching device data structure
    // identityKey:device name:device id:verify state
    private DeviceData parseDeviceInfo(String devData) {
        String elements[] = devData.split(":");
        if (elements.length != 4) {
            return null;
        }
        return new DeviceData(elements[1], elements[2], elements[3]);
    }

    private ArrayList<DeviceData> getSiblingDeviceInfo() {
        byte[][] devices = AxoMessaging.getIdentityKeys(IOUtils.encode(AxoMessaging.getInstance(mParent).getUserName()));
        if (devices == null || devices.length == 0) {
            return null;
        }
        ArrayList<DeviceData> devData = new ArrayList<>(devices.length);
        for (byte[] device : devices) {
           devData.add(parseDeviceInfo(new String(device)));
        }
        return devData;
    }

    private class DevicesInBackground extends AsyncTask<Void, Void, Integer> {
        private final Context mCtx;

        DevicesInBackground(Context ctx) {
            mCtx = ctx;
        }

        @Override
        protected Integer doInBackground(Void... commands) {
            mDeviceId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(mCtx, false));

            long startTime = System.currentTimeMillis();
            AxoMessaging.axoCommand("rescanUserDevices", IOUtils.encode(AxoMessaging.getInstance(mParent).getUserName()));
            return (int) (System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for getAxoDevicesUser: " + time);

            ArrayList<DeviceData> devData = getSiblingDeviceInfo();
            if (devData != null)
                setupDeviceList(devData);
            else
                emptyDeviceInfo();
        }
    }

    private class AxoDeleteInBackground extends AsyncTask<byte[], Void, Integer> {

        private final int[] mCode = new int[1];

        @Override
        protected Integer doInBackground(byte[]... devIds) {
            long startTime = System.currentTimeMillis();
            AxoMessaging.removeAxolotlDevice(devIds[0], mCode);
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Delete device result: " + mCode[0]);
            return (int) (System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for delete: " + time);
            if (mCode[0] == 200)
                getDevicesInfo();
            else {
                mProgressBar.setVisibility(View.INVISIBLE);
                mDevicesList.setVisibility(View.VISIBLE);
            }
        }
    }

    private class AxoNumPreKeysInBackground extends AsyncTask<byte[], Void, Integer> {

        @Override
        protected Integer doInBackground(byte[]... devIds) {
            long startTime = System.currentTimeMillis();
            mNumberPreKeys = AxoMessaging.getNumPreKeys();
            return (int) (System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Processing time for get num pre-keys: " + time);
            Log.d(TAG, "++++ number of pre keys available: " + mNumberPreKeys);
        }
    }
}
