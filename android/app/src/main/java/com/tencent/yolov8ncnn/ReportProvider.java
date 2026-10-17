package com.tencent.yolov8ncnn;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Only explicitly granted, read-only JSON reports are exposed to system sharing. */
public final class ReportProvider extends ContentProvider {
    private File report(Uri uri) throws FileNotFoundException {
        String name=uri.getLastPathSegment();
        if(!"/reports/".concat(name==null?"":name).equals(uri.getPath())||name==null||!name.matches("[a-f0-9\\-]{36}\\.json"))throw new FileNotFoundException("Invalid report");
        File f=new File(getContext().getFilesDir(),"reports/"+name);if(!f.isFile())throw new FileNotFoundException("Report missing");return f;
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "application/json";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(report(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try {File f=report(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?f.getName():OpenableColumns.SIZE.equals(columns[i])?f.length():null;cursor.addRow(row);return cursor;}catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
