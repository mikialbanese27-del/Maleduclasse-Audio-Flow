package it.maleduclasse.audioflow;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.*;
import android.speech.RecognizerIntent;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Full mobile-first editor: Projects, Script, Cast, Dubbing; timeline/sound library live on PC. */
public final class MainActivity extends Activity {
  private static final int MIC=101,IMPORT_WORD=102,IMPORT_TAKE=103,DICTATE=104,IMPORT_EPISODE=105;
  private final int BG=Color.rgb(20,18,29),PANEL=Color.rgb(36,31,48),TEXT=Color.rgb(244,241,248);
  private final int PURPLE=Color.rgb(161,119,242),YELLOW=Color.rgb(231,194,69),GREEN=Color.rgb(27,118,78),RED=Color.rgb(136,35,51),BROWN=Color.rgb(127,78,45);
  private MobileStore store;private MobileSync sync;
  private JSONObject active;private JSONObject cast;
  private String page="Progetti",selectedLine="",actorFilter="Tutti",lastSync="Non sincronizzato";
  private String filterMode="Doppiatore";private boolean pcOnline=false;private TextView connectionDot;
  private boolean recordingPending=false;private AlertDialog reviewDialog;
  private ArrayList<Integer> capturedPeaks=new ArrayList<>();private final Runnable sampleAmplitude=new Runnable(){
    @Override public void run(){if(recorder==null)return;
      try{capturedPeaks.add(Math.max(2,recorder.getMaxAmplitude()));}catch(Exception ignored){}
      ui.postDelayed(this,110);
    }
  };
  private LinearLayout root,body;private EditText editor;private String draft="";private boolean editorDirty=false,styling=false;
  private Handler ui=new Handler(Looper.getMainLooper());private Runnable stylingJob;private PopupWindow suggestions;
  private MediaRecorder recorder;private MediaPlayer player;private File recordingFile;private String recordingLine="";private boolean recordingProvisional=false;private boolean syncBusy=false;
  private AlertDialog takeDialog;private Chronometer recordingClock;private long lastAutoSync=0L;
  private Button recorderButton;private String selectedTakeName="";

