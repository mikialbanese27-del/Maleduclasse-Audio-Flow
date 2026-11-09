package it.maleduclasse.audioflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Consistent readable vector glyphs for the three bottom tabs. */
final class NavGlyphView extends View {
  private final int type;
  private final int ink;
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  NavGlyphView(Context context,int mode,int color){super(context);type=mode;ink=color;}
  @Override protected void onDraw(Canvas canvas){
    super.onDraw(canvas);
    float unit=Math.min(getWidth(),getHeight())/38f;
    canvas.save();canvas.translate((getWidth()-38*unit)/2f,(getHeight()-38*unit)/2f);
    canvas.scale(unit,unit);
    p.setColor(ink);p.setStrokeWidth(3.0f);p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    if(type==0){
      // Projects: four rounded grid squares, independent of device emoji fonts.
      p.setStyle(Paint.Style.STROKE);
      for(int y=0;y<2;y++)for(int x=0;x<2;x++){
        float l=6+x*15f,t=6+y*15f;
        canvas.drawRoundRect(l,t,l+11,t+11,2,2,p);
      }
    }else if(type==1){
      // Script: diagonal pen with a visible nib.
      p.setStyle(Paint.Style.STROKE);
      Path pen=new Path();pen.moveTo(9,27);pen.lineTo(25,11);
      pen.quadTo(27,9,29,11);pen.lineTo(32,14);pen.quadTo(33,16,31,18);
      pen.lineTo(15,34);pen.lineTo(7,35);pen.close();
      canvas.drawPath(pen,p);
      canvas.drawLine(21,15,28,22,p);
    }else{
      // Dubbing: studio microphone, not a dot.
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(3.2f);
      canvas.drawRoundRect(14,5,24,23,5,5,p);
      Path arms=new Path();
      arms.moveTo(9,18);arms.lineTo(9,21);arms.cubicTo(9,34,29,34,29,21);arms.lineTo(29,18);
      canvas.drawPath(arms,p);
      canvas.drawLine(19,30,19,35,p);
      canvas.drawLine(12,35,26,35,p);
    }
    canvas.restore();
  }
}
