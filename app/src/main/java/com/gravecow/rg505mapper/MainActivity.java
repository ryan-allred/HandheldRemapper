package com.gravecow.rg505mapper;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    private TextView output;
    private EditText sourceName, mouseX, mouseY, centerX, centerY, deadzone, speed, interval, inputName, targetName;
    private static final String MODDIR = "/data/adb/modules/rg505_dpad_wasd";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24); sv.addView(root);
        TextView title = new TextView(this); title.setText("RG505 Input Mapper"); title.setTextSize(22); root.addView(title);
        sourceName = edit("Xbox Wireless Controller"); root.addView(label("Source device name")); root.addView(sourceName);
        mouseX = edit("ABS_Z"); mouseY = edit("ABS_RZ"); centerX = edit("0"); centerY = edit("0"); deadzone = edit("200"); speed = edit("2"); interval = edit("8");
        root.addView(label("Mouse axes/config")); root.addView(row("X axis", mouseX)); root.addView(row("Y axis", mouseY)); root.addView(row("Center X", centerX)); root.addView(row("Center Y", centerY)); root.addView(row("Deadzone", deadzone)); root.addView(row("Speed", speed)); root.addView(row("Interval ms", interval));
        inputName = edit("button:KEY_X"); targetName = edit("MOUSE_LEFT"); root.addView(label("Add/replace mapping")); root.addView(row("Input", inputName)); root.addView(row("Target", targetName));
        addButton(root,"Install / Update Backend", v -> installBackend());
        addButton(root,"Apply Mouse Config + Restart", v -> runAndShow("sh " + MODDIR + "/mapctl set-mouse-axes " + mouseX.getText() + " " + mouseY.getText() + " && sh " + MODDIR + "/mapctl mouse-config " + centerX.getText() + " " + centerY.getText() + " " + deadzone.getText() + " " + speed.getText() + " " + interval.getText() + " && sh " + MODDIR + "/mapctl restart"));
        addButton(root,"Add Mapping + Restart", v -> runAndShow("sh " + MODDIR + "/mapctl set-input " + inputName.getText() + " " + targetName.getText() + " && sh " + MODDIR + "/mapctl restart"));
        addButton(root,"Starbound Preset + Restart", v -> runAndShow("sh " + MODDIR + "/mapctl preset starbound && sh " + MODDIR + "/mapctl restart"));
        addButton(root,"Restart Mapper", v -> runAndShow("sh " + MODDIR + "/mapctl restart"));
        addButton(root,"Stop Mapper", v -> runAndShow("sh " + MODDIR + "/mapctl stop"));
        addButton(root,"Status", v -> runAndShow("sh " + MODDIR + "/mapctl status"));
        addButton(root,"Logs", v -> runAndShow("sh " + MODDIR + "/mapctl logs 120"));
        output = new TextView(this); output.setTextIsSelectable(true); output.setText("Ready"); root.addView(output);
        setContentView(sv);
    }
    private TextView label(String s){ TextView v=new TextView(this); v.setText(s); v.setTextSize(16); return v; }
    private EditText edit(String s){ EditText e=new EditText(this); e.setSingleLine(true); e.setText(s); e.setInputType(InputType.TYPE_CLASS_TEXT); return e; }
    private View row(String label, EditText e){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); TextView t=new TextView(this); t.setText(label+": "); t.setWidth(220); l.addView(t); l.addView(e, new LinearLayout.LayoutParams(-1,-2)); return l; }
    private void addButton(LinearLayout root, String text, View.OnClickListener l){ Button b=new Button(this); b.setText(text); b.setOnClickListener(l); root.addView(b); }
    private void installBackend(){ try { copyAssetFolder("module", getCacheDir()); runRoot("mkdir -p " + MODDIR + " && cp -f " + getCacheDir().getAbsolutePath() + "/module/* " + MODDIR + "/ && chmod 755 " + MODDIR + " " + MODDIR + "/* && sh " + MODDIR + "/mapctl restart"); output.setText("Installed/updated backend.\n" + runRootCapture("sh " + MODDIR + "/mapctl status")); } catch(Exception e){ output.setText("Install failed: " + e); } }
    private void runAndShow(String cmd){ output.setText(runRootCapture(cmd)); }
    private String runRootCapture(String cmd){ try { return runRoot(cmd); } catch(Exception e){ return "Command failed: " + e; } }
    private String runRoot(String cmd) throws Exception { Process p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start(); ByteArrayOutputStream baos=new ByteArrayOutputStream(); InputStream is=p.getInputStream(); byte[] buf=new byte[4096]; int n; while((n=is.read(buf))!=-1) baos.write(buf,0,n); int rc=p.waitFor(); return "$ " + cmd + "\nexit=" + rc + "\n" + baos.toString(); }
    private void copyAssetFolder(String assetPath, File outBase) throws IOException { String[] list=getAssets().list(assetPath); File out=new File(outBase, assetPath); out.mkdirs(); if(list==null) return; for(String name:list){ String child=assetPath+"/"+name; String[] sub=getAssets().list(child); if(sub!=null && sub.length>0) copyAssetFolder(child,outBase); else copyAsset(child,new File(out,name)); } }
    private void copyAsset(String asset, File out) throws IOException { out.getParentFile().mkdirs(); try(InputStream in=getAssets().open(asset); OutputStream os=new FileOutputStream(out)){ byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) os.write(b,0,n); } out.setReadable(true,false); out.setExecutable(true,false); }
}
