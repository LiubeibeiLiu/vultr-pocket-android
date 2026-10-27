package app.vultrpocket.mobile;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class PanelDialogs {
    interface Submit { void run(Dialog dialog,TextView error); }
    private static final int INK=Color.rgb(48,50,59),BLUE=Color.rgb(89,107,145),SOFT=Color.rgb(246,247,250);
    private static Typeface latin;
    private static Typeface latin(Activity a){if(latin==null)latin=Typeface.createFromAsset(a.getAssets(),"web/fonts/Tinos-Regular.ttf");return latin;}
    static int dp(Activity a,int n){return (int)(n*a.getResources().getDisplayMetrics().density+.5f);}
    private static GradientDrawable shape(Activity a,int color,int radius){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(a,radius));return b;}
    static EditText field(Activity a,String hint){EditText e=new EditText(a);e.setHint(hint);e.setSingleLine(true);e.setTextSize(16);e.setTextColor(INK);e.setHintTextColor(Color.rgb(104,116,135));e.setPadding(dp(a,15),0,dp(a,15),0);GradientDrawable background=shape(a,SOFT,15);background.setStroke(dp(a,1),Color.rgb(229,232,239));e.setBackground(background);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);e.setTypeface(latin(a));e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);return e;}
    static Dialog form(Activity a,String title,String message,EditText[] fields,String positive,Submit submit){
        return form(a,title,message,fields,positive,submit,null);
    }
    static Dialog reboot(Activity a,String state,String message,Submit submit){
        LinearLayout notice=new LinearLayout(a);notice.setOrientation(LinearLayout.VERTICAL);notice.setPadding(dp(a,16),dp(a,16),dp(a,16),dp(a,16));notice.setBackground(shape(a,Color.rgb(255,246,233),16));
        LinearLayout line=new LinearLayout(a);line.setGravity(Gravity.CENTER_VERTICAL);
        View warning=new View(a){final android.graphics.Paint paint=new android.graphics.Paint(3);@Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);paint.setColor(Color.rgb(171,119,57));paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);android.graphics.Path path=new android.graphics.Path();path.moveTo(12,3);path.lineTo(22,21);path.lineTo(2,21);path.close();canvas.drawPath(path,paint);canvas.drawLine(12,9,12,13,paint);canvas.drawPoint(12,17,paint);canvas.restore();}};
        warning.setContentDescription("提醒");line.addView(warning,new LinearLayout.LayoutParams(dp(a,21),dp(a,21)));
        TextView label=new TextView(a);label.setText(state);label.setTextSize(14);label.setTextColor(INK);label.setPadding(dp(a,9),0,0,0);line.addView(label);notice.addView(line);
        TextView detail=new TextView(a);detail.setText(message);detail.setTextSize(13);detail.setTextColor(Color.rgb(124,96,62));detail.setPadding(0,dp(a,10),0,0);notice.addView(detail);
        return form(a,"重启实例",null,new EditText[0],"确认",submit,notice);
    }
    private static Dialog form(Activity a,String title,String message,EditText[] fields,String positive,Submit submit,View notice){
        Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(a,24),dp(a,25),dp(a,24),dp(a,22));card.setBackground(shape(a,Color.WHITE,27));
        TextView heading=new TextView(a);heading.setText(title);heading.setTextSize(18);heading.setTextColor(INK);heading.setTypeface(latin(a),Typeface.BOLD);card.addView(heading);
        if(message!=null&&!message.isEmpty()){TextView detail=new TextView(a);detail.setText(message);detail.setTextColor(Color.rgb(98,107,123));detail.setTextSize(13);detail.setTypeface(latin(a));detail.setPadding(0,dp(a,14),0,dp(a,3));card.addView(detail);}
        if(notice!=null){LinearLayout.LayoutParams noticeParams=new LinearLayout.LayoutParams(-1,-2);noticeParams.topMargin=dp(a,18);card.addView(notice,noticeParams);}
        for(EditText input:fields){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,54));p.topMargin=dp(a,14);card.addView(input,p);}
        TextView error=new TextView(a);error.setTextSize(12);error.setTextColor(Color.rgb(160,76,64));error.setMinHeight(dp(a,30));error.setGravity(Gravity.CENTER_VERTICAL);card.addView(error,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(a);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel=button(a,"取消",false),save=button(a,positive,true);LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(a,54),1);left.rightMargin=dp(a,10);actions.addView(cancel,left);actions.addView(save,new LinearLayout.LayoutParams(0,dp(a,54),1));card.addView(actions);
        dialog.setContentView(card);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        cancel.setOnClickListener(v->dialog.dismiss());save.setOnClickListener(v->submit.run(dialog,error));dialog.setOnDismissListener(d->{for(EditText input:fields)input.setText("");});dialog.show();
        if(w!=null)w.setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels-dp(a,40),dp(a,380)),-2);
        return dialog;
    }
    private static Button button(Activity a,String title,boolean primary){Button b=new Button(a);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?Color.WHITE:BLUE);b.setStateListAnimator(null);b.setElevation(0);b.setTranslationZ(0);b.setOutlineProvider(null);b.setBackgroundTintList(null);b.setBackground(shape(a,primary?BLUE:SOFT,15));b.setMinimumWidth(0);b.setMinHeight(0);return b;}
}
