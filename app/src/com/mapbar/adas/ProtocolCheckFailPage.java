package com.mapbar.adas;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbar.adas.anno.PageSetting;
import com.mapbar.adas.anno.ViewInject;
import com.mapbar.adas.utils.CustomDialog;
import com.mapbar.adas.utils.OBDUtils;
import com.mapbar.adas.utils.URLUtils;
import com.mapbar.hamster.BleCallBackListener;
import com.mapbar.hamster.BlueManager;
import com.mapbar.hamster.OBDEvent;
import com.mapbar.hamster.OBDStatusInfo;
import com.mapbar.hamster.core.ProtocolUtils;
import com.mapbar.hamster.log.Log;
import com.miyuan.obd.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@PageSetting(contentViewId = R.layout.protocol_check_fail_layout, toHistory = false)
public class ProtocolCheckFailPage extends AppBasePage implements BleCallBackListener, View.OnClickListener {
    @ViewInject(R.id.title)
    private TextView title;
    @ViewInject(R.id.back)
    private View back;
    @ViewInject(R.id.report)
    private View reportV;
    @ViewInject(R.id.before)
    private View beforeMatchingV;
    @ViewInject(R.id.current)
    private TextView currentMatchingTV;
    @ViewInject(R.id.confirm)
    private TextView confirmV;
    private CustomDialog dialog;

    private volatile boolean showConfirm;
    private volatile int times = 1;
    private int time = 15;
    private Timer timer;
    private TimerTask timerTask;
    private volatile OBDStatusInfo obdStatusInfo;
    private boolean beforeMatching;
    private boolean timeOut;

    @Override
    public void onResume() {
        super.onResume();
        MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        back.setVisibility(View.GONE);
        reportV.setOnClickListener(this);
        title.setText("未检测到车辆数据");
        beforeMatching = getDate().getBoolean("before_matching");
        if (beforeMatching) {
            beforeMatchingV.setVisibility(View.VISIBLE);
            currentMatchingTV.setVisibility(View.INVISIBLE);
            confirmV.setVisibility(View.INVISIBLE);
        } else {
            beforeMatchingV.setVisibility(View.INVISIBLE);
            currentMatchingTV.setVisibility(View.VISIBLE);
            confirmV.setVisibility(View.VISIBLE);
            confirmV.setEnabled(timeOut);
        }
        BlueManager.getInstance().send(ProtocolUtils.checkMatchingStatus());
    }

    @Override
    public void onStart() {
        super.onStart();
        BlueManager.getInstance().addBleCallBackListener(this);
    }

    @Override
    public boolean onBackPressed() {
        PageManager.finishActivity(MainActivity.getInstance());
        return true;
    }

    @Override
    public void onEvent(int event, Object data) {
        switch (event) {
            case OBDEvent.NO_PARAM: // 删除参数逻辑
                confirmV.setEnabled(true);
                obdStatusInfo = (OBDStatusInfo) data;
                if (null != timer) {
                    timer.cancel();
                    timer = null;
                }
                if (null != timerTask) {
                    timerTask.cancel();
                    timerTask = null;
                }
                CollectGuide collectGuide = new CollectGuide();
                Bundle collectBundle = new Bundle();
                collectBundle.putString("sn", obdStatusInfo.getSn());
                collectBundle.putBoolean("matching", false);
                collectBundle.putString("pVersion", obdStatusInfo.getpVersion());
                collectBundle.putString("bVersion", obdStatusInfo.getbVersion());
                collectGuide.setDate(collectBundle);
                PageManager.go(collectGuide);
                break;
            case OBDEvent.CURRENT_MISMATCHING:
                obdStatusInfo = (OBDStatusInfo) data;
                if (!showConfirm) {
                    showConfirm = true;
                    time = 15;
                    initTimer();
                    timer.schedule(timerTask, 0, 1000);
                }
                BlueManager.getInstance().send(ProtocolUtils.checkMatchingStatus());
                break;
            case OBDEvent.BEFORE_MATCHING:
                BlueManager.getInstance().send(ProtocolUtils.checkMatchingStatus());
                break;
            case OBDEvent.UN_ADJUST:
                obdStatusInfo = (OBDStatusInfo) data;
                checkColectStauts();
                break;
            case OBDEvent.ADJUST_SUCCESS:
                obdStatusInfo = (OBDStatusInfo) data;
                PageManager.go(new HomePage());
                break;
        }
    }

