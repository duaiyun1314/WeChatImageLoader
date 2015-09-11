package com.example.wanglu.wechatimageloader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;

import java.util.List;

/**
 * Created by wanglu on 15/9/10.
 */
public class GridAdapter extends BaseAdapter {
    private List<String> imgesName;
    private String folderDir;
    private Context context;

    public GridAdapter(List<String> imgesName, String folderDir, Context context) {
        this.imgesName = imgesName;
        this.folderDir = folderDir;
        this.context = context;
    }

    @Override
    public int getCount() {
        return imgesName.size();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.grid_item, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.imageView = (ImageView) convertView.findViewById(R.id.image_view);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        viewHolder.imageView.setImageResource(R.mipmap.ic_launcher);
        ImageLoader.getInstance().loadImage(folderDir + "/" + imgesName.get(position), viewHolder.imageView);
        //
        return convertView;
    }

    private class ViewHolder {
        ImageView imageView;
    }
}
