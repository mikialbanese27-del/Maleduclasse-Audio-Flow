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
  private static final int MIC=101,IMPORT_WORD=102,IMPORT_TAKE=103,DICTATE=104,IMPORT_EPISODE=105,IMPORT_AVATAR=106;
  private String avatarTarget="";
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
    if(sync==null||sync.url().isEmpty()||syncBusy||recorder!=null||recordingPending||editorDirty||!wifiConnected()||
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
    View brand=BrandLogo.view(this);
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
    {
      LinearLayout bottom=row();bottom.setGravity(Gravity.CENTER);
      bottom.setPadding(dp(6),dp(5),dp(6),dp(7));bottom.setBackgroundColor(Color.rgb(26,23,36));
      bottom.addView(navButton("▦","Progetti","Progetti"),new LinearLayout.LayoutParams(0,dp(60),1));
      bottom.addView(navButton("✎","Scrivi","Sceneggiatura"),new LinearLayout.LayoutParams(0,dp(60),1));
      bottom.addView(navButton("◉","Doppiaggio","Doppiaggio"),new LinearLayout.LayoutParams(0,dp(60),1));
      root.addView(bottom);
      final View navBar=bottom;
      final View observed=root;
      observed.getViewTreeObserver().addOnGlobalLayoutListener(()->{
        android.graphics.Rect visible=new android.graphics.Rect();
        observed.getWindowVisibleDisplayFrame(visible);
        int height=observed.getRootView().getHeight();
        boolean keyboard=height-visible.bottom>dp(160);
        int desired=keyboard?View.GONE:View.VISIBLE;
        if(navBar.getVisibility()!=desired)navBar.setVisibility(desired);
      });
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
  private void projectActions(View anchor,JSONObject p){
    String id=MobileStore.str(p,"project_id");PopupMenu menu=new PopupMenu(this,anchor);
    menu.getMenu().add("Rinomina").setOnMenuItemClickListener(item->{
      askText("Rinomina episodio",MobileStore.str(p,"title"),name->{
        MobileStore.put(p,"title",name);MobileStore.put(p,"dirty",true);store.saveProject(p);show();
      });return true;
    });
    menu.getMenu().add("Duplica").setOnMenuItemClickListener(item->{
      setProject(store.duplicateProject(p));toast("Copia creata senza duplicare le registrazioni");return true;
    });
    menu.getMenu().add("Archivia").setOnMenuItemClickListener(item->{
      confirm("Archivia episodio?","L'episodio viene nascosto solo su Android.","Archivia",()->{
        store.trashProject(p);if(id.equals(pid()))active=null;show();
      });return true;
    });
    menu.getMenu().add("Elimina definitivamente").setOnMenuItemClickListener(item->{
      confirm("Eliminazione definitiva","Elimina il progetto e le take SOLO su questo dispositivo. Il PC non viene modificato.","Elimina",()->{
        store.deletePermanently(id);if(id.equals(pid()))active=null;show();
      });return true;
    });
    menu.show();
  }
  private void renderProjects(){
    section("I tuoi episodi",TEXT);
    addButton(body,"＋ Nuovo episodio",PURPLE,()->askText("Titolo del nuovo episodio","",name->setProject(store.createProject(name))));
    for(JSONObject p:store.projects()){
      String id=MobileStore.str(p,"project_id");
      LinearLayout panel=card(Color.rgb(33,29,44));panel.setPadding(dp(16),dp(14),dp(12),dp(14));
      LinearLayout row=row();
      LinearLayout summary=column();
      summary.addView(label(MobileStore.str(p,"title"),18,TEXT,true));
      JSONArray lines=p.optJSONArray("lines");
      String status=p.optBoolean("dirty",false)?"Da sincronizzare":"Sul dispositivo";
      summary.addView(label((lines==null?0:lines.length())+" battute  ·  "+status,12,Color.rgb(168,162,181),false));
      row.addView(summary,new LinearLayout.LayoutParams(0,-2,1));
      Button more=btn("⋮",PANEL,()->{});more.setTextSize(23);
      more.setOnClickListener(v->projectActions(v,p));row.addView(more);
      panel.addView(row);panel.setOnClickListener(v->setProject(store.project(id)));
    }
    if(!store.archivedProjects().isEmpty()){
      body.addView(label("Archivio",16,Color.rgb(175,164,188),true));
      for(JSONObject p:store.archivedProjects()){
        String id=MobileStore.str(p,"project_id");
        LinearLayout cell=card(PANEL);LinearLayout horizontal=row();
        horizontal.addView(label(MobileStore.str(p,"title"),15,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        Button more=btn("⋮",PANEL,()->{});more.setTextSize(22);
        more.setOnClickListener(v->{
          PopupMenu menu=new PopupMenu(this,v);
          menu.getMenu().add("Ripristina").setOnMenuItemClickListener(item->{store.restoreProject(p);show();return true;});
          menu.getMenu().add("Elimina definitivamente").setOnMenuItemClickListener(item->{
            confirm("Eliminare definitivamente?","Questo progetto e le take locali verranno rimossi.","Elimina",()->{store.deletePermanently(id);show();});
            return true;
          });menu.show();
        });horizontal.addView(more);cell.addView(horizontal);
      }
    }
  }
  private void renderScript(){if(active==null){section("Sceneggiatura",PURPLE);note("Apri o crea un progetto prima di scrivere il copione.");return;}
    section("Sceneggiatura · "+MobileStore.str(active,"title"),Color.rgb(95,172,246));

    editor=new EditText(this);editor.setTextColor(TEXT);editor.setHintTextColor(Color.GRAY);editor.setHint("/scena Scena 1\n@Personaggio\nScrivi qui la battuta...");editor.setTextSize(17);
    editor.setGravity(Gravity.TOP);editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
    editor.setTypeface(Typeface.MONOSPACE);editor.setPadding(dp(14),dp(14),dp(14),dp(14));editor.setBackground(box(Color.rgb(25,31,49),10,PURPLE,1));editor.setMinLines(14);editor.setMinimumHeight(dp(350));
    draft=MobileStore.str(active,"script");editor.setText(draft);editor.setSelection(editor.length());body.addView(editor,new LinearLayout.LayoutParams(-1,-2));applySyntax();
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
    addCharacterShortcuts();
  }
  private File avatarFile(String charId){return new File(new File(getFilesDir(),"avatars"),charId+".png");}
  private View characterFace(JSONObject character,int size){
    String id=MobileStore.str(character,"id");File avatar=avatarFile(id);
    if(avatar.exists()){
      android.widget.ImageView image=new android.widget.ImageView(this);
      image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
      image.setImageURI(android.net.Uri.fromFile(avatar));
      image.setBackground(box(PANEL,12,PURPLE,1));
      image.setClipToOutline(true);
      return image;
    }
    String name=MobileStore.str(character,"name");
    String initials=name.trim().isEmpty()?"?":name.trim().substring(0,1).toUpperCase(java.util.Locale.ROOT);
    TextView fallback=label(initials,20,TEXT,true);fallback.setGravity(Gravity.CENTER);
    fallback.setBackground(box(Color.rgb(68,53,91),12,PURPLE,1));
    return fallback;
  }
  private void insertCharacter(JSONObject character){
    if(editor==null)return;
    insertScriptToken("@"+MobileStore.str(character,"name"));
    String key="used_"+pid()+"_"+MobileStore.str(character,"id");
    android.content.SharedPreferences prefs=getPreferences(0);
    prefs.edit().putInt(key,prefs.getInt(key,0)+1).apply();
  }
  private void addCharacterShortcuts(){
    JSONArray chars=cast.optJSONArray("characters");if(chars==null||chars.length()==0)return;
    ArrayList<JSONObject> sorted=new ArrayList<>();
    for(int i=0;i<chars.length();i++){JSONObject ch=chars.optJSONObject(i);if(ch!=null)sorted.add(ch);}
    android.content.SharedPreferences prefs=getPreferences(0);
    sorted.sort((x,y)->Integer.compare(
      prefs.getInt("used_"+pid()+"_"+MobileStore.str(y,"id"),0),
      prefs.getInt("used_"+pid()+"_"+MobileStore.str(x,"id"),0)));
    HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);
    LinearLayout row=row();row.setPadding(dp(4),dp(2),dp(4),dp(2));
    for(JSONObject character:sorted){
      View face=characterFace(character,40);
      LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(40),dp(40));fp.setMargins(dp(3),0,dp(9),0);
      row.addView(face,fp);face.setContentDescription("Inserisci "+MobileStore.str(character,"name"));
      face.setOnClickListener(v->{insertCharacter(character);});
    }
    strip.addView(row);root.addView(strip);
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
  private void chooseAvatar(JSONObject character){
    avatarTarget=MobileStore.str(character,"id");
    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("image/*");startActivityForResult(intent,IMPORT_AVATAR);
  }
  private void renderCast(){section("Cast e personaggi",TEXT);note("I doppiatori e personaggi sono condivisi tra gli episodi. Ogni episodio può avere assegnazioni diverse.");
    addButton(body,"＋ Aggiungi doppiatore",GREEN,()->askText("Nuovo doppiatore","",s->{JSONArray actors=cast.optJSONArray("actors");if(MobileStore.byName(actors,s)!=null){toast("Doppiatore già presente");return;}actors.put(MobileStore.put(MobileStore.put(MobileStore.obj(),"id",MobileStore.id()),"name",s));store.saveCast(cast);show();}));
    JSONArray actors=cast.optJSONArray("actors");for(int i=0;actors!=null&&i<actors.length();i++){JSONObject a=actors.optJSONObject(i);if(a==null)continue;LinearLayout panel=card(PANEL);LinearLayout r=actionRow();TextView title=label("🎙 "+MobileStore.str(a,"name"),16,TEXT,true);r.addView(title,new LinearLayout.LayoutParams(0,-2,1));r.addView(btn("Rinomina",PANEL,()->askText("Rinomina doppiatore",MobileStore.str(a,"name"),name->{MobileStore.put(a,"name",name);store.saveCast(cast);show();})));r.addView(btn("Elimina",RED,()->deleteCastActor(a)));panel.addView(r);}
    addButton(body,"＋ Aggiungi personaggio",PURPLE,()->askText("Nome nuovo personaggio","",s->{JSONArray list=cast.optJSONArray("characters");if(MobileStore.byName(list,s)!=null){toast("Personaggio già presente");return;}list.put(MobileStore.put(MobileStore.put(MobileStore.put(MobileStore.obj(),"id",MobileStore.id()),"name",s),"actor_id",JSONObject.NULL));store.saveCast(cast);show();}));
    JSONArray characters=cast.optJSONArray("characters");for(int i=0;characters!=null&&i<characters.length();i++){JSONObject ch=characters.optJSONObject(i);if(ch==null)continue;LinearLayout panel=card(PANEL);LinearLayout identity=row();
      identity.addView(characterFace(ch,44),new LinearLayout.LayoutParams(dp(44),dp(44)));
      identity.addView(label("@"+MobileStore.str(ch,"name"),16,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
      identity.addView(btn("▧",PANEL,()->chooseAvatar(ch)));panel.addView(identity);
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
  private void openDubbingFilter(View anchor){
    ArrayList<String> options=new ArrayList<>();
    options.add("Tutti");
    JSONArray lines=active==null?null:active.optJSONArray("lines");
    if(lines!=null)for(int i=0;i<lines.length();i++){
      JSONObject line=lines.optJSONObject(i);if(line==null)continue;
      String actor=MobileStore.actorName(line,active,cast);
      String character=MobileStore.characterName(line,cast);
      if(!options.contains("Doppiatore: "+actor))options.add("Doppiatore: "+actor);
      if(!options.contains("Personaggio: "+character))options.add("Personaggio: "+character);
    }
    new AlertDialog.Builder(this).setTitle("Filtra le battute").setItems(options.toArray(new String[0]),(d,index)->{
      actorFilter=options.get(index);show();
    }).setNegativeButton("Annulla",null).show();
  }
  private boolean lineMatchesFilter(JSONObject line){
    if(actorFilter.equals("Tutti"))return true;
    if(actorFilter.startsWith("Doppiatore: "))return actorFilter.substring(12).equals(MobileStore.actorName(line,active,cast));
    if(actorFilter.startsWith("Personaggio: "))return actorFilter.substring(13).equals(MobileStore.characterName(line,cast));
    // Legacy filter stored from the previous alpha.
    return actorFilter.equals(MobileStore.actorName(line,active,cast));
  }
  private void sendTimelineToPc(){
    if(active==null)return;
    if(syncBusy){toast("Sincronizzazione già in corso");return;}
    syncBusy=true;
    background(()->{
      try{
        // Always send pending edits and accepted takes before assembling.
        MobileSync.Result result=sync.synchronizeAll();
        JSONObject assembly=sync.post("/api/sync/projects/"+pid()+"/assemble",MobileStore.obj());
        runOnUiThread(()->{
          syncBusy=false;pcOnline=true;updateConnectionDot();
          toast("Timeline PC aggiornata · "+assembly.optInt("inserted",0)+" nuove clip");
        });
      }catch(Exception ex){runOnUiThread(()->{syncBusy=false;pcOnline=false;updateConnectionDot();toast("Invio timeline non riuscito: "+ex.getMessage());});}
    });
  }
  private void renderDubbing(){
    if(active==null){section("Doppiaggio",TEXT);return;}
    LinearLayout top=row();
    TextView title=label("Doppiaggio",20,TEXT,true);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
    Button filter=btn("☷",PANEL,()->{});filter.setTextSize(22);
    filter.setContentDescription("Filtra per doppiatore o personaggio");
    filter.setOnClickListener(v->openDubbingFilter(v));top.addView(filter);body.addView(top);
    if(!actorFilter.equals("Tutti")){
      TextView activeFilter=label(actorFilter+"   ×",12,Color.rgb(177,155,219),false);
      activeFilter.setOnClickListener(v->{actorFilter="Tutti";show();});body.addView(activeFilter);
    }
    JSONArray lines=active.optJSONArray("lines");
    int ready=0,total=0;LinearLayout cells=column();
    if(lines!=null)for(int i=0;i<lines.length();i++){
      JSONObject line=lines.optJSONObject(i);if(line==null||!lineMatchesFilter(line))continue;
      total++;String id=MobileStore.str(line,"id");
      File[] takes=store.takes(pid(),id);
      boolean complete=takes.length>0&&!"rerecord".equals(MobileStore.str(line,"status"));
      if(complete)ready++;
      // Keep the approved red/green cells and yellow selection stroke unchanged.
      LinearLayout cell=column();cell.setPadding(dp(13),dp(12),dp(13),dp(12));
      cell.setBackground(box(complete?GREEN:RED,9,
        id.equals(selectedLine)?PURPLE:Color.rgb(75,61,83),id.equals(selectedLine)?4:1));
      cell.addView(label(MobileStore.characterName(line,cast)+"   ·   "+MobileStore.actorName(line,active,cast)+"   ·   "+takes.length+" take",13,TEXT,true));
      cell.addView(label(MobileStore.str(line,"text"),16,TEXT,false));
      cell.setOnClickListener(v->{selectedLine=id;showTakeDialog(line);});
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
      lp.setMargins(0,dp(4),0,dp(5));cells.addView(cell,lp);
    }
    body.addView(label(ready+" / "+total+" battute completate",12,Color.rgb(163,162,173),false));
    body.addView(cells);
    Button finish=btn("✓  Invia alla timeline su PC",PURPLE,this::sendTimelineToPc);
    finish.setTextSize(15);
    LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(55));
    fp.setMargins(0,dp(17),0,dp(6));body.addView(finish,fp);
  }

  private void showTakeDialog(JSONObject line){
    if(takeDialog!=null&&takeDialog.isShowing())takeDialog.dismiss();
    String lid=MobileStore.str(line,"id");selectedLine=lid;
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
    LinearLayout pane=column();pane.setPadding(dp(15),dp(10),dp(15),dp(14));
    pane.setBackgroundColor(Color.rgb(20,18,28));scroll.addView(pane);
    LinearLayout top=row();
    TextView who=label(MobileStore.characterName(line,cast),15,Color.rgb(194,176,236),true);
    top.addView(who,new LinearLayout.LayoutParams(0,-2,1));
    Button more=btn("⋮",Color.rgb(40,36,51),()->{});more.setTextSize(23);
    more.setOnClickListener(v->{
      PopupMenu menu=new PopupMenu(this,v);
      menu.getMenu().add("Importa audio da file").setOnMenuItemClickListener(item->{
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("audio/*");
        startActivityForResult(intent,IMPORT_TAKE);return true;
      });menu.show();
    });top.addView(more);pane.addView(top);
    TextView script=label(MobileStore.str(line,"text"),18,TEXT,true);
    script.setPadding(dp(14),dp(14),dp(14),dp(14));
    script.setBackground(box(Color.rgb(34,31,46),14,Color.rgb(52,45,70),1));
    pane.addView(script);
    recordingClock=new Chronometer(this);recordingClock.setGravity(Gravity.CENTER);
    recordingClock.setTextColor(TEXT);recordingClock.setTextSize(29);
    recordingClock.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);
    if(recorder!=null){recordingClock.setBase(SystemClock.elapsedRealtime());recordingClock.start();}
    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(65));cp.setMargins(0,dp(8),0,0);
    pane.addView(recordingClock,cp);
    LinearLayout center=row();center.setGravity(Gravity.CENTER);
    recorderButton=new Button(this);recorderButton.setAllCaps(false);recorderButton.setTextColor(TEXT);
    recorderButton.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    recorderButton.setOnClickListener(v->{if(recorder==null)startRecord(lid,false);else stopRecord();});
    LinearLayout.LayoutParams recLp=new LinearLayout.LayoutParams(dp(124),dp(124));
    recLp.setMargins(0,0,0,dp(14));center.addView(recorderButton,recLp);pane.addView(center);
    renderRecorderState();
    File[] files=store.takes(pid(),lid);
    pane.addView(label("LE TUE TAKE  ·  "+files.length,12,Color.rgb(156,155,171),true));
    for(int index=files.length-1;index>=0;index--){
      File file=files[index];String filename=file.getName();
      boolean preferred=store.isPreferred(pid(),lid,filename),provisional=store.isProvisional(pid(),lid,filename);
      LinearLayout take=column();take.setPadding(dp(10),dp(5),dp(8),dp(5));
      take.setBackground(box(Color.rgb(37,34,49),12,Color.rgb(64,54,78),1));
      LinearLayout header=row();
      header.addView(label("TAKE "+(index+1),12,Color.rgb(187,184,200),true),new LinearLayout.LayoutParams(0,-2,1));
      Button remove=btn("✕",Color.rgb(58,37,48),()->{});
      remove.setTextColor(Color.rgb(244,109,126));remove.setTextSize(17);
      remove.setContentDescription("Elimina take");
      remove.setOnClickListener(v->confirm("Elimina take?","La registrazione sarà rimossa dal telefono e la cancellazione sincronizzata.","Elimina",()->{
        if(file.delete()){store.queueDeletion(pid(),lid,filename);selectedTakeName="";showTakeDialog(line);}
        else toast("Impossibile eliminare il file");
      }));header.addView(remove);take.addView(header);
      LinearLayout controls=row();
      Button play=btn("▶",Color.rgb(56,57,73),()->play(file));play.setTextSize(19);
      controls.addView(play,new LinearLayout.LayoutParams(0,dp(47),1));
      Button star=btn(preferred?"★":"☆",preferred?Color.rgb(78,61,106):Color.rgb(56,57,73),()->{
        store.setPreferred(pid(),lid,filename);showTakeDialog(line);
      });star.setTextSize(22);star.setContentDescription("Contrassegna come preferita");
      controls.addView(star,new LinearLayout.LayoutParams(0,dp(47),1));
      Button provisionalButton=btn(provisional?"◷":"◴",provisional?Color.rgb(78,61,106):Color.rgb(56,57,73),()->{
        store.setProvisional(pid(),lid,filename,!provisional);showTakeDialog(line);
      });provisionalButton.setTextSize(23);
      provisionalButton.setContentDescription(provisional?"Take provvisoria: tocca per cambiare":"Contrassegna provvisoria");
      controls.addView(provisionalButton,new LinearLayout.LayoutParams(0,dp(47),1));
      take.addView(controls);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
      lp.setMargins(0,dp(4),0,dp(7));pane.addView(take,lp);
    }
    takeDialog=new AlertDialog.Builder(this).setView(scroll).create();
    takeDialog.setOnDismissListener(d->{
      if(recordingClock!=null){recordingClock.stop();recordingClock=null;}
      recorderButton=null;
    });takeDialog.show();
    takeDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    takeDialog.getWindow().setLayout(-1,-2);
    takeDialog.setCancelable(recorder==null);
    // Hardware back cannot silently stop an active recording.
  }
  private void renderRecorderState(){
    if(recorderButton==null)return;
    boolean recording=recorder!=null;
    recorderButton.setText(recording?"■ STOP":"● REC");
    recorderButton.setTextSize(recording?19:22);
    recorderButton.setBackground(box(recording?Color.rgb(100,43,56):Color.rgb(198,45,69),
      90,recording?Color.rgb(255,181,195):Color.rgb(242,129,151),3));
    recorderButton.setContentDescription(recording?"Interrompi registrazione e ascolta":"Inizia registrazione");
  }
  private void startRecord(String lid,boolean provisional){
    if(recorder!=null){toast("Registrazione già in corso");return;}
    if(recordingPending){toast("Conferma oppure scarta la take precedente");return;}
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);
      toast("Autorizza il microfono e poi premi REC");return;
    }
    try{
      recordingLine=lid;recordingProvisional=provisional;
      // Pending audio stays outside takes/, so it cannot sync before user acceptance.
      File pendingDir=new File(getFilesDir(),"pending_takes");pendingDir.mkdirs();
      recordingFile=new File(pendingDir,"take_"+System.currentTimeMillis()+".m4a");
      recorder=new MediaRecorder();recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
      recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
      recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
      recorder.setAudioSamplingRate(44100);recorder.setAudioEncodingBitRate(128000);
      recorder.setOutputFile(recordingFile.getAbsolutePath());recorder.prepare();recorder.start();
      capturedPeaks.clear();ui.removeCallbacks(sampleAmplitude);ui.postDelayed(sampleAmplitude,110);
      if(recordingClock!=null){recordingClock.setBase(SystemClock.elapsedRealtime());recordingClock.start();}
      if(takeDialog!=null)takeDialog.setCancelable(false);
      renderRecorderState();
    }catch(Exception ex){
      ui.removeCallbacks(sampleAmplitude);
      if(recorder!=null){try{recorder.release();}catch(Exception ignored){}}
      recorder=null;if(recordingFile!=null)recordingFile.delete();
      renderRecorderState();toast("Microfono: "+ex.getMessage());
    }
  }
  private void stopRecord(){
    if(recorder==null){toast("Premi REC per iniziare");renderRecorderState();return;}
    ui.removeCallbacks(sampleAmplitude);
    boolean valid=false;
    try{recorder.stop();valid=recordingFile!=null&&recordingFile.length()>256;}
    catch(Exception ex){toast("Registrazione non valida: "+ex.getMessage());}
    finally{recorder.release();recorder=null;renderRecorderState();}
    if(recordingClock!=null)recordingClock.stop();
    if(takeDialog!=null)takeDialog.setCancelable(true);
    if(!valid){
      if(recordingFile!=null)recordingFile.delete();recordingFile=null;
      toast("Take non registrata: riprova");return;
    }
    recordingPending=true;
    JSONObject line=MobileStore.byId(active.optJSONArray("lines"),recordingLine);
    showRecordReview(line);
  }
  private View waveformPreview(){
    final ArrayList<Integer> peaks=new ArrayList<>(capturedPeaks);
    return new View(this){
      final android.graphics.Paint paint=new android.graphics.Paint(3);
      @Override protected void onDraw(android.graphics.Canvas canvas){
        super.onDraw(canvas);int w=getWidth(),h=getHeight();
        paint.setColor(Color.rgb(176,139,239));paint.setStrokeWidth(dp(3));paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        if(peaks.isEmpty())return;
        int count=Math.min(70,peaks.size());
        for(int i=0;i<count;i++){
          int idx=(int)((long)i*peaks.size()/count);
          double level=Math.max(0.08,Math.min(1.0,peaks.get(idx)/26000.0));
          float x=(i+.5f)*w/count;
          float amplitude=(float)(level*h*.43);
          canvas.drawLine(x,h/2f-amplitude,x,h/2f+amplitude,paint);
        }
      }
    };
  }
  private void completeReview(JSONObject line,boolean accept){
    if(!recordingPending||recordingFile==null)return;
    File pending=recordingFile;String lid=recordingLine;
    if(accept){
      File dest=new File(store.takeDir(pid(),lid),pending.getName());
      try{
        java.nio.file.Files.move(pending.toPath(),dest.toPath());
        if(recordingProvisional)store.setProvisional(pid(),lid,dest.getName(),true);
        selectedTakeName=dest.getName();
        recordingPending=false;recordingFile=null;recordingLine="";
        if(reviewDialog!=null){reviewDialog.dismiss();reviewDialog=null;}
        if(line!=null)showTakeDialog(line);
        ui.postDelayed(this::autoSync,700);
      }catch(Exception ex){toast("Impossibile confermare la take: "+ex.getMessage());}
    }else{
      if(player!=null){try{player.stop();player.release();}catch(Exception ignored){}player=null;}
      if(!pending.delete()){toast("Impossibile scartare la take");return;}
      recordingPending=false;recordingFile=null;recordingLine="";
      if(reviewDialog!=null){reviewDialog.dismiss();reviewDialog=null;}
      if(line!=null)showTakeDialog(line);
    }
  }
  private void showRecordReview(JSONObject line){
    LinearLayout pane=column();pane.setPadding(dp(18),dp(20),dp(18),dp(22));
    pane.setBackgroundColor(Color.BLACK);
    TextView heading=label("ASCOLTA LA TUA TAKE",13,Color.rgb(194,174,224),true);
    heading.setGravity(Gravity.CENTER);pane.addView(heading);
    TextView content=label(line==null?"":MobileStore.str(line,"text"),18,TEXT,true);
    content.setGravity(Gravity.CENTER);pane.addView(content);
    View wave=waveformPreview();
    LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(-1,dp(120));wl.setMargins(0,dp(14),0,dp(12));
    pane.addView(wave,wl);
    Button listen=btn("▶  Riascolta",Color.rgb(40,38,51),()->{if(recordingFile!=null)play(recordingFile);});
    pane.addView(listen,new LinearLayout.LayoutParams(-1,dp(52)));
    LinearLayout choices=row();choices.setGravity(Gravity.CENTER);
    Button discard=btn("✕",Color.rgb(155,44,62),()->completeReview(line,false));
    Button accept=btn("✓",Color.rgb(32,126,87),()->completeReview(line,true));
    discard.setTextSize(30);accept.setTextSize(30);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(74),1);lp.setMargins(dp(7),dp(12),dp(7),0);
    choices.addView(discard,lp);LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,dp(74),1);
    p2.setMargins(dp(7),dp(12),dp(7),0);choices.addView(accept,p2);
    pane.addView(choices);
    TextView hints=label("SCARTA                                        CONFERMA",11,Color.LTGRAY,false);
    hints.setGravity(Gravity.CENTER);pane.addView(hints);
    reviewDialog=new AlertDialog.Builder(this).setView(pane).create();
    reviewDialog.setCancelable(false);reviewDialog.show();
    reviewDialog.getWindow().setBackgroundDrawableResource(android.R.color.black);
    reviewDialog.getWindow().setLayout(-1,-2);
  }
  private void play(File audio){if(player!=null){try{player.stop();player.release();}catch(Exception ignored){}player=null;}try{player=new MediaPlayer();player.setDataSource(audio.getAbsolutePath());player.prepare();player.start();player.setOnCompletionListener(p->{p.release();player=null;});}catch(Exception ex){toast("Impossibile riprodurre: "+ex.getMessage());}}
  private void renderSync(){section("Sincronizzazione con Windows",YELLOW);note("La Wi-Fi privata sincronizza copioni, cast e take tra telefono e PC. Le modifiche offline restano locali finché non viene stabilita la connessione.");
    addButton(body,"⚙ Collega PC / codice",PURPLE,this::pairDialog);
    addButton(body,"✓ Verifica collegamento reale al PC",GREEN,()->background(()->{
      try{
        JSONObject status=sync.state();runOnUiThread(()->{
          pcOnline=true;updateConnectionDot();
          lastSync="PC raggiungibile · "+status.optJSONArray("projects").length()+" episodi disponibili";
          toast(lastSync);if(page.equals("Sincronizza"))show();
        });
      }catch(Exception ex){error(new IOException("Connessione non riuscita: "+ex.getMessage()));}
    }));
    addButton(body,"⇅ Invia e ricevi modifiche",GREEN,this::syncAll);
    addButton(body,"↓ Importa un progetto già esistente sul PC",PANEL,this::chooseDesktopProject);
    note("Indirizzo: "+(sync.url().isEmpty()?"PC non configurato":sync.url()));note("Ultimo risultato: "+lastSync);
    note("Registri offline anche con PC spento. Quando riapri l’app sulla stessa Wi-Fi privata del PC, tenta automaticamente il trasferimento. Il pulsante manuale resta disponibile. Sul PC avvia AVVIA_PC_ANDROID.bat. Non usare reti pubbliche né aprire la porta verso Internet: HTTP LAN non è cifrato.");

  }
  private void pairDialog(){LinearLayout l=column();pad(l);EditText url=new EditText(this);url.setHint("http://192.168.1.2:18766");url.setText(sync.url());l.addView(url);EditText key=new EditText(this);key.setHint("Codice di abbinamento PC");key.setText(sync.token());l.addView(key);
    new AlertDialog.Builder(this).setTitle("Abbinamento PC").setView(l).setPositiveButton("Salva",(d,w)->{try{sync.setup(url.getText().toString().trim().replaceAll("/+$",""),key.getText().toString());toast("Dati PC salvati. Premi Verifica collegamento per controllare la rete.");show();}catch(Exception e){toast(e.getMessage());}}).setNegativeButton("Annulla",null).show();
  }
  private void background(Runnable run){new Thread(run,"maf-mobile-sync").start();}
  private void error(Exception ex){runOnUiThread(()->{pcOnline=false;updateConnectionDot();lastSync=ex.getMessage();toast("Operazione interrotta: "+ex.getMessage());if(page.equals("Sincronizza"))show();});}
  private void syncAll(){if(syncBusy){toast("Sincronizzazione già in corso");return;}syncBusy=true;lastSync="Invio in corso...";toast(lastSync);background(()->{try{MobileSync.Result result=sync.synchronizeAll();runOnUiThread(()->{syncBusy=false;pcOnline=true;updateConnectionDot();
      lastSync="Completata: "+result.message();show();toast(lastSync);});}catch(Exception ex){syncBusy=false;error(ex);}});}
  private void chooseDesktopProject(){background(()->{try{JSONObject state=sync.state();JSONArray projects=state.getJSONArray("projects");String[] choices=new String[projects.length()];String[] ids=new String[choices.length];for(int i=0;i<projects.length();i++){JSONObject p=projects.getJSONObject(i);ids[i]=p.getString("id");choices[i]=p.getString("title");}
    runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Importa episodio dal PC").setItems(choices,(d,which)->background(()->{try{JSONObject full=sync.download(ids[which]);runOnUiThread(()->{try{store.importFromPC(full);cast=store.cast();setProject(store.project(ids[which]));toast("Progetto importato sul telefono");}catch(Exception ex){toast(ex.getMessage());}});}catch(Exception ex){error(ex);}})).setNegativeButton("Annulla",null).show());
    }catch(Exception ex){error(ex);}});}
  @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(result!=RESULT_OK||intent==null)return;try{
    if(request==IMPORT_AVATAR&&!avatarTarget.isEmpty()){
      File dest=avatarFile(avatarTarget);dest.getParentFile().mkdirs();
      android.graphics.Bitmap bitmap=null;
      try(InputStream in=getContentResolver().openInputStream(intent.getData())){
        bitmap=android.graphics.BitmapFactory.decodeStream(in);
      }
      if(bitmap==null)throw new IOException("Immagine non valida");
      int w=bitmap.getWidth(),h=bitmap.getHeight();
      if(w>2048||h>2048){float scale=Math.min(2048f/w,2048f/h);
        bitmap=android.graphics.Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),true);
      }
      try(FileOutputStream out=new FileOutputStream(dest)){
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,95,out);
      }
      avatarTarget="";show();toast("Icona salvata sul telefono");
    }
    else if(request==IMPORT_WORD){if(editor==null)return;try(InputStream input=getContentResolver().openInputStream(intent.getData())){String imported=MobileStore.docxText(input);editor.getText().insert(editor.getSelectionStart(),imported);toast("Word importato. Controlla i tag e salva.");}}
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