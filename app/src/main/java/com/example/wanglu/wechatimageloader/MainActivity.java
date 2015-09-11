package com.example.wanglu.wechatimageloader;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class MainActivity extends ActionBarActivity {
    private GridView mImageGridView;
    private TextView mCurrentFolderName;
    private TextView mCurrentNum;
    private File mCurrentDir;
    private int mMaxCount;
    private List<Folder> mFolders = new ArrayList<>();
    private Handler mUIHandler;
    private GridAdapter mAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initHandler();
        initData();
        test();

    }

    private boolean test() {
        File f = new File(Environment.getExternalStorageDirectory(), "testSDcard");
        if (f == null) {
            Log.e("wanglu", "new file failed");
        }
        try {
            if (f.exists()) {
                Log.d("wanglu", "delete last ");
                f.delete();
            }
            if (!f.createNewFile()) {
                return false;
            }
            Log.d("wanglu", "11111111 ");
            f.delete();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            Log.d("CXY", "2222222222 ");
        }
        return false;
    }

    private void initHandler() {
        if (mUIHandler == null) {
            mUIHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    if (mCurrentDir == null) {
                        Toast.makeText(MainActivity.this, "未扫描到图片", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    view2Data();
                }
            };
        }

    }

    private void view2Data() {
        List<String> paths = Arrays.asList(mCurrentDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                if (filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png")) {
                    return true;
                }
                return false;
            }
        }));
        if (mAdapter == null) {
            mAdapter = new GridAdapter(paths, mCurrentDir.getAbsolutePath(), MainActivity.this);

        }
        mImageGridView.setAdapter(mAdapter);
        mCurrentFolderName.setText(mCurrentDir.getName());
        mCurrentNum.setText(paths.size() + "");
    }

    private void initData() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                if (Utils.isExternalEnable()) {
                    ContentResolver resolver = getContentResolver();
                    Uri imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    Cursor cursor = resolver.query(imageUri, null, MediaStore.Images.Media.MIME_TYPE + " = ? or " + MediaStore.Images.Media.MIME_TYPE + " = ? ", new String[]{"image/jpeg", "image/png"}, MediaStore.Images.Media.DATE_MODIFIED);
                    Set<String> parentPaths = new HashSet<String>();
                    while (cursor.moveToNext()) {
                        String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                        File fileParent = new File(path).getParentFile();
                        if (fileParent == null) continue;
                        String parentPath = fileParent.getAbsolutePath();
                        if (parentPaths.contains(parentPath)) {
                            continue;
                        } else {
                            parentPaths.add(parentPath);
                            Folder folder = new Folder();
                            folder.setFirstPath(path);
                            folder.setPath(parentPath);
                            if (fileParent.list() == null) continue;
                            int picSize = fileParent.list(new FilenameFilter() {
                                @Override
                                public boolean accept(File dir, String filename) {
                                    if (filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png")) {
                                        return true;
                                    }
                                    return false;
                                }
                            }).length;
                            folder.setCount(picSize);
                            mFolders.add(folder);
                            if (picSize > mMaxCount) {
                                mMaxCount = picSize;
                                mCurrentDir = fileParent;
                            }
                        }

                    }
                    cursor.close();
                    Log.i("wanglu", "mFolders.size:" + mFolders.size());
                    //扫描完成，通知ui
                    mUIHandler.sendEmptyMessage(0x110);

                } else {
                    Toast.makeText(MainActivity.this, "sdcard不可用", Toast.LENGTH_SHORT).show();
                }
            }
        }.start();
    }

    private void initView() {
        mImageGridView = (GridView) findViewById(R.id.gridview);
        mCurrentFolderName = (TextView) findViewById(R.id.folder_name);
        mCurrentNum = (TextView) findViewById(R.id.images_num);


    }

    private class Folder {
        String path;
        String name;
        int count;
        String firstPath;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
            int lastIndex = path.lastIndexOf("/");
            this.name = this.path.substring(lastIndex);
        }

        public String getName() {
            return name;
        }


        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getFirstPath() {
            return firstPath;
        }

        public void setFirstPath(String firstPath) {
            this.firstPath = firstPath;
        }
    }
}
