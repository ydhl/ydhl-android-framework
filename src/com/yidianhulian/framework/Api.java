package com.yidianhulian.framework;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class Api {
    /**
     * Post数据格式
     * @author leeboo
     *
     */
    public enum PostDataType {
        /**
         * 普通表单，key=value的形式
         */
        FORM,
        /**
         * JSON结构体，参数作为一个json数据包发送
         */
        JSON,
        /**
         * XML结构体，参数作为一个xml数据包发送
         */
        XML
    }
	public static final String BOUNDARY = "-----------AndroidFormBoundar7d4a6d158c9";
	private String mApi;
	private Map<String, String> mQueryStr;
	private String mJSONData;
	private PostDataType mPostDataType = PostDataType.FORM;
	private List<String> mFiles;
	private String mMethod;
	private ApiCallback mCallback;
	private SSLContext mSSL;
	private Map<String, String> mHeaders;
	private int mHttpCode;
	
	public Api(String method, String api, Map<String, String> queryStr, List<String> files){
	    this.mApi = api;
	    this.mQueryStr = queryStr;
	    this.mFiles = files;
	    this.mMethod = method;
	    mHeaders = new HashMap<String,String>();
	    
	    if(mApi.startsWith("https")){
	        System.out.println("is https");
	        try {
                mSSL = SSLContext.getInstance("TLS");
                mSSL.init(null, new TrustManager[]{new MyTrustManager()}, new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(mSSL.getSocketFactory());
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            }  
    	    
	    }
          
	}
	public Api(String method, String api, Map<String, String> queryStr){
        this(method, api, queryStr, null);
    }
	
	/**
	 * Post 一个原始json字符串, 这时putArg不起作用
	 * 
	 * @param method
	 * @param api
	 * @param jsonString 格式化的json字符串
	 */
	public Api(String method, String api, String jsonString){
        this(method, api, new HashMap<String, String>(), null);
        mPostDataType = PostDataType.JSON;
        mJSONData = jsonString;
    }
	
	public void addHeader(String header, String value){
	    mHeaders.put(header, value);
	}
	
	public JSONObject invoke() throws NetworkException{
	    if("post".equalsIgnoreCase(mMethod))return this.post();
	    if("put".equalsIgnoreCase(mMethod))return this.put();
	    if("delete".equalsIgnoreCase(mMethod))return this.delete();
        return this.get();
	}
	
	public void putArg(String key, String value){
	    if(mQueryStr==null){
	        mQueryStr = new HashMap<String, String>();
	    }
	    mQueryStr.put(key, value);
	}
	
	public JSONObject invoke(ApiCallback callback) throws NetworkException{
	    mCallback = callback;
        return invoke();
    }
	public JSONObject put() throws NetworkException{ 
	    return request("PUT");
	}
	public JSONObject delete() throws NetworkException{ 
	    return request2("DELETE");
	}
	
	private JSONObject request(String requestMethod) throws NetworkException{ 
	    JSONObject json = null;
        boolean hasFile = false;
        String end_data = "\r\n--" + BOUNDARY + "--\r\n";
        if (mFiles != null) {
            hasFile = true;
        }
        StringBuffer contentBuffer = new StringBuffer();

        try{
            URL postUrl = new URL(mApi);

            HttpURLConnection connection = (HttpURLConnection) postUrl.openConnection();
            if(mSSL!=null){
                ((HttpsURLConnection)connection).setHostnameVerifier(new MyHostnameVerifier()); 
            }
            
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod(requestMethod);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            if (hasFile) {
                connection.setRequestProperty("Content-type", "multipart/form-data; boundary=" + BOUNDARY);
                connection.setRequestProperty("Charset", "UTF-8");
            } else if(mPostDataType==PostDataType.JSON) {
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            }else if(mPostDataType==PostDataType.XML) {
                connection.setRequestProperty("Content-Type", "application/xml; charset=utf-8");
            }else{
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            }

            for (Entry<String, String> header : mHeaders.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            
            connection.connect();
            
            if(mPostDataType==PostDataType.JSON){
                sendJsonPostData(end_data, connection);
            }else if(mPostDataType==PostDataType.XML){
                //TODO
            }else{
                sendPostData(hasFile, end_data, connection);
            }

            mHttpCode = connection.getResponseCode();
            try{
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), "utf-8"));
                String inputLine = null;
                
                while ((inputLine = reader.readLine()) != null) {
                    contentBuffer.append(inputLine);
                }
            }catch(Exception e){
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getErrorStream(), "utf-8"));
                String inputLine = null;
                if(reader != null){
                    while ((inputLine = reader.readLine()) != null) {
                        contentBuffer.append(inputLine);
                    }
                }
                e.printStackTrace();
            }finally {
                connection.disconnect();
            }
            json = new JSONObject(contentBuffer.toString());
        }catch(IOException ioe){
            throw new NetworkException(ioe);
        }catch (Exception e) {
            System.out.println(contentBuffer.toString());
            e.printStackTrace();
        }
        
        return json;
	}
    private void sendPostData(boolean hasFile, String end_data,
            HttpURLConnection connection) throws IOException,
            UnsupportedEncodingException, FileNotFoundException {
        DataOutputStream out = new DataOutputStream(connection.getOutputStream());

        StringBuffer buffer = new StringBuffer();
        Iterator<Entry<String, String>> iterator = mQueryStr.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, String> entry = iterator.next();
            if (hasFile && mFiles.contains(entry.getKey())) {
                continue;
            }
            String value = entry.getValue();
            

            if (hasFile) {
                buffer.append("--");
                buffer.append(BOUNDARY);
                buffer.append("\r\n");
                buffer.append("Content-Disposition: form-data; name=\"");
                buffer.append(entry.getKey());

                buffer.append("\"\r\n\r\n");
                buffer.append(URLEncoder.encode(value, "utf-8"));
                buffer.append("\r\n");
            } else {
                value = value != null ? URLEncoder.encode(value, "utf-8") : "";
                buffer.append(entry.getKey()).append("=").append(value).append("&");
            }
        }
        System.out.println(mApi);
        System.out.println(buffer);
        if(hasFile){
            out.writeBytes(buffer.toString());
        }else{
            if(buffer.length()>0){//remove end &
                out.writeBytes(buffer.substring(0, buffer.length()-1));
            }else{
                out.writeBytes(buffer.toString());
            }
        }


        if (hasFile) {
            long totalSize = 0l;
            for (int i = 0; i < mFiles.size(); i++) {
                String fname = mQueryStr.get(mFiles.get(i));
                File file = new File(fname);
                if (file.exists()) {
                    FileInputStream fis = new FileInputStream(file);
                    totalSize += fis.available();
                    fis.close();
                }
            }
            
            for (int i = 0; i < mFiles.size(); i++) {
                String fname = mQueryStr.get(mFiles.get(i));
                File file = new File(fname);
                StringBuilder sb = new StringBuilder();
                sb.append("--");
                sb.append(BOUNDARY);
                sb.append("\r\n");
                sb.append("Content-Disposition: form-data; name=\"");
                sb.append(mFiles.get(i));
                sb.append("\"; filename=\"");
                sb.append(file.getName());
                sb.append("\"\r\n");
                sb.append("Content-Type: application/octet-stream\r\n\r\n");

                out.writeBytes(sb.toString());
                DataInputStream in = new DataInputStream(new FileInputStream(file));
                int bytes = 0;
                long sendedSize = 0;
                byte[] bufferOut = new byte[1024];
                while ((bytes = in.read(bufferOut)) != -1) {
                    out.write(bufferOut, 0, bytes);
                    out.flush();
                    sendedSize += bytes;
                    if(mCallback!=null)mCallback.updateApiProgress((float)sendedSize / (float)totalSize);
                }
                out.writeBytes("\r\n");
                
                in.close();
            }
            out.writeBytes(end_data);
        }

        out.flush();
        out.close(); // flush and close
    }
    
    private void sendJsonPostData(String end_data,HttpURLConnection connection) throws IOException,
            UnsupportedEncodingException, FileNotFoundException {
        DataOutputStream out = new DataOutputStream(connection.getOutputStream());
        System.out.println(mApi);
        System.out.println("raw JSON:"+mJSONData);
        out.writeBytes(mJSONData);
        out.flush();
        out.close(); // flush and close
    }

	public int httpCode(){
	    return mHttpCode;
	}
	
	public JSONObject post() throws NetworkException{
		return request("POST");
	}

	public JSONObject get() throws NetworkException{
		return request2("GET");
	}
	
	private JSONObject request2(String  method) throws NetworkException{
	    java.net.URL url;
        JSONObject json = null;
        StringBuffer contentBuffer = new StringBuffer();
        StringBuffer buffer = new StringBuffer();
        Iterator<Entry<String, String>> iterator = mQueryStr.entrySet()
                .iterator();
        while (iterator.hasNext()) {
            Entry<String, String> entry = iterator.next();
            String value = entry.getValue();
            if (value != null) {
                try {
                    value = URLEncoder.encode(value, "utf-8");
                }catch(Exception e){}
            } else {
                value = "";
            }
            buffer.append(entry.getKey()).append("=").append(value)
                    .append("&");
        }
        
        try {
            if(mApi.indexOf("?") != -1){
                String[] pathAndArgs = mApi.split("\\?");
                if(pathAndArgs.length==1){
                    url = new java.net.URL(pathAndArgs[0]+"?"+buffer);
                }else{
                    url = new java.net.URL(pathAndArgs[0]+"?"+buffer+"&"+pathAndArgs[1]);
                }
            }else{
                url = new java.net.URL(mApi+"?"+buffer);
            }
            Log.d("get", url.toString());
            
            java.net.HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            
            if(mSSL!=null){
                ((HttpsURLConnection)conn).setHostnameVerifier(new MyHostnameVerifier()); 
            }
            
            for (Entry<String, String> header : mHeaders.entrySet()) {
                conn.setRequestProperty(header.getKey(), header.getValue());
            }
            
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.connect();

            mHttpCode = conn.getResponseCode();
            
            try{
                java.io.InputStream is = conn.getInputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, "UTF-8"));
                String inputLine = null;
                while ((inputLine = reader.readLine()) != null) {
                    contentBuffer.append(inputLine);
                }
                is.close();
                
            }catch(Exception e){
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        conn.getErrorStream(), "utf-8"));
                String inputLine = null;
                if(reader != null){
                    while ((inputLine = reader.readLine()) != null) {
                        contentBuffer.append(inputLine);
                    }
                }
                e.printStackTrace();
            }finally {
                conn.disconnect();
            }
            json = new JSONObject(contentBuffer.toString());
        } catch(IOException ioe){
            throw new NetworkException(ioe);
        }catch (Exception e) {
            Log.d("get-api-exception", contentBuffer.toString());
            e.printStackTrace();
        }
        return json;
	}


	
    public static Object getJSONValue(JSONObject json, String name){
        if(json==null)return null;
        try {
            return json.get(name);
        } catch (JSONException e) {
            return null;
        }
        
    }
    public static Integer getIntegerValue(JSONObject json, String name){
        Object o = Api.getJSONValue(json, name);
        if(o==null)return 0;
        try{
            return Integer.valueOf(String.valueOf(o));
        }catch(Exception e){
            return 0;
        }
    }
    public static String getStringValue(JSONObject json, String name){
        Object o = Api.getJSONValue(json, name);
        if(o==null)return null;
        return String.valueOf(o);
    }
    
    /**
     * 在明确知道返回值类型时使用
     * 
     * @param json
     * @param name
     * @param t
     * @return
     */
    public static <T> T getJSONValue(JSONObject json, String name, Class<T> t){
        Object o = Api.getJSONValue(json, name);
        if(o==null)return null;
        
        try {
            @SuppressWarnings("unchecked")
            T o2 = (T)o;

            return o2;
          
        } catch (Exception e1) {
            return null;
        }
    }
    
    class NetworkException extends Exception{
        private static final long serialVersionUID = 1L;

        public NetworkException(Throwable throwable) {
            super(throwable);
        }
        
    }
    
    interface ApiCallback{
        /**
         * 更新进度
         * 
         * @param percent
         */
        public void updateApiProgress(float percent);
    }
    
    private class MyHostnameVerifier implements HostnameVerifier{  
        
        @Override  
        public boolean verify(String hostname, SSLSession session) {  
//            System.out.println("Warning: URL Host: " + hostname + " vs. "  
//                    + session.getPeerHost());
                return true;  
        }  
    }  

    private class MyTrustManager implements X509TrustManager{  

        @Override  
        public void checkClientTrusted(X509Certificate[] chain, String authType)  
                        throws CertificateException {  
        }  

        @Override  
        public void checkServerTrusted(X509Certificate[] chain, String authType)  
                        throws CertificateException {  
//            System.out.println("cert: " + chain[0].toString() + ", authType: "  
//                    + authType); 
        }  

        @Override  
        public X509Certificate[] getAcceptedIssuers() {  
            return new X509Certificate[] {};
        }          
}  
}
