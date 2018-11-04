package com.feite.ble;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;


import java.util.List;


/**
 * Created by jiafei on 17/4/28.
 */

public class DeviceAdapter extends BaseAdapter {

    private List<String> list;
    private LayoutInflater inflater;

    public DeviceAdapter(List<String> list, Context context) {
        this.list = list;
        this.inflater=LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return list==null?0:list.size();
    }

    @Override
    public String getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public List<String> getList(){
        return list;
    }

    public void setList(List<String> list) {
        this.list = list;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view=inflater.inflate(R.layout.device_item,null);
        String device=getItem(position);
        TextView tv_name= (TextView) view.findViewById(R.id.txt_device_name);
        tv_name.setText(device);
        return view;
    }
}
