package it.maleduclasse.audioflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Custom neon clock-like recording dial; decorative layer, not media storage. */
final class RecorderDialView extends View {
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  private boolean confirmed=false,recording=false,pending=false;
  RecorderDialView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
  void state(boolean ready,boolean live,boolean awaiting){
    confirmed=ready;recording=live;pending=awaiting;invalidate();
  }
  @Override protected void onDraw(Canvas canvas){
    super.onDraw(canvas);
    final float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f;
    float r=Math.min(w,h)*.462f;
    int accent=confirmed?0xff59d796:0xffec587c;
    if(recording)accent=0xffff687d;
    if(pending)accent=0xffb588ff;
    p.setStyle(Paint.Style.FILL);
    p.setShader(new RadialGradient(cx,cy,r,new int[]{0xff342044,0xff241a32,0xff160f22},null,Shader.TileMode.CLAMP));
    canvas.drawCircle(cx,cy,r,p);p.setShader(null);
    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(8);p.setColor(accent);
    p.setShadowLayer(13,0,0,accent);canvas.drawCircle(cx,cy,r-5,p);p.clearShadowLayer();
    p.setStrokeWidth(1.5f);p.setColor(0xff7c5a9b);
    canvas.drawCircle(cx,cy,r-23,p);
    for(int i=0;i<72;i++){
      float rad=(float)(Math.PI*2*i/72-Math.PI/2);
      float outside=r-12,inside=outside-(i%6==0?12:5);
      p.setColor(i%6==0?0xffc49de9:0xff644c80);
      p.setStrokeWidth(i%6==0?2.4f:1.2f);
      canvas.drawLine(cx+(float)Math.cos(rad)*inside,cy+(float)Math.sin(rad)*inside,
          cx+(float)Math.cos(rad)*outside,cy+(float)Math.sin(rad)*outside,p);
    }
    p.setStyle(Paint.Style.FILL);p.setColor(confirmed?0x142fe1a3:0x25f44273);
    canvas.drawCircle(cx,cy,r*.66f,p);
  }
}
