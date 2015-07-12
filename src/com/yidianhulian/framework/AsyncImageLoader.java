package com.yidianhulian.framework;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

/**
 * @deprecated 使用ImageLoader
 * @author leeboo
 *
 */
public class AsyncImageLoader {
	protected static final int LOADED_DIRECT = 0;
	protected static final int LOADED_CALLBACK = 1;
	private HashMap<String, SoftReference<Drawable>> imageCache;
	private Context mContext;
	private static AsyncImageLoader self;
	
	private AsyncImageLoader() {
		imageCache = new HashMap<String, SoftReference<Drawable>>();
	}
	
	public static AsyncImageLoader getInstance(Context context){
		if(self == null){
			self = new AsyncImageLoader();
		}
		self.mContext = context;
		return self;
	}
	
	public void loadDrawable(final String imageUrl, final ImageCallback imageCallback) {
		Map<String, Object> loadedImage = new HashMap<String, Object>();
		loadedImage.put("callback", 	imageCallback);
		
		load(imageUrl, loadedImage, LOADED_CALLBACK);
	}
	
	public void loadDrawableAt(final String imageUrl, final ImageView imageView) {
		Map<String, Object> loadedImage = new HashMap<String, Object>();
		loadedImage.put("imageview", 	imageView);
		load(imageUrl, loadedImage, LOADED_DIRECT);
	}

	private Drawable loadImageFromUrl(String url) {
		if(url==null || "".equals(url.trim()))return null;

		URL u;
		InputStream i = null;
		BitmapDrawable d = null;
		try {
			u = new URL(url);
			i = (InputStream) u.getContent();
			d = new BitmapDrawable(mContext.getResources(), i);
			d.setTargetDensity(mContext.getResources().getDisplayMetrics());
			i.close();
			
		} catch (Exception e) {
			Log.d("loadImageFromUrl", url+":"+e.getMessage());
		}
		Log.d("loading images", url+": "+(d!=null ? "ok" : "fail"));
		return d;
	}

	public interface ImageCallback {
		public void imageLoaded(Drawable imageDrawable, String imageUrl);
	}
	
	static class MyHandler extends Handler{
		@SuppressWarnings("unchecked")
		public void handleMessage(Message message) {
			if(message.what==LOADED_CALLBACK){
				Map<String, Object> image 	= (Map<String, Object>)message.obj;
				ImageCallback imageCallback = (ImageCallback)image.get("callback");
				if(imageCallback == null)return;
				
				imageCallback.imageLoaded((Drawable) image.get("drawable"), (String)image.get("url"));
			}else if(message.what == LOADED_DIRECT){
				Map<String, Object> image 	= (Map<String, Object>)message.obj;
				ImageView imageview = (ImageView)image.get("imageview");
				if(imageview == null || image.get("drawable")==null)return;
				
				imageview.setImageDrawable((Drawable) image.get("drawable"));
			}
		}
	};
	
	private void load(final String imageUrl, final Map<String, Object> loadedImage, final int what){
		final MyHandler handler = new MyHandler();
		if (imageCache.containsKey(imageUrl)) {
			SoftReference<Drawable> softReference = imageCache.get(imageUrl);
			Drawable drawable = softReference.get();
			loadedImage.put("url", 			imageUrl);
			loadedImage.put("drawable", 	drawable);
			handler.obtainMessage(what, loadedImage).sendToTarget();
			return;
		}

		new Thread("AsyncImageLoader") {
			@Override
			public void run() {
				Drawable drawable = loadImageFromUrl(imageUrl);
				imageCache.put(imageUrl, new SoftReference<Drawable>(drawable));
				
				loadedImage.put("url", 			imageUrl);
				loadedImage.put("drawable", 	drawable);
				
				handler.obtainMessage(what, loadedImage).sendToTarget();
			}
		}.start();
	}
}
