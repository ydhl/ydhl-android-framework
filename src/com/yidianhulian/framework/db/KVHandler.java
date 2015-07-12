package com.yidianhulian.framework.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
/**
 * key-value 数据库存储处理
 * 
 * @author leeboo
 *
 */
public class KVHandler extends SQLiteOpenHelper {
    private int DATABASE_VERSION = 0;
    private String TABLE_NAME = "";
    private String TABLE_CREATE = "";
    private String UPGRADE_CREATE ="";
    private SQLiteDatabase mDb;
    
	public KVHandler(Context context, String name,
			CursorFactory factory, int version) {
		super(context, name, factory, version);
		init(name, version);
		mDb = getWritableDatabase();
	}

	protected void init(String dbname, int version) {
	    DATABASE_VERSION = version;
	    TABLE_NAME = dbname;
	    TABLE_CREATE ="CREATE TABLE " + TABLE_NAME + " (" +
	                        BaseColumns._ID + " INTEGER, " +
	                " name VARCHAR(45)," +
	                " value TEXT);";
	    UPGRADE_CREATE ="CREATE TABLE " + TABLE_NAME + " (" +
	                    BaseColumns._ID + " INTEGER, " +
	            " name VARCHAR(45)," +
	            " value TEXT);";
    }

    @Override
	public void onCreate(SQLiteDatabase db) {
		 db.execSQL(TABLE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
//		if(newVer > oldVer){
//			db.execSQL(UPGRADE_CREATE);
//		}
	}

	
	@Override
    public synchronized void close() {
        super.close();
        if(mDb !=null && mDb.isOpen()) mDb.close();
        mDb=null;
    }

    /**
	 * 返回name的所有的值
	 * 
	 * @param context
	 * @param name
	 * @return
	 */
	public String getValue(String name){
		Cursor cursor = mDb.query(TABLE_NAME, new String[]{BaseColumns._ID, "name","value"}, "name=?", new String[]{name}, null, null, null);
		
		String value=null;
		while (cursor.moveToNext()) {  
			value = cursor.getString(cursor.getColumnIndex("value"));  
        }
		cursor.close();
		
		return value;
	}
	
	/**
	 * 增加一个name的value,如果name存在，则更新
	 * 
	 * @param context
	 * @param name
	 * @param value
	 */
	public void setValue(String name, String value){
	    
	    ContentValues cv = new ContentValues();
        cv.put("value", value);
        cv.put("name", name);
        
		String existValue = getValue(name);
		if(existValue!=null){
		    mDb.update(TABLE_NAME, cv, "name=?", new String[]{name});
			return;
		}
		
		mDb.insert(TABLE_NAME, null, cv);
	}
	
	public void removeKey(String name){
	    mDb.delete(TABLE_NAME, "name=?", new String[]{name});
	}
}