    /**
     * 获取采集上传状态
     */
    private void checkColectStauts() {

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("serialNumber", obdStatusInfo.getSn());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.d("checkColectStauts input " + jsonObject.toString());

        RequestBody requestBody = new FormBody.Builder()
                .add("params", GlobalUtil.encrypt(jsonObject.toString())).build();

        Request request = new Request.Builder()
                .url(URLUtils.UPDATE_STATUS)
                .post(requestBody)
                .addHeader("content-type", "application/json;charset:utf-8")
                .build();
        GlobalUtil.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                GlobalUtil.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        dialog = CustomDialog.create(GlobalUtil.getMainActivity().getSupportFragmentManager())
                                .setViewListener(new CustomDialog.ViewListener() {
                                    @Override
                                    public void bindView(View view) {
                                        ((TextView) (view.findViewById(R.id.confirm))).setText("已打开网络，重试");
                                        ((TextView) (view.findViewById(R.id.info))).setText("请打开网络，否则无法完成当前操作!");
                                        ((TextView) (view.findViewById(R.id.title))).setText("网络异常");
                                        final View confirm = view.findViewById(R.id.confirm);
                                        confirm.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                dialog.dismiss();
                                                checkColectStauts();
                                            }
                                        });
                                    }
                                })
                                .setLayoutRes(R.layout.dailog_common_warm)
                                .setCancelOutside(false)
                                .setDimAmount(0.5f)
                                .isCenter(true)
                                .setWidth(OBDUtils.getDimens(getContext(), R.dimen.dailog_width))
                                .show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responese = response.body().string();
                Log.d("checkColectStauts success " + responese);
                try {
                    final JSONObject result = new JSONObject(responese);
                    if ("000".equals(result.optString("status"))) {
                        String state = result.optString("state");
                        if ("1".equals(state)) {
                            CollectReadyPage collectPage = new CollectReadyPage();
                            Bundle bundle = new Bundle();
                            bundle.putString("sn", obdStatusInfo.getSn());
                            collectPage.setDate(bundle);
                            PageManager.go(collectPage);
                        } else {
                            CollectGuide collectGuide = new CollectGuide();
                            Bundle collectBundle = new Bundle();
                            collectBundle.putBoolean("matching", true);
                            collectBundle.putString("sn", obdStatusInfo.getSn());
                            collectBundle.putString("pVersion", obdStatusInfo.getpVersion());
                            collectBundle.putString("bVersion", obdStatusInfo.getbVersion());
                            collectGuide.setDate(collectBundle);
                            PageManager.go(collectGuide);
                        }
                    }
                } catch (JSONException e) {
                    Log.d("checkColectStauts failure " + e.getMessage());
                }
            }
        });
    }

    private void initTimer() {
        timerTask = new TimerTask() {
            @Override
            public void run() {
                GlobalUtil.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (time <= 0 && timer != null) {
                            timeOut = true;
                            timer.cancel();
                            timer = null;
                            timerTask.cancel();
                            timerTask = null;
                            confirmV.setText("确认已打火");
                            confirmV.setEnabled(true);
                            confirmV.setOnClickListener(ProtocolCheckFailPage.this);
                        } else {
                            confirmV.setText("确认已打火(" + time + "s)");
                        }
                        time--;
                    }
                });
            }
        };

        timer = new Timer();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.report:
                uploadLog();
                break;
            case R.id.confirm:
                if (times >= 2) {
                    confirmV.setEnabled(false);
                    confirmV.setOnClickListener(null);
                    cleanParams(obdStatusInfo);
                } else {
                    times++;
                    timeOut = false;
                    currentMatchingTV.setText("请您再次确认车辆已打火！\n请您务必确认已打火后再点击确认按钮!");
                    confirmV.setText("确认已打火");
                    confirmV.setOnClickListener(null);
                    confirmV.setEnabled(false);
                    time = 15;
                    initTimer();
                    timer.schedule(timerTask, 0, 1000);
                }
                break;
        }
    }

    private void uploadLog() {
        Log.d("ProtocolCheckFailPage uploadLog ");
        final File dir = new File(Environment.getExternalStorageDirectory().getPath() + File.separator + "obd");
        final File[] logs = dir.listFiles();

        if (null != logs && logs.length > 0) {
            MultipartBody.Builder builder = new MultipartBody.Builder();
            builder.addPart(MultipartBody.Part.createFormData("serialNumber", obdStatusInfo.getSn()))
                    .addPart(MultipartBody.Part.createFormData("type", "1"));
            for (File file : logs) {
                builder.addFormDataPart("file", file.getName(), RequestBody.create(MediaType.parse("application/octet-stream"), file));
            }
            Request request = new Request.Builder()
                    .url(URLUtils.UPDATE_ERROR_FILE)
                    .post(builder.build())
                    .build();

            GlobalUtil.getOkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.d("ProtocolCheckFailPage uploadLog onFailure " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responese = response.body().string();
                    Log.d("ProtocolCheckFailPage uploadLog success " + responese);
                    try {
                        final JSONObject result = new JSONObject(responese);
                        if ("000".equals(result.optString("status"))) {
                            GlobalUtil.getHandler().post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getContext(), "上报成功", Toast.LENGTH_SHORT).show();
                                }
                            });
                            for (File delete : logs) {
                                delete.delete();
                            }
                        }
                    } catch (JSONException e) {
                        Log.d("ProtocolCheckFailPage uploadLog failure " + e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * 清除参数
     *
     * @param obdStatusInfo
     */
    private void cleanParams(final OBDStatusInfo obdStatusInfo) {

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("serialNumber", obdStatusInfo.getSn());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.d("cleanParams input " + jsonObject.toString());

        RequestBody requestBody = new FormBody.Builder()
                .add("params", GlobalUtil.encrypt(jsonObject.toString())).build();
        Request request = new Request.Builder()
                .url(URLUtils.CLEAR_PARAM)
                .post(requestBody)
                .addHeader("content-type", "application/json;charset:utf-8")
                .build();
        GlobalUtil.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                GlobalUtil.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        dialog = CustomDialog.create(GlobalUtil.getMainActivity().getSupportFragmentManager())
                                .setViewListener(new CustomDialog.ViewListener() {
                                    @Override
                                    public void bindView(View view) {
                                        ((TextView) (view.findViewById(R.id.confirm))).setText("已打开网络，重试");
                                        ((TextView) (view.findViewById(R.id.info))).setText("请打开网络，否则无法完成当前操作!");
                                        ((TextView) (view.findViewById(R.id.title))).setText("网络异常");
                                        final View confirm = view.findViewById(R.id.confirm);
                                        confirm.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                dialog.dismiss();
                                                confirm.setEnabled(false);
                                                cleanParams(obdStatusInfo);
                                            }
                                        });
                                    }
                                })
                                .setLayoutRes(R.layout.dailog_common_warm)
                                .setCancelOutside(false)
                                .setDimAmount(0.5f)
                                .isCenter(true)
                                .setWidth(OBDUtils.getDimens(getContext(), R.dimen.dailog_width))
                                .show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responese = response.body().string();
                Log.d("cleanParams success " + responese);
                try {
                    final JSONObject result = new JSONObject(responese);
                    if ("000".equals(result.optString("status"))) {
                        BlueManager.getInstance().send(ProtocolUtils.cleanParams());
                    }
                } catch (JSONException e) {
                    Log.d("cleanParams failure " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void onStop() {
        BlueManager.getInstance().removeCallBackListener(this);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }
}
