package it.maleduclasse.audioflow;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Trusted LAN only; microphone recordings always remain available offline. */
final class MobileSync {
  private final Context context;
  private final MobileStore store;
  private final SharedPreferences prefs;
  MobileSync(Context ctx,MobileStore db){context=ctx;store=db;prefs=ctx.getSharedPreferences("sync",0);}
  String url(){return prefs.getString("url","");}
  String token(){return prefs.getString("token","");}
  void setup(String url,String token){if(!url.matches("http://(192\\.168\\.\\d+\\.\\d+|10\\.\\d+\\.\\d+\\.\\d+|172\\.(1[6-9]|2[0-9]|3[01])\\.\\d+\\.\\d+):18766"))throw new IllegalArgumentException("Inserisci IP Wi-Fi privato del PC e porta 18766");if(token.trim().length()<16)throw new IllegalArgumentException("Codice di abbinamento non valido");prefs.edit().putString("url",url).putString("token",token.trim()).apply();}
  String deviceId(){String id=prefs.getString("device_id","");if(id.isEmpty()){id=UUID.randomUUID().toString();prefs.edit().putString("device_id",id).apply();}return id;}
  private HttpURLConnection connect(String path,String method) throws Exception {if(url().isEmpty())throw new IOException("Collega prima il telefono al PC");HttpURLConnection con=(HttpURLConnection)new URL(url()+path).openConnection();con.setRequestMethod(method);con.setRequestProperty("X-MAF-Key",token());con.setConnectTimeout(7500);con.setReadTimeout(40000);con.setUseCaches(false);return con;}
  private static byte[] contents(InputStream in)throws Exception{try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=source.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
  private static JSONObject response(HttpURLConnection conn)throws Exception{int status=conn.getResponseCode();String body=new String(contents(status<400?conn.getInputStream():conn.getErrorStream()),StandardCharsets.UTF_8);if(status>=400)throw new IOException("PC HTTP "+status+": "+(body.length()>260?body.substring(0,260):body));return new JSONObject(body);}
  JSONObject get(String path)throws Exception{HttpURLConnection conn=connect(path,"GET");try{return response(conn);}finally{conn.disconnect();}}
  JSONObject post(String path,JSONObject payload)throws Exception{HttpURLConnection conn=connect(path,"POST");try{conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=conn.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}return response(conn);}finally{conn.disconnect();}}
  JSONObject state()throws Exception{return get("/api/sync/state");}
  JSONObject download(String pid)throws Exception{return get("/api/sync/projects/"+pid+"/full");}
  static String sha256(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}StringBuilder out=new StringBuilder();for(byte x:digest.digest())out.append(String.format(Locale.ROOT,"%02x",x&255));return out.toString();}
  private void field(OutputStream out,String boundary,String name,String value)throws Exception{String s="--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n";out.write(s.getBytes(StandardCharsets.UTF_8));}
  void upload(String pid,String lid,File file,boolean provisional,boolean preferred)throws Exception{
    HttpURLConnection con=connect("/api/sync/projects/"+pid+"/takes","POST");String boundary="MAF"+System.nanoTime();con.setDoOutput(true);con.setChunkedStreamingMode(65536);con.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
    try{try(OutputStream out=con.getOutputStream()){
      field(out,boundary,"line_id",lid);field(out,boundary,"device_id",deviceId());field(out,boundary,"upload_id",lid+"_"+file.getName());field(out,boundary,"sha256",sha256(file));field(out,boundary,"provisional",String.valueOf(provisional));field(out,boundary,"preferred",String.valueOf(preferred));
      String head="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+file.getName().replaceAll("[^a-zA-Z0-9_.-]","_")+"\"\r\nContent-Type: application/octet-stream\r\n\r\n";out.write(head.getBytes(StandardCharsets.UTF_8));
      try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
    }response(con);}finally{con.disconnect();}
  }
  static final class Result{int projects,takes,assembled,receivedProjects,receivedTakes;
    String message(){return projects+" progetti inviati, "+takes+" take inviate, "+receivedProjects+" progetti ricevuti, "+receivedTakes+" take ricevute";}}

  /** Pull changes only into clean local projects. Dirty drafts are never overwritten. */
  private int pullProjects()throws Exception{
    JSONObject remote=state();JSONArray list=remote.optJSONArray("projects");
    if(list==null)return 0;
    boolean hasDirty=false;
    for(JSONObject local:store.projects())if(local.optBoolean("dirty",false)){hasDirty=true;break;}
    if(hasDirty)return 0;
    int changed=0;
    for(int i=0;i<list.length();i++){
      JSONObject r=list.optJSONObject(i);if(r==null)continue;
      String id=r.optString("id","");
      JSONObject local=store.project(id);
      if(local.optBoolean("dirty",false))continue;
      String localId=MobileStore.str(local,"project_id");
      if(localId.isEmpty()||r.optInt("revision",0)>local.optInt("revision",-1)){
        store.importFromPC(download(id));changed++;
      }
    }
    JSONObject remoteCast=MobileStore.obj();
    MobileStore.put(remoteCast,"actors",remote.optJSONArray("actors")==null?MobileStore.array():remote.optJSONArray("actors"));
    MobileStore.put(remoteCast,"characters",remote.optJSONArray("characters")==null?MobileStore.array():remote.optJSONArray("characters"));
    JSONObject localCast=store.cast();
    if(!normalizedCast(remoteCast).equals(normalizedCast(localCast))){
      store.applyRemoteCast(remoteCast);changed++;
    }
    return changed;
  }
  private static String normalizedCast(JSONObject cast){
    ArrayList<String> result=new ArrayList<>();
    for(String kind:new String[]{"actors","characters"}){
      JSONArray list=cast.optJSONArray(kind);
      if(list==null)continue;
      for(int i=0;i<list.length();i++){
        JSONObject o=list.optJSONObject(i);if(o!=null)result.add(kind+"|"+o.optString("id")+"|"+o.optString("name")+"|"+o.optString("actor_id"));
      }
    }
    Collections.sort(result);return result.toString();
  }
  private int pullTakes()throws Exception{
    int downloaded=0;
    for(JSONObject project:store.projects()){
      String pid=MobileStore.str(project,"project_id");
      JSONObject info=get("/api/sync/projects/"+pid+"/take-list");
      JSONArray files=info.optJSONArray("takes");if(files==null)continue;
      for(int i=0;i<files.length();i++){
        JSONObject t=files.optJSONObject(i);if(t==null)continue;
        String lid=t.optString("line_id",""),tid=t.optString("id","");
        if(tid.length()!=16||lid.length()!=16)continue;
        if(deviceId().equals(t.optString("device_id","")))continue;
        String extension=t.optString("extension",".m4a");
        if(!extension.matches("\\.(wav|mp3|m4a|aac|ogg|flac)"))continue;
        File dest=new File(store.takeDir(pid,lid),"pc_"+tid+extension);
        if(dest.exists())continue;
        File temp=new File(dest.getParentFile(),dest.getName()+".download");
        HttpURLConnection con=connect("/api/sync/projects/"+pid+"/take-file/"+tid,"GET");
        try{
          if(con.getResponseCode()!=200)throw new IOException("Download take PC: HTTP "+con.getResponseCode());
          String checksum=con.getHeaderField("X-MAF-SHA256");
          if(checksum==null||!checksum.matches("[a-f0-9]{64}"))throw new IOException("Checksum audio PC assente");
          long length=0;
          try(InputStream in=con.getInputStream();FileOutputStream out=new FileOutputStream(temp)){
            byte[] buffer=new byte[65536];int n;
            while((n=in.read(buffer))!=-1){
              length+=n;if(length>200_000_000L)throw new IOException("Audio troppo grande");
              out.write(buffer,0,n);
            }
          }
          if(length==0||!sha256(temp).equals(checksum))throw new IOException("Integrità take PC non valida");
          if(!temp.renameTo(dest))throw new IOException("Impossibile salvare take PC");
          downloaded++;
          if(t.optBoolean("provisional",false))store.setProvisional(pid,lid,dest.getName(),true);
          JSONObject meta=store.takeMeta(pid,lid);
          if(t.optBoolean("preferred",false)&&meta.optString("preferred","").isEmpty())store.setPreferred(pid,lid,dest.getName());
        }finally{con.disconnect();if(temp.exists())temp.delete();}
      }
    }
    return downloaded;
  }
  Result synchronizeAll()throws Exception{
    Result result=new Result();
    for(JSONObject project:store.projects()){
      String pid=MobileStore.str(project,"project_id");
      if(project.optBoolean("dirty",false)){
        JSONObject payload=MobileStore.obj();MobileStore.put(payload,"project_id",pid);MobileStore.put(payload,"title",MobileStore.str(project,"title"));MobileStore.put(payload,"script",MobileStore.str(project,"script"));MobileStore.put(payload,"base_revision",project.optInt("revision",0));
        JSONObject cast=store.cast();MobileStore.put(payload,"actors",cast.optJSONArray("actors"));MobileStore.put(payload,"characters",cast.optJSONArray("characters"));
        MobileStore.put(payload,"overrides",project.optJSONArray("overrides")==null?MobileStore.array():project.optJSONArray("overrides"));MobileStore.put(payload,"lines",project.optJSONArray("lines")==null?MobileStore.array():project.optJSONArray("lines"));
        JSONObject accepted=post("/api/sync/projects/upsert",payload);
        MobileStore.put(project,"revision",accepted.getInt("revision"));MobileStore.put(project,"dirty",false);store.saveProject(project);result.projects++;
      }
      int changesBefore=result.projects+result.takes;
      // Deletions first, so a deleted take cannot reappear as an older preferred.
      JSONArray deleted=project.optJSONArray("deleted_takes");
      if(deleted!=null){for(int i=0;i<deleted.length();i++){
        JSONObject row=deleted.optJSONObject(i);if(row==null)continue;String uploadId=MobileStore.str(row,"line_id")+"_"+MobileStore.str(row,"file_name");
        JSONObject request=MobileStore.obj();MobileStore.put(request,"device_id",deviceId());MobileStore.put(request,"upload_id",uploadId);
        post("/api/sync/projects/"+pid+"/take-delete",request);
      }MobileStore.put(project,"deleted_takes",MobileStore.array());store.saveProject(project);}
      JSONObject response=get("/api/sync/projects/"+pid+"/uploads?device_id="+URLEncoder.encode(deviceId(),"UTF-8"));
      HashSet<String> known=new HashSet<>();JSONArray ids=response.optJSONArray("upload_ids");if(ids!=null)for(int i=0;i<ids.length();i++)known.add(ids.optString(i));
      JSONArray lines=project.optJSONArray("lines");if(lines!=null)for(int i=0;i<lines.length();i++){
        JSONObject line=lines.optJSONObject(i);if(line==null)continue;String lid=MobileStore.str(line,"id");
        JSONObject meta=store.takeMeta(pid,lid);String preferred=meta.optString("preferred","");
        for(File f:store.takes(pid,lid)){
          if(f.getName().startsWith("pc_"))continue; // PC-origin files are already on the server
          String uploadId=lid+"_"+f.getName();boolean isPreferred=f.getName().equals(preferred);boolean provisional=store.isProvisional(pid,lid,f.getName());
          if(!known.contains(uploadId)){upload(pid,lid,f,provisional,isPreferred);known.add(uploadId);result.takes++;}
          JSONObject state=MobileStore.obj();MobileStore.put(state,"device_id",deviceId());MobileStore.put(state,"upload_id",uploadId);MobileStore.put(state,"preferred",isPreferred);MobileStore.put(state,"provisional",provisional);
          String stateKey="take_state_"+pid+"_"+uploadId;
          String signature=isPreferred+":"+provisional;
          if(!signature.equals(prefs.getString(stateKey,""))){
            post("/api/sync/projects/"+pid+"/take-state",state);
            prefs.edit().putString(stateKey,signature).apply();
          }
        }
      }
      if(result.projects+result.takes>changesBefore){
        JSONObject assembled=post("/api/sync/projects/"+pid+"/assemble",MobileStore.obj());
        result.assembled+=assembled.optInt("inserted",0);
      }
    }
    result.receivedProjects=pullProjects();
    result.receivedTakes=pullTakes();
    return result;
  }
}