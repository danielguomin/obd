package com.mapbar.adas;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.mapbar.adas.anno.PageSetting;
import com.mapbar.adas.anno.ViewInject;
import com.mapbar.adas.utils.DBManager;
import com.mapbar.hamster.BleCallBackListener;
import com.mapbar.hamster.BlueManager;
import com.mapbar.hamster.core.HexUtils;
import com.mapbar.hamster.core.ProtocolUtils;
import com.mapbar.hamster.log.Log;
import com.miyuan.obd.R;

import java.util.ArrayList;
import java.util.List;

import static com.mapbar.hamster.OBDEvent.FAULT_CODE;

@PageSetting(contentViewId = R.layout.fault_code_layout)
public class FaultCodePage extends AppBasePage implements View.OnClickListener, BleCallBackListener {
    @ViewInject(R.id.back)
    private View back;
    @ViewInject(R.id.title)
    private TextView titleTV;
    @ViewInject(R.id.report)
    private View reportV;
    @ViewInject(R.id.confirm)
    private TextView confirmV;
    @ViewInject(R.id.content)
    private RecyclerView contentLV;
    private List<FaultCode> codeList = new ArrayList<>();
    private NormalAdapter normalAdapter;

    @Override
    public void onResume() {
        super.onResume();
        BlueManager.getInstance().addBleCallBackListener(this);
        titleTV.setText("爱车体检");
        confirmV.setText("一键体检");
        back.setOnClickListener(this);
        confirmV.setOnClickListener(this);
        reportV.setVisibility(View.GONE);
    }

    @Override
    public void onStop() {
        super.onStop();
        BlueManager.getInstance().removeCallBackListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.back:
                PageManager.back();
                break;
            case R.id.confirm:
                codeList.clear();
                BlueManager.getInstance().send(ProtocolUtils.sendFaultCode());
                break;
        }
    }

    @Override
    public void onEvent(int event, Object data) {
        switch (event) {
            case FAULT_CODE:
                paraseFaultResult((byte[]) data);
                break;
        }
    }

    private void paraseFaultResult(byte[] result) {
        int count = HexUtils.byteToInt(result[0]);
        for (int i = 0; i < count; i++) {
            byte[] item = new byte[3];
            System.arraycopy(result, 1 + i * 3, item, 0, item.length);
            String type = "";
            switch ((item[1] & 0xFF) >> 6) {
                case 0:
                    type = "P";
                    break;
                case 1:
                    type = "C";
                    break;
                case 2:
                    type = "B";
                    break;
                case 3:
                    type = "U";
                    break;
            }
            type += HexUtils.byte2HexStr(new byte[]{(byte) (item[1] & 0x3F), item[2]}).replace(" ", "");
            Log.d("type " + type);
            codeList.addAll(DBManager.getInstance().getInfoForCode(type));
        }
        normalAdapter = new NormalAdapter(codeList);
        contentLV.setLayoutManager(new LinearLayoutManager(getContext()));
        contentLV.setAdapter(normalAdapter);
    }

    public static class VH extends RecyclerView.ViewHolder {
        public final TextView id;
        public final TextView suit;
        public final TextView desc_ch;
        public final TextView desc_en;
        public final TextView system;
        public final TextView detail;

        public VH(View v) {
            super(v);
            id = v.findViewById(R.id.id);
            suit = v.findViewById(R.id.suit);
            desc_ch = v.findViewById(R.id.desc_ch);
            desc_en = v.findViewById(R.id.desc_en);
            system = v.findViewById(R.id.system);
            detail = v.findViewById(R.id.detail);
        }
    }

    public class NormalAdapter extends RecyclerView.Adapter<VH> {

        private List<FaultCode> mDatas;

        public NormalAdapter(List<FaultCode> data) {
            this.mDatas = data;
        }

        @Override
        public void onBindViewHolder(final VH holder, int position) {
            FaultCode code = mDatas.get(position);
            holder.id.setText(code.getId());
            holder.suit.setText(code.getSuit());
            holder.desc_ch.setText(code.getDesc_ch());
            holder.desc_en.setText(code.getDesc_en());
            holder.system.setText(code.getSystem());
            holder.detail.setText(code.getDetail());
        }

        @Override
        public int getItemCount() {
            return mDatas.size();
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.fault_code_item, parent, false);
            return new VH(v);
        }
    }
}
