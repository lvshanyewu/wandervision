package com.tencent.yolov8ncnn;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.*;
import org.json.*;

/** Explicit HTTP/1.1 TLS chunks: no HTTP/2 request-body buffering. Default trust and hostname checks. */
final class LiveHttpSession {
    private final MonitorApi.Credentials credentials;
    private final boolean test;
    private final String id;
    private final Socket raw=new Socket();
    private volatile SSLSocket tls;
    private volatile boolean cancelled;
    private OutputStream output;
    LiveHttpSession(MonitorApi.Credentials c,boolean test,String id) throws IOException {
        if(c==null||!MonitorApi.validDevice(c.device)||!c.token.matches("[!-~]{8,512}")||!id.matches("[a-f0-9-]{36}"))throw new MonitorApi.Failure(0,"无效实时会话");
        credentials=c;this.test=test;this.id=id;
    }
    private void connect() throws IOException {
        if(cancelled)throw new IOException("Closed stream");
        final String host="lvshanyewu.site";
        raw.connect(new InetSocketAddress(host,443),3000);raw.setSoTimeout(3000);raw.setTcpNoDelay(true);
        tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(raw,host,443,true);
        SSLParameters parameters=tls.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");
        if(android.os.Build.VERSION.SDK_INT>=29)parameters.setApplicationProtocols(new String[]{"http/1.1"});
        tls.setSSLParameters(parameters);tls.startHandshake();
        if(!HttpsURLConnection.getDefaultHostnameVerifier().verify(host,tls.getSession()))throw new SSLPeerUnverifiedException("Hostname verification failed");
        if(cancelled)throw new IOException("Closed stream");
        output=tls.getOutputStream();
        String headers="POST /wandervision/api/v1/devices/"+credentials.device+"/live/stream HTTP/1.1\r\nHost: "+host+"\r\nAuthorization: Bearer "+credentials.token+"\r\nContent-Type: application/x-wandervision-live\r\nTransfer-Encoding: chunked\r\nUser-Agent: wandervision/3.3.0 (Android)\r\nX-WV-Test-Data: "+(test?"1":"0")+"\r\nX-WV-Stream-Id: "+id+"\r\nAccept: application/json\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));output.flush();
    }
    void send(byte[] jpeg) throws IOException {
        if(jpeg.length==0||jpeg.length>262144)throw new MonitorApi.Failure(413,"实时帧超过限制");
        if(output==null)connect();
        output.write((Integer.toHexString(jpeg.length+4)+"\r\n").getBytes(StandardCharsets.US_ASCII));
        MonitorApi.writeLiveFrame(new DataOutputStream(output),jpeg);
        output.write(new byte[]{13,10});output.flush();
    }
    void finish() throws IOException {
        try{
            if(output==null)return;
            output.write("4\r\n".getBytes(StandardCharsets.US_ASCII));new DataOutputStream(output).writeInt(0);
            output.write("\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));output.flush();
            InputStream in=tls.getInputStream();String line=readLine(in);String[] status=line.split(" ");
            if(status.length<2||!status[0].equals("HTTP/1.1"))throw new IOException("Invalid HTTP response");
            int code=Integer.parseInt(status[1]);int total=0,length=-1;boolean chunked=false;
            while(!(line=readLine(in)).isEmpty()){
                total+=line.length();if(total>16384)throw new IOException("Oversized headers");
                int colon=line.indexOf(':');if(colon<0)throw new IOException("Invalid header");
                String key=line.substring(0,colon).trim(),value=line.substring(colon+1).trim();
                if(key.equalsIgnoreCase("Content-Length"))length=Integer.parseInt(value);
                if(key.equalsIgnoreCase("Transfer-Encoding"))chunked=value.equalsIgnoreCase("chunked");
            }
            if(code!=200)throw new MonitorApi.Failure(code,"实时流未被接受");
            ByteArrayOutputStream body=new ByteArrayOutputStream();
            if(chunked){
                for(;;){int count=Integer.parseInt(readLine(in).split(";",2)[0],16);if(count==0)break;copy(in,body,count);if(!readLine(in).isEmpty())throw new IOException("Invalid chunk");}
            }else if(length>=0)copy(in,body,length);else body.write(MonitorApi.read(in,8192));
            try{if(!new JSONObject(body.toString("UTF-8")).optBoolean("ok"))throw new MonitorApi.Failure(0,"实时流未确认");}catch(JSONException e){throw new MonitorApi.Failure(0,"实时流回执格式不正确");}
        }catch(NumberFormatException e){throw new IOException("Invalid HTTP response");}finally{cancel();}
    }
    void cancel(){cancelled=true;try{raw.close();}catch(IOException ignored){}try{if(tls!=null)tls.close();}catch(IOException ignored){}}
    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();int previous=-1;
        for(int c;(c=in.read())!=-1;){if(previous==13&&c==10){byte[] line=bytes.toByteArray();return new String(line,0,line.length-1,StandardCharsets.US_ASCII);}bytes.write(c);previous=c;if(bytes.size()>4096)throw new IOException("Oversized line");}
        throw new EOFException("Missing HTTP line");
    }
    static void copy(InputStream in,ByteArrayOutputStream out,int count) throws IOException {
        if(count<0||count>8192-out.size())throw new IOException("Oversized response");
        byte[] buffer=new byte[1024];while(count>0){int n=in.read(buffer,0,Math.min(count,buffer.length));if(n<0)throw new EOFException();out.write(buffer,0,n);count-=n;}
    }
}
