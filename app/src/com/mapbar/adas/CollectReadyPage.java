package com.mapbar.adas;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.mapbar.adas.anno.PageSetting;
import com.mapbar.adas.anno.ViewInject;
import com.miyuan.obd.R;


@PageSetting(contentViewId = R.layout.collect_ready_layout)
public class CollectReadyPage extends AppBasePage implements View.OnClickListener {

    @ViewInject(R.id.title)
    private TextView title;
    @ViewInject(R.id.back)
    private View back;
    @ViewInject(R.id.report)
    private View reportV;
    @ViewInject(R.id.confirm)
    private View confirmV;

    @Override
    public void onResume() {
        super.onResume();
        MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        title.setText("校准即将完成-5");
        back.setVisibility(View.GONE);
        reportV.setVisibility(View.GONE);
        confirmV.setOnClickListener(this);
    }

    @Override
    public boolean onBackPressed() {
        PageManager.finishActivity(MainActivity.getInstance());
        return true;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.confirm:
                CollectPage collectPage = new CollectPage();
                Bundle bundle = new Bundle();
                bundle.putString("sn", getDate().getString("sn"));
                collectPage.setDate(bundle);
                PageManager.go(collectPage);
                break;
        }
    }
}