  @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
    store=new MobileStore(this);sync=new MobileSync(this,store);cast=store.cast();
    String pid=getPreferences(0).getString("current_project","");active=store.project(pid);
    if(MobileStore.str(active,"project_id").isEmpty()){List<JSONObject> ps=store.projects();active=ps.isEmpty()?null:ps.get(0);}
    show();
  }
  private final Runnable lanHeartbeat=new Runnable(){
    @Override public void run(){autoSync();ui.postDelayed(this,12000);}
  };
  @Override protected void onResume(){super.onResume();ui.removeCallbacks(lanHeartbeat);ui.postDelayed(lanHeartbeat,1100);}
  @Override protected void onPause(){ui.removeCallbacks(lanHeartbeat);super.onPause();}
  private boolean wifiConnected(){
    ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
    if(cm==null)return false;Network network=cm.getActiveNetwork();if(network==null)return false;
    NetworkCapabilities cap=cm.getNetworkCapabilities(network);
    return cap!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
  }
  private void autoSync(){
    long now=SystemClock.elapsedRealtime();
    if(sync==null||sync.url().isEmpty()||syncBusy||recorder!=null||editorDirty||!wifiConnected()||
       (lastAutoSync>0&&now-lastAutoSync<11000))return;
    lastAutoSync=now;syncBusy=true;
    background(()->{
      try{
        MobileSync.Result result=sync.synchronizeAll();
        runOnUiThread(()->{
          syncBusy=false;pcOnline=true;updateConnectionDot();lastSync="Automatico: "+result.message();
          if(result.receivedProjects>0||result.receivedTakes>0){
            cast=store.cast();
            if(active!=null)active=store.project(pid());
            if(page.equals("Progetti")||page.equals("⚙ Cast")||
               (page.equals("Doppiaggio")&&(takeDialog==null||!takeDialog.isShowing())))show();
            else if(page.equals("Sceneggiatura")&&editor!=null&&!editorDirty&&!editor.hasFocus()){
              editor.setText(MobileStore.str(active,"script"));applySyntax();
            }
          }else if(page.equals("Sincronizza"))show();
        });
      }catch(Exception ex){
        runOnUiThread(()->{syncBusy=false;pcOnline=false;updateConnectionDot();lastSync="PC non raggiungibile o conflitto: "+ex.getMessage();});
      }
    });
  }
  private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
  private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  private GradientDrawable box(int color,int radius,int strokeColor,int strokeWidth){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(strokeWidth>0)d.setStroke(dp(strokeWidth),strokeColor);return d;}
  private TextView label(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextColor(color);v.setTextSize(size);v.setPadding(dp(7),dp(7),dp(7),dp(7));if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
  private Button btn(String s,int color,Runnable handler){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(TEXT);b.setTextSize(13);b.setPadding(dp(7),dp(3),dp(7),dp(3));b.setBackgroundTintList(ColorStateList.valueOf(color));b.setOnClickListener(v->handler.run());return b;}
  private void addButton(LinearLayout target,String label,int color,Runnable handler){Button b=btn(label,color,handler);target.addView(b,new LinearLayout.LayoutParams(-1,dp(50)));}
  private void pad(View v){v.setPadding(dp(12),dp(12),dp(12),dp(12));}
  private void section(String s,int color){TextView title=label(s,19,color,true);body.addView(title);}
  private void note(String s){body.addView(label(s,13,Color.rgb(179,173,193),false));}
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  private String pid(){return active==null?"":MobileStore.str(active,"project_id");}
  private void setProject(JSONObject p){active=p;selectedLine="";if(p!=null)getPreferences(0).edit().putString("current_project",pid()).apply();page="Sceneggiatura";show();}
  private void updateConnectionDot(){
    if(connectionDot!=null)connectionDot.setTextColor(pcOnline?Color.rgb(63,204,139):Color.rgb(235,84,102));
  }
  private void showSettingsMenu(View anchor){
    PopupMenu menu=new PopupMenu(this,anchor);
    menu.getMenu().add("Cast e personaggi").setOnMenuItemClickListener(item->{openPage("⚙ Cast");return true;});
    menu.getMenu().add("Sincronizzazione Windows").setOnMenuItemClickListener(item->{openPage("Sincronizza");return true;});
    menu.show();
  }
  private Button navButton(String glyph,String title,String destination){
    Button b=btn(glyph+"\n"+title,page.equals(destination)?Color.rgb(78,59,107):BG,()->openPage(destination));
    b.setTextSize(11);b.setMinHeight(0);b.setPadding(dp(3),dp(4),dp(3),dp(4));return b;
  }
  private void show(){
    dismissSuggestions();editor=null;editorDirty=false;
    root=column();root.setBackgroundColor(BG);setContentView(root);
    LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(12),dp(5),dp(10),dp(5));header.setBackgroundColor(Color.rgb(26,23,36));
    TextView brand=label("◉",30,PURPLE,true);brand.setGravity(Gravity.CENTER);
    header.addView(new View(this),new LinearLayout.LayoutParams(0,dp(48),1));
    header.addView(brand,new LinearLayout.LayoutParams(dp(50),dp(51)));
    header.addView(new View(this),new LinearLayout.LayoutParams(0,dp(48),1));
    connectionDot=label("●",22,pcOnline?Color.rgb(63,204,139):Color.rgb(235,84,102),true);
    connectionDot.setContentDescription(pcOnline?"PC collegato":"PC non collegato");
    header.addView(connectionDot);Button settings=btn("⚙",PANEL,()->showSettingsMenu(connectionDot));
    settings.setTextSize(23);header.addView(settings);root.addView(header);
    TextView projectLabel=label(active==null?"Scegli un episodio":MobileStore.str(active,"title"),13,Color.rgb(186,176,203),false);
    projectLabel.setMaxLines(1);root.addView(projectLabel);
    if(page.equals("Sceneggiatura")&&active!=null)addScriptPresetToolbar();
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
    scroll.setVerticalScrollBarEnabled(false);
    root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    body=column();body.setPadding(dp(12),dp(6),dp(12),dp(20));scroll.addView(body);
    switch(page){case "Progetti":renderProjects();break;case "Sceneggiatura":renderScript();break;
      case "Doppiaggio":renderDubbing();break;case "⚙ Cast":renderCast();break;default:renderSync();}
    if(!page.equals("Sceneggiatura")){
      LinearLayout bottom=row();bottom.setGravity(Gravity.CENTER);
      bottom.setPadding(dp(6),dp(5),dp(6),dp(7));bottom.setBackgroundColor(Color.rgb(26,23,36));
      bottom.addView(navButton("▦","Progetti","Progetti"),new LinearLayout.LayoutParams(0,dp(60),1));
      bottom.addView(navButton("✎","Scrivi","Sceneggiatura"),new LinearLayout.LayoutParams(0,dp(60),1));
      bottom.addView(navButton("◉","Doppiaggio","Doppiaggio"),new LinearLayout.LayoutParams(0,dp(60),1));
      root.addView(bottom);
    }
  }
  private void openPage(String target){if(editor!=null&&editorDirty){new AlertDialog.Builder(this).setTitle("Sceneggiatura modificata").setMessage("Salvare il copione sul telefono prima di uscire?")
      .setPositiveButton("Salva",(d,w)->{if(saveEditor())go(target);}).setNeutralButton("Non salvare",(d,w)->go(target)).setNegativeButton("Resta qui",null).show();return;}go(target);}
  private void go(String target){page=target;show();}
  private void requireProject(Runnable action){if(active==null){toast("Crea prima un progetto");openPage("Progetti");}else action.run();}
  private void askText(String title,String original,java.util.function.Consumer<String> done){EditText field=new EditText(this);field.setTextColor(TEXT);field.setText(original);field.setSingleLine(true);field.setSelectAllOnFocus(true);field.setPadding(dp(18),dp(12),dp(18),dp(12));new AlertDialog.Builder(this).setTitle(title).setView(field).setPositiveButton("Salva",(d,w)->{String name=field.getText().toString().trim();if(name.isEmpty())toast("Nome obbligatorio");else done.accept(name);}).setNegativeButton("Annulla",null).show();}
  private void confirm(String title,String message,String action,Runnable run){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(action,(d,w)->run.run()).setNegativeButton("Annulla",null).show();}
  private LinearLayout card(int background){LinearLayout l=column();l.setBackground(box(background,12,Color.rgb(59,48,73),1));pad(l);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,0,0,dp(9));body.addView(l,params);return l;}
  private LinearLayout actionRow(){LinearLayout l=row();l.setPadding(0,dp(3),0,dp(3));return l;}
  private void renderProjects(){section("Progetti · tutto inizia dal telefono",PURPLE);note("Crea e modifica gli episodi anche senza Internet. Le registrazioni restano sul telefono finché il PC non le riceve.");
    addButton(body,"＋ Nuovo progetto",PURPLE,()->askText("Titolo nuovo episodio","",name->{setProject(store.createProject(name));}));
    for(JSONObject p:store.projects()){String id=MobileStore.str(p,"project_id");LinearLayout panel=card(PANEL);panel.addView(label(MobileStore.str(p,"title"),18,TEXT,true));
      JSONArray lines=p.optJSONArray("lines");panel.addView(label((lines==null?0:lines.length())+" battute  ·  "+(p.optBoolean("dirty",false)?"Da sincronizzare":"Allineato al PC"),12,YELLOW,false));
      LinearLayout actions=actionRow();actions.addView(btn("Apri",GREEN,()->setProject(store.project(id))));actions.addView(btn("Rinomina",PANEL,()->askText("Rinomina progetto",MobileStore.str(p,"title"),name->{MobileStore.put(p,"title",name);MobileStore.put(p,"dirty",true);store.saveProject(p);show();})));panel.addView(actions);
      LinearLayout more=actionRow();more.addView(btn("Duplica",PANEL,()->{setProject(store.duplicateProject(p));toast("Progetto duplicato; take escluse dalla copia");}));more.addView(btn("Archivia",BROWN,()->confirm("Archivia progetto?","Nascondo l'episodio sul telefono. Il PC non viene cancellato.","Archivia",()->{store.trashProject(p);if(id.equals(pid()))active=null;show();})));more.addView(btn("Elimina definitivamente",RED,()->confirm("ELIMINAZIONE DEFINITIVA","Cancella questo progetto e tutte le registrazioni locali associate. Se è già sul PC, il progetto PC resta intatto.","Elimina",()->{store.deletePermanently(id);if(id.equals(pid()))active=null;show();})));panel.addView(more);
    }
    if(!store.archivedProjects().isEmpty()){
      body.addView(label("Archivio progetti (recuperabili)",17,YELLOW,true));
      for(JSONObject p:store.archivedProjects()){
        String id=MobileStore.str(p,"project_id");LinearLayout archived=card(PANEL);
        archived.addView(label(MobileStore.str(p,"title"),15,TEXT,true));
        LinearLayout actions=actionRow();actions.addView(btn("Ripristina",GREEN,()->{store.restoreProject(p);show();}));
        actions.addView(btn("Elimina definitivamente",RED,()->confirm("Cancellazione definitiva?","Questo progetto e tutte le sue registrazioni saranno rimossi SOLO dal telefono.","Elimina",()->{store.deletePermanently(id);show();})));
        archived.addView(actions);
      }
    }
  }
  private void renderScript(){if(active==null){section("Sceneggiatura",PURPLE);note("Apri o crea un progetto prima di scrivere il copione.");return;}
    section("Sceneggiatura · "+MobileStore.str(active,"title"),Color.rgb(95,172,246));
    note("@Personaggio  ·  /scena Titolo  ·  /azione Testo  ·  /pausa 1.5  ·  /sfx Tag  ·  /ambiente Tag  ·  /musica Tag");
    editor=new EditText(this);editor.setTextColor(TEXT);editor.setHintTextColor(Color.GRAY);editor.setHint("/scena Scena 1\n@Personaggio\nScrivi qui la battuta...");editor.setTextSize(17);
    editor.setGravity(Gravity.TOP);editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
    editor.setTypeface(Typeface.MONOSPACE);editor.setPadding(dp(14),dp(14),dp(14),dp(14));editor.setBackground(box(Color.rgb(25,31,49),10,PURPLE,1));editor.setMinLines(16);
    draft=MobileStore.str(active,"script");editor.setText(draft);editor.setSelection(editor.length());body.addView(editor,new LinearLayout.LayoutParams(-1,dp(435)));applySyntax();
    editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!styling)editorDirty=!s.toString().equals(MobileStore.str(active,"script"));}
      public void afterTextChanged(Editable e){if(styling)return;if(stylingJob!=null)ui.removeCallbacks(stylingJob);stylingJob=()->{applySyntax();characterSuggestions();};ui.postDelayed(stylingJob,190);}});
    LinearLayout actions=actionRow();actions.addView(btn("Salva copione",GREEN,()->{if(saveEditor())toast("Copione salvato sul telefono");}));actions.addView(btn("@ Personaggio",PURPLE,this::chooseCharacterInsert));actions.addView(btn("Dettatura",PANEL,this::dictate));body.addView(actions);
    LinearLayout more=actionRow();more.addView(btn("Importa Word",PANEL,this::importWord));more.addView(btn("Controlla battute",PANEL,()->showValidation(editor.getText().toString())));more.addView(btn("Statistiche",PANEL,this::showStats));body.addView(more);
    note("Salvataggio manuale. La sincronizzazione con Windows si avvia dalla sezione Sincronizza. Nessuna take viene cancellata cambiando una battuta: le precedenti vengono archiviate separatamente.");
  }

  private void addScriptPresetToolbar(){
    root.addView(label("TAG RAPIDI",12,YELLOW,true));
    HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
    LinearLayout presets=row();
    String[] names={"@ Personaggio","Scena","Azione","Pausa","SFX","Ambiente","Musica"};
    String[] tokens={"","/scena ","/azione ","/pausa 1.5","/sfx ","/ambiente ","/musica "};
    int[] colors={GREEN,Color.rgb(55,104,169),RED,PANEL,Color.rgb(153,65,118),BROWN,Color.rgb(140,109,38)};
    for(int i=0;i<names.length;i++){
      final int which=i;
      Button b=btn(names[i],colors[i],()->{if(which==0)chooseCharacterInsert();else insertScriptToken(tokens[which]);});
      b.setFocusable(false);b.setTextSize(12);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(46));lp.setMargins(dp(2),0,dp(5),0);
      presets.addView(b,lp);
    }
    scroll.addView(presets);root.addView(scroll);
  }
  private void insertScriptToken(String token){
    if(editor==null)return;
    Editable e=editor.getText();int pos=Math.max(0,editor.getSelectionStart());
    boolean lineStart=pos==0||e.charAt(pos-1)=='\n';
    String insert=(lineStart?"":"\n")+token+(token.endsWith(" ")?"":"\n");
    e.insert(pos,insert);
    editor.setSelection(Math.min(e.length(),pos+(lineStart?0:1)+token.length()));
    editor.requestFocus();
    InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
    if(keyboard!=null)keyboard.showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);
  }
  private void applySyntax(){if(editor==null||styling)return;Editable edit=editor.getText();if(edit.length()>150000)return;styling=true;
    for(ForegroundColorSpan span:edit.getSpans(0,edit.length(),ForegroundColorSpan.class))edit.removeSpan(span);
    String[] lines=edit.toString().split("\n",-1);int offset=0;
    for(String raw:lines){String t=raw.trim();int color=t.startsWith("@")?Color.rgb(98,218,151):t.startsWith("/scena")?Color.rgb(95,173,244):t.startsWith("/azione")?Color.rgb(249,109,119):t.startsWith("/pausa")?Color.LTGRAY:t.startsWith("/sfx")?Color.rgb(255,134,196):t.startsWith("/musica")?YELLOW:t.startsWith("/ambiente")?Color.rgb(191,145,103):TEXT;
      if(!t.isEmpty())edit.setSpan(new ForegroundColorSpan(color),offset,Math.min(offset+raw.length(),edit.length()),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);offset+=raw.length()+1;
    }styling=false;
  }
  private void dismissSuggestions(){if(suggestions!=null){suggestions.dismiss();suggestions=null;}}
  private void characterSuggestions(){if(editor==null||!editor.hasFocus())return;int cursor=editor.getSelectionStart();if(cursor<0)return;String content=editor.getText().toString();int start=content.lastIndexOf('\n',Math.max(0,cursor-1))+1;
    if(start>=cursor||content.charAt(start)!='@'){dismissSuggestions();return;}String query=content.substring(start+1,cursor).toLowerCase(Locale.ROOT).trim();if(query.length()>30){dismissSuggestions();return;}
    JSONArray chars=cast.optJSONArray("characters");ArrayList<JSONObject> found=new ArrayList<>();if(chars!=null)for(int i=0;i<chars.length();i++){JSONObject c=chars.optJSONObject(i);if(c!=null&&MobileStore.str(c,"name").toLowerCase(Locale.ROOT).contains(query))found.add(c);}
    if(found.isEmpty()||query.length()==0){dismissSuggestions();return;}dismissSuggestions();LinearLayout list=column();list.setPadding(dp(5),dp(5),dp(5),dp(5));list.setBackground(box(PANEL,8,PURPLE,1));for(int i=0;i<Math.min(found.size(),5);i++){
      JSONObject ch=found.get(i);list.addView(btn(MobileStore.str(ch,"name"),PANEL,()->{Editable edit=editor.getText();edit.replace(start,Math.min(cursor,edit.length()),"@"+MobileStore.str(ch,"name"));dismissSuggestions();}));
    }suggestions=new PopupWindow(list,dp(280),-2,false);suggestions.setBackgroundDrawable(box(PANEL,8,PURPLE,1));suggestions.setOutsideTouchable(true);suggestions.setElevation(dp(9));suggestions.showAsDropDown(editor,dp(10),-dp(160));
  }
  private void chooseCharacterInsert(){JSONArray chars=cast.optJSONArray("characters");if(chars==null||chars.length()==0){toast("Aggiungi prima un personaggio in ⚙ Cast");return;}String[] names=new String[chars.length()];for(int i=0;i<names.length;i++)names[i]=MobileStore.str(chars.optJSONObject(i),"name");new AlertDialog.Builder(this).setTitle("Inserisci personaggio").setItems(names,(d,i)->{if(editor!=null){int pos=Math.max(0,editor.getSelectionStart());insertScriptToken("@"+names[i]);}}).show();}
  private boolean saveEditor(){if(editor==null||active==null)return false;try{String old=MobileStore.str(active,"script"),next=editor.getText().toString();if(!old.equals(next)){active=store.saveScript(active,next);editorDirty=false;}return true;}catch(Exception ex){toast("Impossibile salvare: "+ex.getMessage());return false;}}
  private void showValidation(String text){if(active==null)return;JSONObject temp=MobileStore.obj();MobileStore.put(temp,"script",text);List<JSONObject> parsed=store.parseScript(temp,cast,active.optJSONArray("lines"));StringBuilder issues=new StringBuilder();for(String s:text.split("\n"))if(s.trim().startsWith("@")){
      String name=s.trim().substring(1).trim().replaceFirst(":$","");if(MobileStore.byName(cast.optJSONArray("characters"),name)==null)issues.append("Personaggio non riconosciuto: ").append(name).append("\n");}
    new AlertDialog.Builder(this).setTitle("Controllo sceneggiatura").setMessage(parsed.size()+" battute riconosciute\n"+(issues.length()==0?"Nessun errore nei tag @ esatti.":issues.toString())+"\nIl PC effettuerà il controllo definitivo durante la sincronizzazione.").setPositiveButton("OK",null).show();}
  private void showStats(){if(active==null)return;JSONArray lines=active.optJSONArray("lines");TreeMap<String,Integer> counts=new TreeMap<>();int total=0;if(lines!=null)for(int i=0;i<lines.length();i++){JSONObject l=lines.optJSONObject(i);if(l==null)continue;String who=MobileStore.actorName(l,active,cast);counts.put(who,counts.getOrDefault(who,0)+1);total++;}StringBuilder b=new StringBuilder("Battute: "+total+"\nPersonaggi: "+cast.optJSONArray("characters").length()+"\nDoppiatori: "+cast.optJSONArray("actors").length()+"\n\n");for(Map.Entry<String,Integer> e:counts.entrySet())b.append(e.getKey()).append(": ").append(e.getValue()).append(" battute\n");new AlertDialog.Builder(this).setTitle("Statistiche episodio").setMessage(b.toString()).setPositiveButton("OK",null).show();}
  private void dictate(){Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"it-IT");try{startActivityForResult(intent,DICTATE);}catch(Exception e){toast("Dettatura non disponibile su questo Android");}}
  private void importWord(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");startActivityForResult(intent,IMPORT_WORD);}
  private void renderCast(){section("⚙ Impostazioni · Cast",Color.rgb(98,218,151));note("I doppiatori e personaggi sono condivisi tra gli episodi. Ogni episodio può avere assegnazioni diverse.");
    addButton(body,"＋ Aggiungi doppiatore",GREEN,()->askText("Nuovo doppiatore","",s->{JSONArray actors=cast.optJSONArray("actors");if(MobileStore.byName(actors,s)!=null){toast("Doppiatore già presente");return;}actors.put(MobileStore.put(MobileStore.put(MobileStore.obj(),"id",MobileStore.id()),"name",s));store.saveCast(cast);show();}));
    JSONArray actors=cast.optJSONArray("actors");for(int i=0;actors!=null&&i<actors.length();i++){JSONObject a=actors.optJSONObject(i);if(a==null)continue;LinearLayout panel=card(PANEL);LinearLayout r=actionRow();TextView title=label("🎙 "+MobileStore.str(a,"name"),16,TEXT,true);r.addView(title,new LinearLayout.LayoutParams(0,-2,1));r.addView(btn("Rinomina",PANEL,()->askText("Rinomina doppiatore",MobileStore.str(a,"name"),name->{MobileStore.put(a,"name",name);store.saveCast(cast);show();})));r.addView(btn("Elimina",RED,()->deleteCastActor(a)));panel.addView(r);}
    addButton(body,"＋ Aggiungi personaggio",PURPLE,()->askText("Nome nuovo personaggio","",s->{JSONArray list=cast.optJSONArray("characters");if(MobileStore.byName(list,s)!=null){toast("Personaggio già presente");return;}list.put(MobileStore.put(MobileStore.put(MobileStore.put(MobileStore.obj(),"id",MobileStore.id()),"name",s),"actor_id",JSONObject.NULL));store.saveCast(cast);show();}));
    JSONArray characters=cast.optJSONArray("characters");for(int i=0;characters!=null&&i<characters.length();i++){JSONObject ch=characters.optJSONObject(i);if(ch==null)continue;LinearLayout panel=card(PANEL);panel.addView(label("@"+MobileStore.str(ch,"name"),16,Color.rgb(98,218,151),true));
      JSONObject actor=MobileStore.byId(actors,MobileStore.str(ch,"actor_id"));panel.addView(label("Doppiatore base: "+(actor==null?"Non assegnato":MobileStore.str(actor,"name")),13,Color.LTGRAY,false));
      LinearLayout actions=actionRow();actions.addView(btn("Rinomina",PANEL,()->askText("Rinomina personaggio",MobileStore.str(ch,"name"),name->{MobileStore.put(ch,"name",name);store.saveCast(cast);show();})));actions.addView(btn("Assegna voce",GREEN,()->chooseActor(ch,null)));if(active!=null)actions.addView(btn("Voce episodio",PURPLE,()->chooseActor(ch,active)));actions.addView(btn("Elimina",RED,()->deleteCastCharacter(ch)));panel.addView(actions);
    }
  }
  private static JSONArray withoutId(JSONArray source,String id){
    JSONArray result=MobileStore.array();
    if(source!=null)for(int i=0;i<source.length();i++){
      JSONObject x=source.optJSONObject(i);
      if(x!=null&&!id.equals(MobileStore.str(x,"id")))result.put(x);
    }
    return result;
  }
  private void deleteCastActor(JSONObject actor){
    String id=MobileStore.str(actor,"id"),name=MobileStore.str(actor,"name");
    JSONArray chars=cast.optJSONArray("characters");
    if(chars!=null)for(int i=0;i<chars.length();i++){
      JSONObject ch=chars.optJSONObject(i);
      if(ch!=null&&id.equals(MobileStore.str(ch,"actor_id"))){
        toast("Doppiatore ancora assegnato: rimuovi prima le assegnazioni");return;
      }
    }
    for(JSONObject project:store.projects()){
      JSONArray overrides=project.optJSONArray("overrides");
      if(overrides!=null)for(int i=0;i<overrides.length();i++){
        JSONObject ov=overrides.optJSONObject(i);
        if(ov!=null&&id.equals(MobileStore.str(ov,"actor_id"))){
          toast("Doppiatore utilizzato in un episodio: rimuovi prima la voce episodio");return;
        }
      }
    }
    confirm("Eliminare doppiatore?","Eliminare "+name+" dal cast condiviso?","Elimina",()->{
      MobileStore.put(cast,"actors",withoutId(cast.optJSONArray("actors"),id));store.saveCast(cast);show();
    });
  }
  private void deleteCastCharacter(JSONObject character){
    String id=MobileStore.str(character,"id"),name=MobileStore.str(character,"name");
    for(JSONObject project:store.projects()){
      JSONArray lines=project.optJSONArray("lines");
      if(lines!=null)for(int i=0;i<lines.length();i++){
        JSONObject line=lines.optJSONObject(i);
        if(line!=null&&id.equals(MobileStore.str(line,"character_id"))){
          toast("Personaggio usato nelle battute: correggi prima i copioni");return;
        }
      }
      JSONArray overrides=project.optJSONArray("overrides");
      if(overrides!=null)for(int i=0;i<overrides.length();i++){
        JSONObject ov=overrides.optJSONObject(i);
        if(ov!=null&&id.equals(MobileStore.str(ov,"character_id"))){
          toast("Personaggio assegnato in un episodio: rimuovi prima l'assegnazione");return;
        }
      }
      if(MobileStore.str(project,"script").contains("@"+name)){
        toast("Personaggio citato nel copione: correggi prima il testo");return;
      }
    }
    confirm("Eliminare personaggio?","Eliminare "+name+" dal cast condiviso?","Elimina",()->{
      MobileStore.put(cast,"characters",withoutId(cast.optJSONArray("characters"),id));store.saveCast(cast);show();
    });
  }
  private void chooseActor(JSONObject character,JSONObject projectOverride){JSONArray actors=cast.optJSONArray("actors");String[] choices=new String[(actors==null?0:actors.length())+1];choices[0]="Non assegnato";for(int i=1;i<choices.length;i++)choices[i]=MobileStore.str(actors.optJSONObject(i-1),"name");new AlertDialog.Builder(this).setTitle(projectOverride==null?"Voce principale":"Voce per questo episodio").setItems(choices,(d,which)->{
      Object actorId=which==0?JSONObject.NULL:MobileStore.str(actors.optJSONObject(which-1),"id");if(projectOverride==null){MobileStore.put(character,"actor_id",actorId);store.saveCast(cast);}else{
        JSONArray old=projectOverride.optJSONArray("overrides"),next=MobileStore.array();if(old!=null)for(int i=0;i<old.length();i++){JSONObject entry=old.optJSONObject(i);if(entry!=null&&!MobileStore.str(entry,"character_id").equals(MobileStore.str(character,"id")))next.put(entry);}
        if(which!=0){JSONObject entry=MobileStore.obj();MobileStore.put(entry,"character_id",MobileStore.str(character,"id"));MobileStore.put(entry,"actor_id",actorId);next.put(entry);}MobileStore.put(projectOverride,"overrides",next);MobileStore.put(projectOverride,"dirty",true);store.saveProject(projectOverride);
      }show();
    }).show();}
  private void renderDubbing(){if(active==null){section("Doppiaggio",GREEN);note("Crea o apri un episodio prima di registrare.");return;}
    section("Doppiaggio · Offline",Color.rgb(98,218,151));note("Scegli la battuta, registra più take, ascolta, assegna preferita/provvisoria. Stop salva subito: nessuna attesa per waveform o Internet.");
    JSONArray lines=active.optJSONArray("lines");ArrayList<String> actors=new ArrayList<>();actors.add("Tutti");if(lines!=null)for(int i=0;i<lines.length();i++){JSONObject l=lines.optJSONObject(i);if(l!=null){String name=MobileStore.actorName(l,active,cast);if(!actors.contains(name))actors.add(name);}}
    Spinner filter=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,actors);filter.setAdapter(adapter);int selection=actors.indexOf(actorFilter);filter.setSelection(Math.max(0,selection));body.addView(filter);
    filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int index,long id){String newValue=actors.get(index);if(!newValue.equals(actorFilter)){actorFilter=newValue;show();}}});
    int ready=0,total=0;LinearLayout cells=column();if(lines!=null)for(int i=0;i<lines.length();i++){
      JSONObject line=lines.optJSONObject(i);if(line==null)continue;String who=MobileStore.actorName(line,active,cast);if(!actorFilter.equals("Tutti")&&!actorFilter.equals(who))continue;
      total++;String id=MobileStore.str(line,"id");File[] takeFiles=store.takes(pid(),id);boolean complete=takeFiles.length>0&&!"rerecord".equals(MobileStore.str(line,"status"));if(complete)ready++;
      LinearLayout cell=column();cell.setPadding(dp(13),dp(12),dp(13),dp(12));cell.setBackground(box(complete?GREEN:RED,9,id.equals(selectedLine)?PURPLE:Color.rgb(75,61,83),id.equals(selectedLine)?4:1));
      cell.addView(label(MobileStore.characterName(line,cast)+"   ·   "+who+"   ·   "+takeFiles.length+" take",13,TEXT,true));cell.addView(label(MobileStore.str(line,"text"),16,TEXT,false));
      cell.setOnClickListener(v->{selectedLine=id;showTakeDialog(line);});LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.setMargins(0,dp(4),0,dp(5));cells.addView(cell,layout);
    }
    body.addView(label("Completate: "+ready+" / "+total+" battute",16,YELLOW,true));body.addView(cells);
  }

  private void showTakeDialog(JSONObject line){
    if(takeDialog!=null&&takeDialog.isShowing())takeDialog.dismiss();
    String lid=MobileStore.str(line,"id");selectedLine=lid;
    ScrollView scroll=new ScrollView(this);LinearLayout pane=column();pad(pane);scroll.addView(pane);
    pane.addView(label(MobileStore.characterName(line,cast)+" · "+MobileStore.actorName(line,active,cast),14,PURPLE,true));
    TextView lineText=label(MobileStore.str(line,"text"),20,TEXT,true);
    lineText.setPadding(dp(14),dp(16),dp(14),dp(16));
    lineText.setBackground(box(Color.rgb(29,26,44),13,Color.rgb(70,57,89),1));
    pane.addView(lineText);
    TextView heading=label("REGISTRATORE",12,YELLOW,true);heading.setGravity(Gravity.CENTER);pane.addView(heading);
    recordingClock=new Chronometer(this);recordingClock.setTextColor(TEXT);
    recordingClock.setTextSize(32);recordingClock.setGravity(Gravity.CENTER);
    recordingClock.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
    if(recorder!=null){recordingClock.setBase(SystemClock.elapsedRealtime());recordingClock.start();}
    pane.addView(recordingClock,new LinearLayout.LayoutParams(-1,dp(70)));
    LinearLayout center=row();center.setGravity(Gravity.CENTER);
    recorderButton=new Button(this);recorderButton.setAllCaps(false);
    recorderButton.setText(recorder==null?"● REC":"■ STOP");
    recorderButton.setTextColor(Color.WHITE);recorderButton.setTextSize(22);
    recorderButton.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    recorderButton.setBackground(box(recorder==null?Color.rgb(205,46,66):Color.rgb(130,38,53),90,Color.rgb(252,184,190),3));
    recorderButton.setOnClickListener(v->{if(recorder==null)startRecord(lid,false);else stopRecord();});
    LinearLayout.LayoutParams recLp=new LinearLayout.LayoutParams(dp(132),dp(132));
    recLp.setMargins(0,dp(5),0,dp(8));center.addView(recorderButton,recLp);pane.addView(center);
    TextView recordHint=label("Tocca REC per una nuova take · STOP salva subito",12,Color.LTGRAY,false);
    recordHint.setGravity(Gravity.CENTER);pane.addView(recordHint);
    LinearLayout shortcuts=actionRow();shortcuts.setGravity(Gravity.CENTER);
    shortcuts.addView(btn("＋ Importa file",PANEL,()->{
      Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("audio/*");
      startActivityForResult(intent,IMPORT_TAKE);
    }));
    shortcuts.addView(btn("↻ Rifai",PURPLE,()->{
      if(recorder!=null)toast("Ferma prima la registrazione");else startRecord(lid,false);
    }));
    pane.addView(shortcuts);
    File[] files=store.takes(pid(),lid);
    pane.addView(label("TAKE SALVATE · "+files.length,13,YELLOW,true));
    if(files.length==0)pane.addView(label("Nessuna take. Inizia dal tasto rosso.",14,Color.rgb(240,160,163),false));
    for(int index=files.length-1;index>=0;index--){
      File file=files[index];String filename=file.getName();
      boolean preferred=store.isPreferred(pid(),lid,filename);
      boolean provisional=store.isProvisional(pid(),lid,filename);
      boolean selected=filename.equals(selectedTakeName)||(selectedTakeName.isEmpty()&&index==files.length-1);
      LinearLayout take=column();take.setPadding(dp(7),dp(6),dp(7),dp(6));
      take.setBackground(box(selected?Color.rgb(60,48,83):PANEL,11,selected?YELLOW:Color.rgb(72,60,87),selected?2:1));
      take.addView(label("Take "+(index+1)+(preferred?"  ★ PREFERITA":"")+(provisional?"  · PROVVISORIA":""),13,provisional?YELLOW:TEXT,true));
      take.setOnClickListener(v->{selectedTakeName=filename;showTakeDialog(line);});
      HorizontalScrollView scroller=new HorizontalScrollView(this);scroller.setHorizontalScrollBarEnabled(false);
      LinearLayout tools=actionRow();
      tools.addView(btn("▶",GREEN,()->{selectedTakeName=filename;play(file);}));
      tools.addView(btn(preferred?"★ Preferita":"☆ Preferita",PURPLE,()->{
        selectedTakeName=filename;store.setPreferred(pid(),lid,filename);showTakeDialog(line);
      }));
      tools.addView(btn(provisional?"✓ Provvisoria":"◌ Provvisoria",BROWN,()->{
        selectedTakeName=filename;store.setProvisional(pid(),lid,filename,!provisional);showTakeDialog(line);
      }));
      tools.addView(btn("✕ Elimina",RED,()->confirm("Eliminare la take?",
        "La registrazione sarà eliminata dal telefono; il PC sarà aggiornato alla sincronizzazione.",
        "Elimina",()->{if(file.delete()){store.queueDeletion(pid(),lid,filename);selectedTakeName="";showTakeDialog(line);toast("Take eliminata");}
          else toast("Eliminazione fallita");})));
      scroller.addView(tools);take.addView(scroller);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(4));pane.addView(take,lp);
    }
    takeDialog=new AlertDialog.Builder(this).setTitle("Doppiaggio · Battuta").setView(scroll)
      .setPositiveButton("Chiudi",null).create();
    takeDialog.setOnDismissListener(dialog->{if(recordingClock!=null){recordingClock.stop();recordingClock=null;}recorderButton=null;});
    takeDialog.show();
    takeDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
      if(recorder!=null){toast("Ferma e salva la registrazione prima di chiudere");return;}
      takeDialog.dismiss();show();
    });
    takeDialog.setCancelable(recorder==null);
    takeDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  }
  private void startRecord(String lid,boolean provisional){if(recorder!=null){toast("Registrazione in corso");return;}if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);toast("Autorizza microfono e premi di nuovo Registra");return;}
    try{recordingLine=lid;recordingProvisional=provisional;recordingFile=new File(store.takeDir(pid(),lid),"take_"+System.currentTimeMillis()+".m4a");recorder=new MediaRecorder();recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioSamplingRate(44100);recorder.setAudioEncodingBitRate(128000);recorder.setOutputFile(recordingFile.getAbsolutePath());recorder.prepare();recorder.start();if(recorderButton!=null){recorderButton.setText("■ STOP");recorderButton.setBackground(box(Color.rgb(130,38,53),90,Color.rgb(252,184,190),3));}if(recordingClock!=null){recordingClock.setBase(SystemClock.elapsedRealtime());recordingClock.start();}toast("Registrazione avviata");}
    catch(Exception ex){if(recorder!=null){try{recorder.release();}catch(Exception ignored){}}recorder=null;toast("Microfono: "+ex.getMessage());}
  }
  private void stopRecord(){if(recorder==null){toast("Nessuna registrazione attiva");return;}boolean saved=false;try{recorder.stop();saved=recordingFile!=null&&recordingFile.length()>0;}catch(Exception ex){toast("Errore durante lo Stop: "+ex.getMessage());}finally{recorder.release();recorder=null;}
    if(saved){selectedTakeName=recordingFile.getName();if(recordingProvisional)store.setProvisional(pid(),recordingLine,recordingFile.getName(),true);toast("Take salvata immediatamente sul telefono");}else if(recordingFile!=null)recordingFile.delete();
    if(recordingClock!=null)recordingClock.stop();JSONObject line=MobileStore.byId(active.optJSONArray("lines"),recordingLine);recordingLine="";if(line!=null)showTakeDialog(line);if(saved)ui.postDelayed(this::autoSync,700);
  }
  private void play(File audio){if(player!=null){try{player.stop();player.release();}catch(Exception ignored){}player=null;}try{player=new MediaPlayer();player.setDataSource(audio.getAbsolutePath());player.prepare();player.start();player.setOnCompletionListener(p->{p.release();player=null;});}catch(Exception ex){toast("Impossibile riprodurre: "+ex.getMessage());}}
  private void renderSync(){section("Sincronizzazione con Windows",YELLOW);note("La Wi-Fi privata sincronizza copioni, cast e take tra telefono e PC. Le modifiche offline restano locali finché non viene stabilita la connessione.");
    addButton(body,"⚙ Collega PC / codice",PURPLE,this::pairDialog);
    addButton(body,"✓ Verifica collegamento reale al PC",GREEN,()->background(()->{
      try{
        JSONObject status=sync.state();runOnUiThread(()->{
          lastSync="PC raggiungibile · "+status.optJSONArray("projects").length()+" episodi disponibili";
          toast(lastSync);if(page.equals("Sincronizza"))show();
        });
      }catch(Exception ex){error(new IOException("Connessione non riuscita: "+ex.getMessage()));}
    }));
    addButton(body,"⇅ Invia e ricevi modifiche",GREEN,this::syncAll);
    addButton(body,"↓ Importa un progetto già esistente sul PC",PANEL,this::chooseDesktopProject);
    note("Indirizzo: "+(sync.url().isEmpty()?"PC non configurato":sync.url()));note("Ultimo risultato: "+lastSync);
    note("Registri offline anche con PC spento. Quando riapri l’app sulla stessa Wi-Fi privata del PC, tenta automaticamente il trasferimento. Il pulsante manuale resta disponibile. Sul PC avvia AVVIA_PC_ANDROID.bat. Non usare reti pubbliche né aprire la porta verso Internet: HTTP LAN non è cifrato.");
    addButton(body,"Genera la timeline sul PC",PURPLE,()->requireProject(()->background(()->{try{sync.post("/api/sync/projects/"+pid()+"/assemble",MobileStore.obj());runOnUiThread(()->toast("Bozza timeline generata sul PC"));}catch(Exception e){error(e);}})));
  }
  private void pairDialog(){LinearLayout l=column();pad(l);EditText url=new EditText(this);url.setHint("http://192.168.1.2:18766");url.setText(sync.url());l.addView(url);EditText key=new EditText(this);key.setHint("Codice di abbinamento PC");key.setText(sync.token());l.addView(key);
    new AlertDialog.Builder(this).setTitle("Abbinamento PC").setView(l).setPositiveButton("Salva",(d,w)->{try{sync.setup(url.getText().toString().trim().replaceAll("/+$",""),key.getText().toString());toast("Dati PC salvati. Premi Verifica collegamento per controllare la rete.");show();}catch(Exception e){toast(e.getMessage());}}).setNegativeButton("Annulla",null).show();
  }
  private void background(Runnable run){new Thread(run,"maf-mobile-sync").start();}
  private void error(Exception ex){runOnUiThread(()->{lastSync=ex.getMessage();toast("Operazione interrotta: "+ex.getMessage());if(page.equals("Sincronizza"))show();});}
  private void syncAll(){if(syncBusy){toast("Sincronizzazione già in corso");return;}syncBusy=true;lastSync="Invio in corso...";toast(lastSync);background(()->{try{MobileSync.Result result=sync.synchronizeAll();runOnUiThread(()->{syncBusy=false;lastSync="Completata: "+result.message();show();toast(lastSync);});}catch(Exception ex){syncBusy=false;error(ex);}});}
  private void chooseDesktopProject(){background(()->{try{JSONObject state=sync.state();JSONArray projects=state.getJSONArray("projects");String[] choices=new String[projects.length()];String[] ids=new String[choices.length];for(int i=0;i<projects.length();i++){JSONObject p=projects.getJSONObject(i);ids[i]=p.getString("id");choices[i]=p.getString("title");}
    runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Importa episodio dal PC").setItems(choices,(d,which)->background(()->{try{JSONObject full=sync.download(ids[which]);runOnUiThread(()->{try{store.importFromPC(full);cast=store.cast();setProject(store.project(ids[which]));toast("Progetto importato sul telefono");}catch(Exception ex){toast(ex.getMessage());}});}catch(Exception ex){error(ex);}})).setNegativeButton("Annulla",null).show());
    }catch(Exception ex){error(ex);}});}
  @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(result!=RESULT_OK||intent==null)return;try{
    if(request==IMPORT_WORD){if(editor==null)return;try(InputStream input=getContentResolver().openInputStream(intent.getData())){String imported=MobileStore.docxText(input);editor.getText().insert(editor.getSelectionStart(),imported);toast("Word importato. Controlla i tag e salva.");}}
    else if(request==DICTATE){ArrayList<String> results=intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(editor!=null&&results!=null&&!results.isEmpty())editor.getText().insert(Math.max(0,editor.getSelectionStart()),results.get(0));}
    else if(request==IMPORT_TAKE&&active!=null&&!selectedLine.isEmpty()){
      String lid=selectedLine;String extension=".m4a";String name="take_import_"+System.currentTimeMillis();String type=getContentResolver().getType(intent.getData());
      if(type!=null){if(type.contains("wav"))extension=".wav";else if(type.contains("mpeg"))extension=".mp3";else if(type.contains("ogg"))extension=".ogg";else if(type.contains("flac"))extension=".flac";else if(type.contains("aac"))extension=".aac";}
      File destination=new File(store.takeDir(pid(),lid),name+extension);try(InputStream in=getContentResolver().openInputStream(intent.getData());FileOutputStream out=new FileOutputStream(destination)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}JSONObject line=MobileStore.byId(active.optJSONArray("lines"),lid);selectedTakeName=destination.getName();if(line!=null)showTakeDialog(line);toast("Audio importato");
    }
    else if(request==IMPORT_EPISODE){ /* reserved for migration */ }
    }catch(Exception ex){toast("File: "+ex.getMessage());}}
  @Override public void onBackPressed(){if(recorder!=null){confirm("Registrazione in corso","Fermare e salvare la registrazione prima di uscire?","Stop e salva",this::stopRecord);return;}if(!page.equals("Progetti")){openPage("Progetti");return;}super.onBackPressed();}
  @Override protected void onDestroy(){if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();}if(player!=null)player.release();dismissSuggestions();super.onDestroy();}
}