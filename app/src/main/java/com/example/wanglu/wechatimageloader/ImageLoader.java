package com.example.wanglu.wechatimageloader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * 加载图片，采用one-line式
 */
public class ImageLoader {
    private static ImageLoader mInstance;
    /**
     * 经过处理后的图片的缓存
     */
    private LruCache<String, Bitmap> mImageCache;

    /**
     * 任务队列
     */
    private LinkedList<Runnable> mTaskQueue;
    /**
     * 任务线程池
     */
    private ExecutorService mExecutors;
    private static final int DEFUALT_THREAD_COUNT = 1;

    /**
     * 后台轮询线程
     */
    private Thread mPoolThread;
    private Handler mPoolThreadHandler;
    /**
     * UI Handler
     */
    private Handler mUIHandler;
    /**
     * 队列取出方式
     */
    private Type mType = Type.LIFO;

    public enum Type {
        FIFO, LIFO
    }

    /**
     * 调用子线程初始化的变量要注意同步问题
     */
    private Semaphore mPoolThreadHandlerSemaphore = new Semaphore(0);
    private Semaphore mPoolThreandTaskSemaphore;

    private ImageLoader(int maxThreadCount, Type type) {
        init(maxThreadCount, type);

    }

    private void init(int maxThreadCount, final Type type) {
        //后台轮询线程
        mPoolThread = new Thread("Queue_Thread") {
            @Override
            public void run() {
                super.run();
                Looper.prepare();
                mPoolThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                        //请求信号量
                        try {
                            mPoolThreandTaskSemaphore.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        //取出一个runnable执行
                        Runnable runnable = null;
                        switch (type) {
                            case FIFO:
                                runnable = mTaskQueue.removeLast();
                                break;
                            case LIFO:
                                runnable = mTaskQueue.removeFirst();
                                break;
                        }
                        mExecutors.execute(runnable);

                    }
                };
                //释放信号量
                mPoolThreadHandlerSemaphore.release();
                Looper.loop();
            }
        };
        mPoolThread.start();
        //lrucache
        int maxMemory = (int) Runtime.getRuntime().maxMemory() / 8;
        mImageCache = new LruCache<String, Bitmap>(maxMemory) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                    return value.getByteCount();
                }
                return value.getRowBytes() * value.getHeight();
            }
        };

        //线程池
        mExecutors = Executors.newFixedThreadPool(maxThreadCount);
        mTaskQueue = new LinkedList<Runnable>();
        mType = type;

        mPoolThreandTaskSemaphore = new Semaphore(maxThreadCount);
    }

    /**
     * 单例
     *
     * @return
     */
    public static ImageLoader getInstance() {
        if (mInstance == null) {
            synchronized (ImageLoader.class) {
                if (mInstance == null) {
                    mInstance = new ImageLoader(DEFUALT_THREAD_COUNT, Type.LIFO);
                }
            }
        }
        return mInstance;
    }

    /**
     * 主要方法
     */
    public void loadImage(final String path, final ImageView imageview) {
        //避免图片加载混乱
        imageview.setTag(path);
        if (mUIHandler == null) {
            mUIHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    ImageHolder holder = (ImageHolder) msg.obj;
                    if (holder != null) {
                        ImageView imageview = holder.imageView;
                        String path = holder.path;
                        Bitmap bitmap = holder.bitmap;
                        if (path.equals(imageview.getTag())) {
                            imageview.setImageBitmap(bitmap);
                        }
                    }
                }
            };

        }
        Bitmap bitmap = getBitmapFromCache(path);
        if (bitmap != null) {
            notifyUI(path, imageview, bitmap);
        } else {
            addTask(new Runnable() {
                @Override
                public void run() {
                    //1. 根据imageview的宽高 得到图片要显示的适当的宽高
                    ImageSize imageSize = getImageSizeFromImageView(imageview);
                    //2.压缩图片
                    Bitmap finalBitmap = getBitmapFromDecode(path, imageSize.height, imageSize.width);
                    //3.加入缓存
                    putBitmapToCache(path, finalBitmap);
                    notifyUI(path, imageview, finalBitmap);

                    //4.释放一个task信号量
                    mPoolThreandTaskSemaphore.release();

                }
            });

        }

    }

    /**
     * 加入缓存
     *
     * @param path
     * @param finalBitmap
     */
    private void putBitmapToCache(String path, Bitmap finalBitmap) {

        if (mImageCache.get(path) == null) {
            if (finalBitmap != null) {
                mImageCache.put(path, finalBitmap);
            }

        }
    }

    private void notifyUI(String path, ImageView imageview, Bitmap bitmap) {
        Message msg = Message.obtain();
        ImageHolder holder = new ImageHolder();
        holder.bitmap = bitmap;
        holder.imageView = imageview;
        holder.path = path;
        msg.obj = holder;
        mUIHandler.sendEmptyMessage(0x110);
    }

    /**
     * 压缩图片
     *
     * @param path
     * @param height
     * @param width
     * @return
     */
    private Bitmap getBitmapFromDecode(String path, int height, int width) {
        //获取图片宽高，并不加载到内存
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = caculateSampleSize(options, height, width);
        //需要加载到内存了
        options.inJustDecodeBounds = false;
        Bitmap finalBitmap = BitmapFactory.decodeFile(path, options);
        return finalBitmap;
    }

    private int caculateSampleSize(BitmapFactory.Options options, int height, int width) {
        int inSampleSize = 1;
        int bHeight = options.outHeight;
        int bWidth = options.outWidth;
        if (bHeight > height || bWidth > width) {
            int widthRadio = Math.round(bWidth * 1.0f / width);
            int heightRadio = Math.round(bHeight * 1.0f / height);
            inSampleSize = Math.min(widthRadio, heightRadio);
        }
        return inSampleSize;
    }

    /**
     * 根据imageview的宽高 得到图片要显示的适当的宽高
     *
     * @param imageview
     * @return
     */
    private ImageSize getImageSizeFromImageView(ImageView imageview) {
        ImageSize imageSize = new ImageSize();
        ViewGroup.LayoutParams layoutParams = imageview.getLayoutParams();
        DisplayMetrics metrics = imageview.getResources().getDisplayMetrics();
        int width = imageview.getWidth();
        //控件没有初始化得到的width是0
        if (width <= 0) {
            width = layoutParams.width;
        }
        //wrap_content fill_parent 得到的是负数
        if (width <= 0) {
            width = getValueFromField(imageview, "mMaxWidth");
        }
        if (width <= 0) {
            width = metrics.widthPixels;
        }
        int height = imageview.getWidth();
        //控件没有初始化得到的height是0
        if (height <= 0) {
            height = layoutParams.height;
        }
        //wrap_content fill_parent 得到的是负数
        if (height <= 0) {
            height = getValueFromField(imageview, "mMaxHeight");
        }
        if (height <= 0) {
            height = metrics.heightPixels;
        }
        imageSize.height = height;
        imageSize.width = width;

        return imageSize;
    }

    private int getValueFromField(Object obj, String fieldName) {
        int value = 0;
        try {
            Field field = ImageView.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            int fieldValue = field.getInt(obj);
            if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE) {
                value = fieldValue;
            }

        } catch (Exception e) {

        }
        return value;
    }

    private class ImageSize {
        int width;
        int height;
    }

    private Bitmap getBitmapFromCache(String path) {
        return mImageCache.get(path);
    }

    /**
     * 保证生成的bitmap与imageview和path对应,因为存在imageview的复用问题，所以后续还应该为iamgeview设置tag
     */
    private class ImageHolder {
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }

    /**
     * 添加任务
     */
    private synchronized void addTask(Runnable runnable) {
        mTaskQueue.add(runnable);//单纯的这种方法会导致来一个任务就会马上取出放到线程池里面，最后会堆积在线程池内部队列里面
        //也就无所谓先进先出和后进先出了，于是利用信号量让线程池最多放三个（设置三个信号量），完成一个任务就释放一个信号量
        if (mPoolThreadHandler == null)
            try {
                mPoolThreadHandlerSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        mPoolThreadHandler.sendEmptyMessage(0x110);
    }
}
