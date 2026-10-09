package com.example.books;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.util.*;

public class BookDbHelper extends SQLiteOpenHelper {
    public static class Book {
        public long id; public String title,author,category,path,status,notes; public int favorite,progress,rating;
    }
    public BookDbHelper(Context c){super(c,"books.db",null,1);}
    public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE books(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,author TEXT DEFAULT '',category TEXT DEFAULT 'Uncategorized',path TEXT NOT NULL,status TEXT DEFAULT 'Unread',favorite INTEGER DEFAULT 0,progress INTEGER DEFAULT 0,rating INTEGER DEFAULT 0,notes TEXT DEFAULT '')");}
    public void onUpgrade(SQLiteDatabase d,int o,int n){}
    public long add(String title,String path){ContentValues v=new ContentValues();v.put("title",title);v.put("path",path);return getWritableDatabase().insert("books",null,v);}
    public List<Book> all(String query,String filter){
        List<Book> out=new ArrayList<>();String q="%"+(query==null?"":query.trim())+"%";
        String w="(title LIKE ? OR author LIKE ? OR category LIKE ?)";List<String>a=new ArrayList<>(Arrays.asList(q,q,q));
        if("Favorites".equals(filter))w+=" AND favorite=1";else if(Arrays.asList("Unread","Reading","Finished").contains(filter)){w+=" AND status=?";a.add(filter);}
        Cursor c=getReadableDatabase().query("books",null,w,a.toArray(new String[0]),null,null,"title COLLATE NOCASE");
        try{while(c.moveToNext()){Book b=new Book();b.id=c.getLong(c.getColumnIndexOrThrow("id"));b.title=c.getString(c.getColumnIndexOrThrow("title"));b.author=c.getString(c.getColumnIndexOrThrow("author"));b.category=c.getString(c.getColumnIndexOrThrow("category"));b.path=c.getString(c.getColumnIndexOrThrow("path"));b.status=c.getString(c.getColumnIndexOrThrow("status"));b.favorite=c.getInt(c.getColumnIndexOrThrow("favorite"));b.progress=c.getInt(c.getColumnIndexOrThrow("progress"));b.rating=c.getInt(c.getColumnIndexOrThrow("rating"));b.notes=c.getString(c.getColumnIndexOrThrow("notes"));out.add(b);}}finally{c.close();}return out;
    }
    public void update(Book b){ContentValues v=new ContentValues();v.put("title",b.title);v.put("author",b.author);v.put("category",b.category);v.put("status",b.status);v.put("favorite",b.favorite);v.put("progress",b.progress);v.put("rating",b.rating);v.put("notes",b.notes);getWritableDatabase().update("books",v,"id=?",new String[]{""+b.id});}
    public void delete(long id){getWritableDatabase().delete("books","id=?",new String[]{""+id});}
    public int count(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM books",null);try{c.moveToFirst();return c.getInt(0);}finally{c.close();}}
}
