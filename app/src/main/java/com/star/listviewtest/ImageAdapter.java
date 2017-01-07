package com.star.listviewtest;


import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class ImageAdapter extends ArrayAdapter<String> {

    private LruCache<String, BitmapDrawable> mMemoryCache;

    private ListView mListView;
    private Bitmap mLoadingBitmap;

    private String[] mUrls;

    public ImageAdapter(Context context, int resource, String[] objects) {
        super(context, resource, objects);

        mUrls = objects;

        mLoadingBitmap = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.empty_photo);

        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;

        mMemoryCache = new LruCache<String, BitmapDrawable>(cacheSize) {
            @Override
            protected int sizeOf(String key, BitmapDrawable bitmapDrawable) {
                return bitmapDrawable.getBitmap().getByteCount() / 1024;
            }
        };
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (mListView == null) {
            mListView = (ListView) parent;
        }

        String currentUrl = getItem(position);

        View view;

        if (convertView == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.image_item, null);
        } else {
            view = convertView;
        }

        ImageView imageView = (ImageView) view.findViewById(R.id.image_view);

        BitmapDrawable bitmapDrawable = getBitmapFromMemoryCache(currentUrl);

        if (bitmapDrawable != null) {
            imageView.setImageDrawable(bitmapDrawable);
        } else if (cancelPotentialWorkerTask(currentUrl, imageView)) {
            BitmapWorkerTask bitmapWorkerTask = new BitmapWorkerTask(imageView);
            AsyncDrawable asyncDrawable = new AsyncDrawable(getContext().getResources(),
                    mLoadingBitmap, bitmapWorkerTask);
            imageView.setImageDrawable(asyncDrawable);
            bitmapWorkerTask.execute(currentUrl);
        }

        for (int i = position - 1; i <= position + 1; i++) {
            if (i >= 0 && i < mUrls.length) {
                final String url = mUrls[i];
                if (getBitmapFromMemoryCache(url) == null) {

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Bitmap bitmap = downloadBitmap(url);
                            BitmapDrawable drawable = new BitmapDrawable(getContext().getResources(), bitmap);
                            synchronized (mMemoryCache) {
                                addBitmapToMemoryCache(url, drawable);
                            }
                        }
                    }).start();
                }
            }
        }

        return view;
    }

    private BitmapDrawable getBitmapFromMemoryCache(String key) {
        return mMemoryCache.get(key);
    }

    private void addBitmapToMemoryCache(String key, BitmapDrawable bitmapDrawable) {
        if (getBitmapFromMemoryCache(key) == null) {
            mMemoryCache.put(key, bitmapDrawable);
        }
    }

    private class BitmapWorkerTask extends AsyncTask<String, Void, BitmapDrawable> {

        private ImageView mImageView;
        private String mUrl;

        private WeakReference<ImageView> mImageViewWeakReference;

        public BitmapWorkerTask(ImageView imageView) {
            mImageViewWeakReference = new WeakReference<>(imageView);
        }

        @Override
        protected BitmapDrawable doInBackground(String... params) {

            mUrl = params[0];

            Bitmap bitmap = downloadBitmap(mUrl);
            BitmapDrawable bitmapDrawable = new BitmapDrawable(getContext().getResources(), bitmap);

            addBitmapToMemoryCache(mUrl, bitmapDrawable);

            return bitmapDrawable;
        }

        @Override
        protected void onPostExecute(BitmapDrawable drawable) {

            mImageView = getAttachedImageView();

            if (mImageView != null && drawable != null) {
                mImageView.setImageDrawable(drawable);
            }
        }

        private ImageView getAttachedImageView() {
            ImageView imageView = mImageViewWeakReference.get();

            BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

            if (this == bitmapWorkerTask) {
                return imageView;
            }

            return null;
        }
    }

    private Bitmap downloadBitmap(String imageUrl) {

        Bitmap bitmap = null;

        HttpURLConnection httpURLConnection = null;

        try {
            URL url = new URL(imageUrl);
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setConnectTimeout(5 * 1000);
            httpURLConnection.setReadTimeout(10 * 1000);
            bitmap = BitmapFactory.decodeStream(httpURLConnection.getInputStream());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }

        return bitmap;
    }

    private boolean cancelPotentialWorkerTask(String url, ImageView imageView) {
        BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            String imageUrl = bitmapWorkerTask.mUrl;
            if (imageUrl == null || !imageUrl.equals(url)) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }

        return true;
    }

    private BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            Drawable drawable = imageView.getDrawable();

            if (drawable instanceof AsyncDrawable) {
                return ((AsyncDrawable) drawable).getBitmapWorkerTask();
            }
        }

        return null;
    }

    private class AsyncDrawable extends BitmapDrawable {

        private WeakReference<BitmapWorkerTask> mBitmapWorkerTaskWeakReference;

        public AsyncDrawable(Resources res, Bitmap bitmap,
                             BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            mBitmapWorkerTaskWeakReference = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return mBitmapWorkerTaskWeakReference.get();
        }
    }

}
