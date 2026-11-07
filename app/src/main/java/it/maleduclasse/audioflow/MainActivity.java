package it.maleduclasse.audioflow;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    static final int PURPLE=Color.rgb(141,83,202), DARK=Color.rgb(30,24,39);
    final ArrayList<JSONObject> lines=new ArrayList<>();
    JSONObject episode=null;
    LinearLayout screen, listing;
    TextView title, status;
    Spinner filter;
    MediaRecorder recorder;
    File recording;
    MediaPlayer player;
    String recordingLine="";
    boolean provisional=false, syncing=false;
    final Handler handler=new Handler();
    final String[] extensions={".m4a",".wav",".mp3",".aac",".ogg",".flac"};

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        try {
            File saved=new File(getFilesDir(),"episode.json");
            if(saved.exists())loadEpisode(new String(read(new FileInputStream(saved)),StandardCharsets.UTF_8));
        } catch(Exception e){toast("Impossibile caricare il progetto: "+e.getMessage());}
        render();
    }
    String pref(String k){return getPreferences(0).getString(k,"");}
    void setting(String k,String v){getPreferences(0).edit().putString(k,v).apply();}
    String deviceId(){String id=pref("device_id");if(id.isEmpty()){id=UUID.randomUUID().toString();setting("device_id",id);}return id;}
    void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(12,12,12,12);return t;}
    Button btn(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->action.run());return b;}
    void background(Runnable r){new Thread(r,"maf-sync").start();}
    byte[] read(InputStream in)throws IOException {try(InputStream is=in;ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=is.read(b))!=-1)o.write(b,0,n);return o.toByteArray();}}
    void loadEpisode(String raw)throws Exception {
        JSONObject obj=new JSONObject(raw);
        JSONArray data=obj.getJSONArray("lines");
        ArrayList<JSONObject> tmp=new ArrayList<>();
        for(int i=0;i<data.length();i++)tmp.add(data.getJSONObject(i));
        episode=obj;lines.clear();lines.addAll(tmp);
    }
    void render(){
        ScrollView scroll=new ScrollView(this);
        screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);screen.setPadding(12,16,12,22);screen.setBackgroundColor(DARK);
        scroll.addView(screen);setContentView(scroll);
        screen.addView(text("MALEDUCLASSE  •  AUDIO FLOW",21));
        title=text(episode==null?"Nessun episodio scaricato":episode.optString("title","Episodio"),19);screen.addView(title);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(btn("⚙ PC",this::pair),new LinearLayout.LayoutParams(0,-2,1));
        row.addView(btn("Episodi",this::chooseEpisode),new LinearLayout.LayoutParams(0,-2,1));
        screen.addView(row);
        screen.addView(btn("⇄ Sincronizza registrazioni con PC",this::sync));
        status=text("Le registrazioni restano sul telefono finché non sono state trasferite.",12);screen.addView(status);
        if(episode==null){screen.addView(text("Collega il PC sulla stessa rete Wi-Fi e seleziona un episodio.",16));return;}
        LinkedHashSet<String> actors=new LinkedHashSet<>();actors.add("Tutti");
        for(JSONObject x:lines)actors.add(x.optString("actor","Non assegnato"));
        filter=new Spinner(this);
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new ArrayList<>(actors));
        filter.setAdapter(a);
        screen.addView(text("Doppiatore",13));screen.addView(filter);
        listing=new LinearLayout(this);listing.setOrientation(LinearLayout.VERTICAL);screen.addView(listing);
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){renderLines();}
        });
        renderLines();
    }
    File takeDir(String id){File d=new File(new File(new File(getFilesDir(),"recordings"),episode.optString("project_id","demo")),id);d.mkdirs();return d;}
    boolean isAudio(File f){if(!f.isFile())return false;String name=f.getName().toLowerCase(Locale.ROOT);for(String x:extensions)if(name.endsWith(x))return true;return false;}
    File[] takes(String id){File[] arr=takeDir(id).listFiles(this::isAudio);if(arr==null)return new File[0];Arrays.sort(arr,Comparator.comparing(File::getName));return arr;}
    void renderLines(){
        if(listing==null||filter==null)return;listing.removeAllViews();
        String who=String.valueOf(filter.getSelectedItem());
        for(JSONObject l:lines){
            if(!who.equals("Tutti")&&!who.equals(l.optString("actor","Non assegnato")))continue;
            String id=l.optString("id");
            int n=takes(id).length;
            LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setPadding(8,8,8,8);
            cell.setBackgroundColor(n>0?Color.rgb(28,91,60):Color.rgb(115,37,42));
            cell.addView(text(l.optString("character")+" · "+n+" take",14));
            cell.addView(text(l.optString("text"),17));
            cell.addView(btn("Apri battuta / Registra",()->openLine(l)));
            listing.addView(cell);
            View gap=new View(this);gap.setMinimumHeight(8);listing.addView(gap);
        }
    }
    void openLine(JSONObject l){
        String id=l.optString("id");
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(18,8,18,8);
        panel.addView(text(l.optString("character")+"\n"+l.optString("text"),17));
        CheckBox temporary=new CheckBox(this);temporary.setText("Take provvisoria");temporary.setTextColor(Color.WHITE);panel.addView(temporary);
        panel.addView(btn("● Registra nuova take",()->{provisional=temporary.isChecked();start(id);}));
        panel.addView(btn("■ Ferma registrazione",this::stop));
        String favorite=pref("favorite_"+id);
        for(File f:takes(id)){
            LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.VERTICAL);
            bar.addView(text((f.getName().equals(favorite)?"★ ":"")+f.getName(),12));
            LinearLayout controls=new LinearLayout(this);
            controls.addView(btn("▶",()->play(f)),new LinearLayout.LayoutParams(0,-2,1));
            controls.addView(btn("★",()->{setting("favorite_"+id,f.getName());toast("Take preferita aggiornata");}),new LinearLayout.LayoutParams(0,-2,1));
            controls.addView(btn("Elimina",()->new AlertDialog.Builder(this).setTitle("Eliminare questa take?")
                 .setMessage("La registrazione locale sarà eliminata definitivamente.")
                 .setPositiveButton("Elimina",(d,w)->{if(f.delete()){toast("Take eliminata");renderLines();}else toast("Impossibile eliminare");})
                 .setNegativeButton("Annulla",null).show()),new LinearLayout.LayoutParams(0,-2,2));
            bar.addView(controls);panel.addView(bar);
        }
        ScrollView wrap=new ScrollView(this);wrap.addView(panel);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Doppiaggio").setView(wrap).setPositiveButton("Chiudi",(d,w)->renderLines()).create();
        dialog.show();
    }
    void start(String line){
        if(recorder!=null){toast("Registrazione già in corso");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},41);
            toast("Autorizza il microfono, poi premi Registra di nuovo");return;
        }
        try{
            recording=new File(takeDir(line),"take_"+System.currentTimeMillis()+(provisional?"_TEMP":"")+".m4a");
            recorder=new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(recording.getAbsolutePath());
            recorder.prepare();recorder.start();recordingLine=line;
            toast("Registrazione avviata");
        }catch(Exception e){if(recorder!=null)recorder.release();recorder=null;toast("Microfono: "+e.getMessage());}
    }
    void stop(){
        if(recorder==null){toast("Nessuna registrazione in corso");return;}
        try{recorder.stop();toast("Take salvata sul telefono");}
        catch(Exception e){if(recording!=null)recording.delete();toast("Registrazione non valida");}
        finally{recorder.release();recorder=null;recordingLine="";renderLines();}
    }
    void play(File f){
        try{if(player!=null){player.release();player=null;}player=new MediaPlayer();player.setDataSource(f.getAbsolutePath());player.prepare();player.start();
            player.setOnCompletionListener(p->{p.release();player=null;});
        }catch(Exception e){toast("Audio: "+e.getMessage());}
    }
    void pair(){
        LinearLayout box=new LinearLayout(this);box.setPadding(25,10,25,10);box.setOrientation(LinearLayout.VERTICAL);
        EditText url=new EditText(this);url.setSingleLine();url.setHint("http://192.168.1.20:18766");url.setText(pref("host"));box.addView(url);
        EditText key=new EditText(this);key.setSingleLine();key.setHint("Codice di abbinamento PC");key.setText(pref("key"));box.addView(key);
        new AlertDialog.Builder(this).setTitle("Connessione al PC").setView(box)
        .setPositiveButton("Salva",(d,w)->{
            String host=url.getText().toString().trim().replaceAll("/+$","");
            if(!host.matches("http://(10\\.\\d+\\.\\d+\\.\\d+|192\\.168\\.\\d+\\.\\d+|172\\.(1[6-9]|2[0-9]|3[01])\\.\\d+\\.\\d+):18766")){
                toast("Indirizzo Wi-Fi privato non valido (porta 18766)");return;
            }
            if(key.getText().toString().trim().length()<16){toast("Codice di abbinamento mancante");return;}
            setting("host",host);setting("key",key.getText().toString().trim());toast("PC configurato");
        }).setNegativeButton("Annulla",null).show();
    }
    HttpURLConnection connection(String path,String method)throws Exception{
        if(pref("host").isEmpty())throw new IOException("Configura prima il PC");
        HttpURLConnection c=(HttpURLConnection)new URL(pref("host")+path).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(6000);c.setReadTimeout(45000);
        c.setRequestProperty("X-MAF-Key",pref("key"));c.setUseCaches(false);return c;
    }
    String get(String path)throws Exception{
        HttpURLConnection c=connection(path,"GET");
        try{int status=c.getResponseCode();
            if(status!=200)throw new IOException("Errore PC HTTP "+status);
            return new String(read(c.getInputStream()),StandardCharsets.UTF_8);
        }finally{c.disconnect();}
    }
    void chooseEpisode(){
        background(()->{
            try{
                JSONArray arr=new JSONObject(get("/api/sync/projects")).getJSONArray("projects");
                String[] labels=new String[arr.length()],ids=new String[arr.length()];
                for(int i=0;i<arr.length();i++){JSONObject p=arr.getJSONObject(i);ids[i]=p.getString("id");labels[i]=p.getString("title");}
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Episodi sul PC").setItems(labels,(d,n)->download(ids[n]))
                    .setNegativeButton("Annulla",null).show());
            }catch(Exception e){toast("Elenco episodi: "+e.getMessage());}
        });
    }
    void download(String id){
        // Never switch projects without warning about unsynchronized local recordings.
        if(episode!=null&&!id.equals(episode.optString("project_id"))&&localTakeCount()>0){
            new AlertDialog.Builder(this).setTitle("Cambio episodio").setMessage("Controlla prima che le take dell'episodio corrente siano sincronizzate.")
               .setPositiveButton("Continua",(d,w)->fetchEpisode(id)).setNegativeButton("Annulla",null).show();
        }else fetchEpisode(id);
    }
    int localTakeCount(){int n=0;for(JSONObject l:lines)n+=takes(l.optString("id")).length;return n;}
    void fetchEpisode(String id){
        background(()->{
            try{String raw=get("/api/sync/projects/"+id);
                runOnUiThread(()->{
                    try{loadEpisode(raw);
                        try(FileOutputStream f=openFileOutput("episode.json",MODE_PRIVATE)){f.write(raw.getBytes(StandardCharsets.UTF_8));}
                        render();toast("Episodio disponibile offline");
                    }catch(Exception e){toast("Salvataggio: "+e.getMessage());}
                });
            }catch(Exception e){toast("Download episodio: "+e.getMessage());}
        });
    }
    String sha256(File f)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(FileInputStream input=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=input.read(b))!=-1)hash.update(b,0,n);}
        StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    void field(OutputStream os,String boundary,String k,String val)throws IOException {
        os.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+k+"\"\r\n\r\n"+val+"\r\n").getBytes(StandardCharsets.UTF_8));
    }
    void postTake(String pid,String lid,File file,String uploadId,boolean preferred)throws Exception{
        String boundary="MAF"+System.currentTimeMillis();
        HttpURLConnection c=connection("/api/sync/projects/"+pid+"/takes","POST");
        c.setDoOutput(true);c.setChunkedStreamingMode(32768);c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
        try{
            try(OutputStream os=c.getOutputStream()){
                field(os,boundary,"line_id",lid);
                field(os,boundary,"device_id",deviceId());
                field(os,boundary,"upload_id",uploadId);
                field(os,boundary,"sha256",sha256(file));
                field(os,boundary,"provisional",String.valueOf(file.getName().contains("_TEMP")));
                field(os,boundary,"preferred",String.valueOf(preferred));
                os.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+file.getName()+"\"\r\nContent-Type: audio/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                try(FileInputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)os.write(b,0,n);}
                os.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            if(c.getResponseCode()!=200)throw new IOException("Caricamento fallito HTTP "+c.getResponseCode());
            read(c.getInputStream());
        }finally{c.disconnect();}
    }
    void sync(){
        if(episode==null){toast("Seleziona prima un episodio");return;}
        if(syncing)return;syncing=true;toast("Sincronizzazione in corso");
        String pid=episode.optString("project_id");
        background(()->{
            int sent=0;
            try{
                JSONObject remote=new JSONObject(get("/api/sync/projects/"+pid));
                if(remote.optInt("revision",-1)!=episode.optInt("revision",-2))
                    throw new IOException("Copione aggiornato sul PC. Scarica nuovamente l'episodio prima di inviare le take.");
                JSONObject known=new JSONObject(get("/api/sync/projects/"+pid+"/uploads?device_id="+deviceId()));
                HashSet<String> uploaded=new HashSet<>();
                JSONArray ids=known.getJSONArray("upload_ids");
                for(int i=0;i<ids.length();i++)uploaded.add(ids.getString(i));
                for(JSONObject line:lines){
                    String id=line.optString("id");
                    String fav=pref("favorite_"+id);
                    for(File f:takes(id)){
                        String uploadId=id+"_"+f.getName();
                        if(uploaded.contains(uploadId))continue;
                        postTake(pid,id,f,uploadId,f.getName().equals(fav));sent++;
                    }
                }
                final int n=sent;toast("Sincronizzazione completata: "+n+" nuove take. Audio conservati sul telefono.");
            }catch(Exception e){toast("Sincronizzazione interrotta: "+e.getMessage()+". Le take restano salvate sul telefono.");}
            finally{syncing=false;}
        });
    }
    @Override public void onBackPressed(){
        if(recorder!=null){toast("Ferma la registrazione prima di uscire");return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy(){super.onDestroy();if(recorder!=null){try{recorder.stop();}catch(Exception e){}recorder.release();}if(player!=null)player.release();}
}
