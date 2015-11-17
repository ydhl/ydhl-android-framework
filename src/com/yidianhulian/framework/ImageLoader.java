package com.yidianhulian.framework;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

/**
 * 
 * 
 * @author leeboo
 *
 */
public class ImageLoader {
	private Context mContext;
	private static HashMap<String, Object> mMemoryCache = new HashMap<String, Object>();
	MyHandler mHandler = new MyHandler();
	
	private static final int LOAD_IMAGE = 0; //加载图片
	private static final int LOAD_FILE = 1; //加载其它资源文件
	private int width=0;
	private int height=0;
	
	public ImageLoader(Context context) {
	    mContext = context;
	}
	
	private File cachedFile(final String url){
	   File existFile = new File(url);
	   if (existFile.exists()){
	      return existFile;
	   }
	   File dir = mContext.getExternalCacheDir();
	   if(dir == null)return null;
	   
	   if(! dir.exists()){  
           return null;
       }  
	   File[] files = dir.listFiles(new FilenameFilter(){
	       @Override
            public boolean accept(File dir, String filename) {
                return filename.equals(Util.MD5(url));
            }
	   });
	   if (files==null || files.length==0) return null;
	   
	   return files[0];  
	}
	
	public void loadImage(ImageView imageView, String imageUrl, int width, int height){
	    this.width = width;
	    this.height = height;
        loadImage(imageView, imageUrl, new ImageLoaded() {
            @Override
            public void imageLoaded(ImageView imageView, Drawable imageDrawable) {
                if(imageDrawable!=null){
                    imageView.setImageDrawable(imageDrawable);
                }
            }
        });
    }
	
	public void loadImage(ImageView imageView, String imageUrl){
	    loadImage(imageView, imageUrl, new ImageLoaded() {
            @Override
            public void imageLoaded(ImageView imageView, Drawable imageDrawable) {
                if(imageDrawable!=null){
                    imageView.setImageDrawable(imageDrawable);
                }
            }
        });
	}
	
	public void loadImage(final ImageView imageView, final String imageUrl, final ImageLoaded imageCallback) {
	    if(mMemoryCache.containsKey(imageUrl)){
	        mHandler.obtainMessage(LOAD_IMAGE, new Object[]{imageView, mMemoryCache.get(imageUrl), imageCallback}).sendToTarget();
	        return;
	    }
	    new Thread("loadDrawable"){
	        public void run(){
        	    File cachedFile = cachedFile(imageUrl);
                if (cachedFile != null) {
                	Bitmap bm = BitmapFactory.decodeFile(cachedFile.getAbsolutePath());
                	
                	if(width>0 && height>0){
                	    bm = ImageUtils.extractMiniThumb(bm, width, height, true);
                	}
                	BitmapDrawable cached = new BitmapDrawable(mContext.getResources(), bm);
                	
                    mMemoryCache.put(imageUrl, cached);
                    mHandler.obtainMessage(LOAD_IMAGE, new Object[]{imageView, cached, imageCallback}).sendToTarget();
                    return;
                }
        
                new Thread("loadImageFromUrl") {
                    @Override
                    public void run() {
                        BitmapDrawable drawable = loadImageFromUrl(imageUrl);
                        mHandler.obtainMessage(LOAD_IMAGE, new Object[]{imageView, drawable, imageCallback}).sendToTarget();
                        
                        if(drawable!=null){
                            mMemoryCache.put(imageUrl, (BitmapDrawable)drawable);
                            
                            try {
                                File dirFile = mContext.getExternalCacheDir();  
                                if(dirFile != null){
                                    if( ! dirFile.exists()){  
                                        dirFile.mkdir();  
                                    }  
                                    
                                    File cacheFile = new File(dirFile.getAbsolutePath() + "/" + Util.MD5(imageUrl));  
                                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cacheFile));
                                      
                                    drawable.getBitmap().compress(Bitmap.CompressFormat.PNG, 100, bos);  
                                    bos.flush();  
                                    bos.close(); 
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        
                    }
                }.start();
	        }
        }.start();
	}
	

	private BitmapDrawable loadImageFromUrl(String url) {
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
			
			if(width>0 && height>0){
                Bitmap bm = ImageUtils.extractMiniThumb(d.getBitmap(), width, height, true);
                d = new BitmapDrawable(mContext.getResources(), bm);
            }
			
			
		} catch (Exception e) {
			Log.d("loadImageFromUrl", url+":"+e.getMessage());
		}catch (Error e) {
            
        }
		Log.d("loading images", url+": "+(d!=null ? "ok" : "fail"));
		return d;
	}
	
