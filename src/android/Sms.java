package com.cordova.plugins.sms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import java.util.ArrayList;
import java.util.UUID;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;

public class Sms extends CordovaPlugin {
    private static final String INTENT_FILTER_SMS_SENT = "SMS_SENT";
    private static final int REQUEST_PERMISSION_REQ_CODE = 1;
    private static final int SEND_SMS_REQ_CODE = 0;
    public final String ACTION_HAS_PERMISSION = "has_permission";
    public final String ACTION_REQUEST_PERMISSION = "request_permission";
    public final String ACTION_SEND_SMS = "send";
    /* access modifiers changed from: private */
    public JSONArray args;
    /* access modifiers changed from: private */
    public CallbackContext callbackContext;

    public boolean execute(String action, JSONArray args2, CallbackContext callbackContext2) throws JSONException {
        this.callbackContext = callbackContext2;
        this.args = args2;
        if (action.equals("send")) {
            boolean isIntent = false;
            try {
                isIntent = args2.getString(2).equalsIgnoreCase("INTENT");
            } catch (NullPointerException e) {
            }
            if (isIntent || hasPermission()) {
                sendSMS();
            } else {
                requestPermission(0);
            }
            return true;
        } else if (action.equals("has_permission")) {
            callbackContext2.sendPluginResult(new PluginResult(Status.OK, hasPermission()));
            return true;
        } else if (!action.equals("request_permission")) {
            return false;
        } else {
            requestPermission(1);
            return true;
        }
    }

    private boolean hasPermission() {
        return this.cordova.hasPermission("android.permission.SEND_SMS");
    }

    private void requestPermission(int requestCode) {
        this.cordova.requestPermission(this, requestCode, "android.permission.SEND_SMS");
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == -1) {
                this.callbackContext.sendPluginResult(new PluginResult(Status.ERROR, "User has denied permission"));
                return;
            }
        }
        if (requestCode == 0) {
            sendSMS();
        } else {
            this.callbackContext.sendPluginResult(new PluginResult(Status.OK, true));
        }
    }

    private boolean sendSMS() {
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                String separator = ";";
                try {
                    if (Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
                        separator = ",";
                    }
                    String phoneNumber = Sms.this.args.getJSONArray(0).join(separator).replace("\"", "");
                    String message = Sms.this.args.getString(1);
                    String method = Sms.this.args.getString(2);
                    String slot = Sms.this.args.getString(4);
                    if (Boolean.parseBoolean(Sms.this.args.getString(3))) {
                        message = message.replace("\\n", System.getProperty("line.separator"));
                    }
                    if (!Sms.this.checkSupport()) {
                        Sms.this.callbackContext.sendPluginResult(new PluginResult(Status.ERROR, "SMS not supported on this platform"));
                        return;
                    }
                    if (method.equalsIgnoreCase("INTENT")) {
                        Sms.this.invokeSMSIntent(phoneNumber, message);
                        Sms.this.callbackContext.sendPluginResult(new PluginResult(Status.OK));
                    } else {
                        Sms.this.send(Sms.this.callbackContext, phoneNumber, message, slot);
                    }
                } catch (JSONException e) {
                    Sms.this.callbackContext.sendPluginResult(new PluginResult(Status.JSON_EXCEPTION));
                }
            }
        });
        return true;
    }

    /* access modifiers changed from: private */
    public boolean checkSupport() {
        return this.cordova.getActivity().getPackageManager().hasSystemFeature("android.hardware.telephony");
    }

    private int getSubscriptionId(String str) {
        if (str == null) {
            return -1;
        }
        try {
            int parseInt = Integer.parseInt(str);
            if (VERSION.SDK_INT < 22) {
                return -1;
            }
            int i = -1;
            for (SubscriptionInfo subscriptionInfo : SubscriptionManager.from(this.cordova.getActivity()).getActiveSubscriptionInfoList()) {
                if (parseInt == subscriptionInfo.getSimSlotIndex()) {
                    i = subscriptionInfo.getSubscriptionId();
                }
            }
            return i;
        } catch (Exception e) {
            return -1;
        }
    }

    /* access modifiers changed from: private */
    @SuppressLint({"NewApi"})
    public void invokeSMSIntent(String phoneNumber, String message) {
        Intent sendIntent;
        if (!"".equals(phoneNumber) || VERSION.SDK_INT < 19) {
            sendIntent = new Intent("android.intent.action.VIEW");
            sendIntent.putExtra("sms_body", message);
            sendIntent.putExtra("address", phoneNumber);
            StringBuilder sb = new StringBuilder();
            sb.append("smsto:");
            sb.append(Uri.encode(phoneNumber));
            sendIntent.setData(Uri.parse(sb.toString()));
        } else {
            String defaultSmsPackageName = android.provider.Telephony.Sms.getDefaultSmsPackage(this.cordova.getActivity());
            sendIntent = new Intent("android.intent.action.SEND");
            sendIntent.setType("text/plain");
            sendIntent.putExtra("android.intent.extra.TEXT", message);
            if (defaultSmsPackageName != null) {
                sendIntent.setPackage(defaultSmsPackageName);
            }
        }
        this.cordova.getActivity().startActivity(sendIntent);
    }

    /* access modifiers changed from: private */
    public void send(CallbackContext callbackContext2, String phoneNumber, String message, String slot) {
        SmsManager manager;
        int subscriptionId = getSubscriptionId(slot);
        if (VERSION.SDK_INT < 22 || subscriptionId < 0) {
            manager = SmsManager.getDefault();
        } else {
            manager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
        }
        final ArrayList<String> parts = manager.divideMessage(message);
        final CallbackContext callbackContext3 = callbackContext2;
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            boolean anyError = false;
            int partsCount = parts.size();

            public void onReceive(Context context, Intent intent) {
                int resultCode = getResultCode();
                if (resultCode != -1) {
                    switch (resultCode) {
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                            this.anyError = true;
                            break;
                    }
                }
                this.partsCount--;
                if (this.partsCount == 0) {
                    if (this.anyError) {
                        callbackContext3.sendPluginResult(new PluginResult(Status.ERROR));
                    } else {
                        callbackContext3.sendPluginResult(new PluginResult(Status.OK));
                    }
                    Sms.this.cordova.getActivity().unregisterReceiver(this);
                }
            }
        };
        StringBuilder sb = new StringBuilder();
        sb.append(INTENT_FILTER_SMS_SENT);
        sb.append(UUID.randomUUID().toString());
        String intentFilterAction = sb.toString();
        this.cordova.getActivity().registerReceiver(broadcastReceiver, new IntentFilter(intentFilterAction));
        PendingIntent sentIntent = PendingIntent.getBroadcast(this.cordova.getActivity(), 0, new Intent(intentFilterAction), 0);
        if (parts.size() > 1) {
            ArrayList arrayList = new ArrayList();
            for (int i = 0; i < parts.size(); i++) {
                arrayList.add(sentIntent);
            }
            ArrayList arrayList2 = arrayList;
            manager.sendMultipartTextMessage(phoneNumber, null, parts, arrayList, null);
            return;
        }
        manager.sendTextMessage(phoneNumber, null, message, sentIntent, null);
    }
}
