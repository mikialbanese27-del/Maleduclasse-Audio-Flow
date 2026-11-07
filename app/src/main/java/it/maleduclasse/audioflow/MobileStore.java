package it.maleduclasse.audioflow;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.util.zip.ZipInputStream;

/** Local-first persistent model. Every write is atomic; recordings are never overwritten. */
final class MobileStore {
  private final Context context;
  private final File base;
  MobileStore(Context c){ context=c; base=new File(c.getFilesDir(),"mobile_projects"); base.mkdirs(); migrateLegacy(); }
  /** Import v0.3 data without moving or deleting any recorded files. */
  private void migrateLegacy(){
    File previous=new File(context.getFilesDir(),"episode.json");if(!previous.isFile())return;
    JSONObject old=read(previous);String pid=str(old,"project_id");if(!pid.matches("[a-f0-9]{16}")||new File(projectDir(pid),"project.json").isFile())return;
    JSONObject cast=cast();JSONArray actors=cast.optJSONArray("actors"),chars=cast.optJSONArray("characters"),lines=old.optJSONArray("lines");
    if(lines==null)return;StringBuilder script=new StringBuilder();String scene="";
    for(int i=0;i<lines.length();i++){JSONObject line=lines.optJSONObject(i);if(line==null)continue;
      String who=str(line,"actor"),name=str(line,"character");if(name.isEmpty())name="Personaggio";
      if(!who.isEmpty()&&!who.equals("Non assegnato")&&byName(actors,who)==null){JSONObject actor=obj();put(actor,"id",id());put(actor,"name",who);actors.put(actor);}
      JSONObject ch=byName(chars,name);if(ch==null){ch=obj();String cid=str(line,"character_id");put(ch,"id",cid.matches("[a-f0-9]{16}")?cid:id());put(ch,"name",name);JSONObject a=byName(actors,who);put(ch,"actor_id",a==null?JSONObject.NULL:str(a,"id"));chars.put(ch);}
      put(line,"character_id",str(ch,"id"));String current=str(line,"scene");if(current.isEmpty())current="Scena 1";
      if(!current.equals(scene)){scene=current;script.append("/scena ").append(scene).append("\n");}
      script.append('@').append(name).append("\n").append(str(line,"text")).append("\n\n");
      String preferred=context.getSharedPreferences("MainActivity",0).getString("preferred_"+str(line,"id"),"");
      if(!preferred.isEmpty()){JSONObject meta=takeMeta(pid,str(line,"id"));put(meta,"preferred",preferred);saveTakeMeta(pid,str(line,"id"),meta);}
    }
    put(cast,"actors",actors);put(cast,"characters",chars);write(new File(base,"cast.json"),cast);
    put(old,"script",script.toString());put(old,"dirty",false);put(old,"revision",old.optInt("revision",0));saveProject(old);
  }
  static String id(){return UUID.randomUUID().toString().replace("-","").substring(0,16);}
  static JSONObject obj(){return new JSONObject();}
  static JSONArray array(){return new JSONArray();}
  static JSONObject put(JSONObject o,String k,Object v){try{o.put(k,v);}catch(JSONException e){throw new IllegalArgumentException(e);}return o;}
  static String str(JSONObject o,String k){return o==null?"":o.optString(k,"");}
  static JSONObject read(File f){try{if(!f.isFile())return obj();byte[] bytes=new byte[(int)f.length()];try(FileInputStream in=new FileInputStream(f)){int n=0;while(n<bytes.length){int k=in.read(bytes,n,bytes.length-n);if(k<0)break;n+=k;}}return new JSONObject(new String(bytes,StandardCharsets.UTF_8));}catch(Exception ex){return obj();}}
  static void write(File f,JSONObject data){ try{ f.getParentFile().mkdirs();File temp=new File(f.getParentFile(),f.getName()+".tmp"); try(FileOutputStream out=new FileOutputStream(temp)){out.write(data.toString(2).getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(!temp.renameTo(f)){try(FileInputStream in=new FileInputStream(temp);FileOutputStream out=new FileOutputStream(f)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}temp.delete();}}catch(Exception e){throw new RuntimeException("Salvataggio: "+e.getMessage(),e);} }
  File projectDir(String pid){return new File(base,pid);}
  File takeDir(String pid,String lid){File dir=new File(context.getFilesDir(),"takes/"+pid+"/"+lid);dir.mkdirs();return dir;}
  File[] takes(String pid,String lid){File[] arr=takeDir(pid,lid).listFiles((dir,name)->{String s=name.toLowerCase(Locale.ROOT);return s.endsWith(".m4a")||s.endsWith(".wav")||s.endsWith(".mp3")||s.endsWith(".aac")||s.endsWith(".ogg")||s.endsWith(".flac");});if(arr==null)return new File[0];Arrays.sort(arr,Comparator.comparing(File::getName));return arr;}
  JSONObject cast(){JSONObject cast=read(new File(base,"cast.json"));if(!cast.has("actors"))put(cast,"actors",array());if(!cast.has("characters"))put(cast,"characters",array());return cast;}
  void saveCast(JSONObject value){write(new File(base,"cast.json"),value);markAllDirty();}
  void markAllDirty(){for(JSONObject project:projects()){put(project,"dirty",true);saveProject(project);}}
  List<JSONObject> projects(){ArrayList<JSONObject> result=new ArrayList<>();File[] dirs=base.listFiles(File::isDirectory);if(dirs!=null)for(File d:dirs){JSONObject p=read(new File(d,"project.json"));if(!str(p,"project_id").isEmpty()&&!p.optBoolean("deleted",false))result.add(p);}result.sort((a,b)->Long.compare(b.optLong("edited",0),a.optLong("edited",0)));return result;}
  JSONObject project(String pid){return read(new File(projectDir(pid),"project.json"));}
  void saveProject(JSONObject p){String pid=str(p,"project_id");if(!pid.matches("[a-f0-9]{16}"))throw new IllegalArgumentException("ID progetto non valido");put(p,"edited",System.currentTimeMillis());write(new File(projectDir(pid),"project.json"),p);}
  JSONObject createProject(String title){JSONObject p=obj();put(p,"project_id",id());put(p,"title",title.trim());put(p,"script","");put(p,"revision",0);put(p,"dirty",true);put(p,"lines",array());put(p,"overrides",array());saveProject(p);return p;}
  JSONObject duplicateProject(JSONObject original){JSONObject p=createProject(str(original,"title")+" (copia)");put(p,"script",str(original,"script"));put(p,"overrides",original.optJSONArray("overrides")==null?array():original.optJSONArray("overrides"));List<JSONObject> lines=parseScript(p,cast(),array());put(p,"lines",asArray(lines));saveProject(p);return p;}
  void trashProject(JSONObject project){put(project,"deleted",true);saveProject(project);}
  void deletePermanently(String pid){File dir=projectDir(pid);File audio=new File(context.getFilesDir(),"takes/"+pid);deleteTree(dir);deleteTree(audio);}
  private static void deleteTree(File f){if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File child:children)deleteTree(child);}if(f.exists()&&!f.delete())throw new RuntimeException("Impossibile eliminare "+f.getName());}
  static JSONArray asArray(List<JSONObject> list){JSONArray a=array();for(JSONObject o:list)a.put(o);return a;}
  static JSONObject byId(JSONArray arr,String id){if(arr!=null)for(int i=0;i<arr.length();i++){JSONObject v=arr.optJSONObject(i);if(v!=null&&id.equals(str(v,"id")))return v;}return null;}
  static JSONObject byName(JSONArray arr,String name){if(arr!=null)for(int i=0;i<arr.length();i++){JSONObject v=arr.optJSONObject(i);if(v!=null&&name.equalsIgnoreCase(str(v,"name")))return v;}return null;}
  static String actorName(JSONObject line,JSONObject project,JSONObject cast){String charId=str(line,"character_id");JSONObject ch=byId(cast.optJSONArray("characters"),charId);String aid=ch==null?"":str(ch,"actor_id");JSONArray ov=project.optJSONArray("overrides");if(ov!=null)for(int i=0;i<ov.length();i++){JSONObject row=ov.optJSONObject(i);if(row!=null&&charId.equals(str(row,"character_id"))&&!str(row,"actor_id").isEmpty())aid=str(row,"actor_id");}JSONObject actor=byId(cast.optJSONArray("actors"),aid);return actor==null?"Non assegnato":str(actor,"name");}
  static String characterName(JSONObject line,JSONObject cast){JSONObject ch=byId(cast.optJSONArray("characters"),str(line,"character_id"));return ch==null?"Personaggio?":str(ch,"name");}