	public void loadFile(final String fileUrl, final FileLoaded fileCallback) {
	    if(mMemoryCache.containsKey(fileUrl)){
	        mHandler.obtainMessage(LOAD_FILE, new Object[]{mMemoryCache.get(fileUrl), fileCallback}).sendToTarget();
	        return;
	    }
	    new Thread("loadFile"){
	        public void run(){
        	    File cachedFile = cachedFile(fileUrl);
                if (cachedFile != null) {
                	String file_path = cachedFile.getAbsolutePath();
                    mMemoryCache.put(fileUrl, file_path);
                    mHandler.obtainMessage(LOAD_FILE, new Object[]{file_path, fileCallback}).sendToTarget();
                    return;
                }
        
                new Thread("loadFileFromUrl") {
                    @Override
                    public void run() {                  	
                        try {
                            File dirFile = mContext.getExternalCacheDir();
                            if (dirFile == null) {
                            	mHandler.obtainMessage(LOAD_FILE, new Object[]{"", fileCallback}).sendToTarget();                           
                            	return;
                            }
                            
                            if( ! dirFile.exists()){  
                                dirFile.mkdir();  
                            }  
                            String[] splitUrl = fileUrl.split("\\?");
                            int pos = splitUrl[0].lastIndexOf(".");
                            String extName = "";
                            if (pos != -1) extName = splitUrl[0].substring(pos);
                            InputStream inputStream = null;
                            FileOutputStream outStream = null;
                            try {
	                            
	                    		URL u = new URL(fileUrl);         
	                    		URLConnection conn = u.openConnection();
	                    		inputStream = conn.getInputStream();   
	                    		
	                    		File cacheFile = new File(dirFile.getAbsolutePath() + "/" + Util.MD5(fileUrl) + extName);  
	                            outStream = new FileOutputStream(cacheFile);
	                            
                            	int byteread = 0;
                                byte[] buffer = new byte[1024];
                                while ((byteread = inputStream.read(buffer)) != -1) {
                                	outStream.write(buffer, 0, byteread);
                                 }
                                 String file_path = cacheFile.getAbsolutePath();
                                 mHandler.obtainMessage(LOAD_FILE, new Object[]{file_path, fileCallback}).sendToTarget();
                                 mMemoryCache.put(fileUrl, file_path);
                                 
                            } catch (Exception e) {
                                 e.printStackTrace();
                            } finally{
                                try {
                                	inputStream.close();
                                	outStream.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                mHandler.obtainMessage(LOAD_FILE, new Object[]{"", fileCallback}).sendToTarget();
                            }
                            
	                    } catch (Exception e) {
	                        e.printStackTrace();
	                        mHandler.obtainMessage(LOAD_FILE, new Object[]{"", fileCallback}).sendToTarget();
	                    }
                    }
                }.start();
	        }
        }.start();
	}
	
	public interface ImageLoaded {
	    /**
	     * 
	     * @param imageView
	     * @param imageDrawable null表示加载失败
	     */
		public void imageLoaded(ImageView imageView, Drawable imageDrawable);
	}
	
	public interface FileLoaded {
		/**
		 * 加载其它资源文件,比如声音,视频等
		 * @param localFilePath 返回的文件路径,为null表示加载失败
		 */
		public void fileLoaded(String localFilePath);
	}
	
	static class MyHandler extends Handler{
		public void handleMessage(Message message) {
			Object[] datas 	     = (Object[])message.obj;
			if (message.what == LOAD_IMAGE) {
				ImageView imageView  = (ImageView)datas[0];
				Drawable  drawable   = (Drawable)datas[1];
				ImageLoaded  callback  = (ImageLoaded)datas[2];
				
				
				if(callback == null)return;
				callback.imageLoaded(imageView, drawable);
			} else {
				String file_path  = datas[0].toString();			
				FileLoaded callback  = (FileLoaded)datas[1];
				if(callback == null)return;
				callback.fileLoaded(file_path);
			}
		}
	};
}