  /** Matches desktop /scena @nome /azione /pausa parsing and keeps line IDs on edits. */
  List<JSONObject> parseScript(JSONObject project,JSONObject cast,JSONArray previous){
    String script=str(project,"script"),scene="Scena 1",currentCharacter="";
    ArrayList<JSONObject> drafts=new ArrayList<>();StringBuilder buffer=new StringBuilder();
    String[] rows=script.split("\\r?\\n",-1);
    for(String raw:rows){String t=raw.trim();
      if(t.isEmpty()){if(buffer.length()>0){addLine(drafts,currentCharacter,scene,buffer.toString().trim());buffer.setLength(0);}continue;}
      if(t.startsWith("@")){
        if(buffer.length()>0){addLine(drafts,currentCharacter,scene,buffer.toString().trim());buffer.setLength(0);}
        String name=t.substring(1).trim().replaceFirst(":$","");JSONObject ch=byName(cast.optJSONArray("characters"),name);
        if(ch==null){JSONObject only=null;JSONArray chars=cast.optJSONArray("characters");if(chars!=null)for(int i=0;i<chars.length();i++){JSONObject a=chars.optJSONObject(i);String label=str(a,"name");for(String part:label.split("\\s+"))if(part.equalsIgnoreCase(name)){if(only!=null)only=null;else only=a;}}
          ch=only;
        }
        currentCharacter=ch==null?"":str(ch,"id");continue;
      }
      if(t.startsWith("/")){
        if(buffer.length()>0){addLine(drafts,currentCharacter,scene,buffer.toString().trim());buffer.setLength(0);}
        currentCharacter="";
        if(t.toLowerCase(Locale.ROOT).startsWith("/scena "))scene=t.substring(7).trim();
        continue;
      }
      if(buffer.length()>0)buffer.append('\n');buffer.append(raw.replaceFirst("\\s+$",""));
    }
    if(buffer.length()>0)addLine(drafts,currentCharacter,scene,buffer.toString().trim());
    ArrayList<JSONObject> result=new ArrayList<>();HashSet<String> used=new HashSet<>();
    for(int i=0;i<drafts.size();i++){
      JSONObject d=drafts.get(i);JSONObject best=null;double score=0;
      for(int j=0;previous!=null&&j<previous.length();j++){
        JSONObject old=previous.optJSONObject(j);if(old==null||used.contains(str(old,"id")))continue;
        boolean sameCharacter=str(old,"character_id").equals(str(d,"character_id"));
        double similarity=similarity(str(old,"text"),str(d,"text"));
        double proximity=1.0-Math.min(1.0,Math.abs(old.optInt("ordinal",j)-i)/(double)Math.max(1,drafts.size()));
        double s=.72*similarity+.18*proximity+.10*(sameCharacter?1:0);
        if(s>score){score=s;best=old;}
      }
      boolean reusing=best!=null&&score>=.65;
      put(d,"id",reusing?str(best,"id"):id());put(d,"ordinal",i);
      if(reusing){used.add(str(best,"id"));boolean changed=!str(d,"text").equals(str(best,"text"))||!str(d,"character_id").equals(str(best,"character_id"));put(d,"status",changed?"rerecord":str(best,"status"));}
      else put(d,"status","missing");
      result.add(d);
    }
    return result;
  }
  private static void addLine(List<JSONObject> draft,String charId,String scene,String txt){if(charId.isEmpty()||txt.isEmpty())return;JSONObject row=obj();put(row,"character_id",charId);put(row,"scene",scene);put(row,"text",txt);draft.add(row);}
  private static double similarity(String a,String b){if(a.equals(b))return 1;if(a.isEmpty()||b.isEmpty())return 0;if(a.length()>700||b.length()>700)return 0.5;
    int[] prev=new int[b.length()+1],next=new int[b.length()+1];for(int j=0;j<=b.length();j++)prev[j]=j;
    for(int i=1;i<=a.length();i++){next[0]=i;for(int j=1;j<=b.length();j++)next[j]=Math.min(Math.min(next[j-1]+1,prev[j]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));int[] swap=prev;prev=next;next=swap;}
    return 1.0-prev[b.length()]/(double)Math.max(a.length(),b.length());
  }
  JSONObject saveScript(JSONObject p,String script){
    JSONArray old=p.optJSONArray("lines");String pid=str(p,"project_id");
    put(p,"script",script);List<JSONObject> updated=parseScript(p,cast(),old);HashSet<String> retained=new HashSet<>();
    for(JSONObject line:updated){String lid=str(line,"id");retained.add(lid);JSONObject before=byId(old,lid);
      if(before!=null&&(!str(before,"text").equals(str(line,"text"))||!str(before,"character_id").equals(str(line,"character_id"))))archiveLineTakes(pid,lid);
    }
    if(old!=null)for(int i=0;i<old.length();i++){JSONObject previous=old.optJSONObject(i);if(previous!=null&&!retained.contains(str(previous,"id")))archiveLineTakes(pid,str(previous,"id"));}
    put(p,"lines",asArray(updated));put(p,"dirty",true);saveProject(p);return p;
  }
  private void archiveLineTakes(String pid,String lid){
    File dir=takeDir(pid,lid);File[] files=dir.listFiles();if(files==null||files.length==0)return;
    File archive=new File(context.getFilesDir(),"archived_takes/"+pid+"/"+lid+"/"+System.currentTimeMillis());archive.mkdirs();
    for(File f:files)if(f.isFile()&&!f.getName().equals("metadata.json")){
      File destination=new File(archive,f.getName());if(!f.renameTo(destination))throw new RuntimeException("Impossibile archiviare vecchie take: "+f.getName());
    }
    File meta=new File(dir,"metadata.json");if(meta.exists()){File destination=new File(archive,"metadata.json");if(!meta.renameTo(destination))throw new RuntimeException("Impossibile archiviare i metadati delle take");}
  }
  void importFromPC(JSONObject full){String pid=str(full,"project_id");JSONObject current=project(pid);
    if(current.optBoolean("dirty",false))throw new IllegalStateException("Ci sono modifiche locali: sincronizzale o esegui una copia prima di sostituirle");
    JSONObject newCast=obj();put(newCast,"actors",full.optJSONArray("actors"));put(newCast,"characters",full.optJSONArray("characters"));write(new File(base,"cast.json"),newCast);
    put(full,"dirty",false);saveProject(full);
  }
  JSONObject takeMeta(String pid,String lid){return read(new File(takeDir(pid,lid),"metadata.json"));}
  void saveTakeMeta(String pid,String lid,JSONObject meta){write(new File(takeDir(pid,lid),"metadata.json"),meta);}
  boolean isPreferred(String pid,String lid,String filename){return filename.equals(takeMeta(pid,lid).optString("preferred"));}
  boolean isProvisional(String pid,String lid,String filename){return takeMeta(pid,lid).optJSONObject("provisional")!=null&&takeMeta(pid,lid).optJSONObject("provisional").optBoolean(filename,false);}
  void setPreferred(String pid,String lid,String filename){JSONObject m=takeMeta(pid,lid);put(m,"preferred",filename);saveTakeMeta(pid,lid,m);}
  void setProvisional(String pid,String lid,String filename,boolean value){JSONObject m=takeMeta(pid,lid),pr=m.optJSONObject("provisional");if(pr==null)pr=obj();put(pr,filename,value);put(m,"provisional",pr);saveTakeMeta(pid,lid,m);}
  void queueDeletion(String pid,String lid,String filename){JSONObject p=project(pid);JSONArray q=p.optJSONArray("deleted_takes");if(q==null){q=array();put(p,"deleted_takes",q);}JSONObject row=obj();put(row,"line_id",lid);put(row,"file_name",filename);q.put(row);saveProject(p);}
  static String docxText(InputStream file) throws Exception {byte[] xml=null;try(ZipInputStream zip=new ZipInputStream(file)){java.util.zip.ZipEntry e;while((e=zip.getNextEntry())!=null){if(e.getName().equals("word/document.xml")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=zip.read(b))!=-1){out.write(b,0,n);if(out.size()>3_000_000)throw new IOException("Word troppo grande");}xml=out.toByteArray();break;}}}if(xml==null)throw new IOException("File .docx non valido");
    DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
    Document doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));NodeList paragraphs=doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","p");StringBuilder text=new StringBuilder();for(int i=0;i<paragraphs.getLength();i++){Node p=paragraphs.item(i);NodeList parts=((Element)p).getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","t");for(int j=0;j<parts.getLength();j++)text.append(parts.item(j).getTextContent());text.append('\n');}return text.toString();
  }
}