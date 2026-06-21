package com.gravecow.rg505mapper;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final String MODDIR = "/data/adb/modules/rg505_dpad_wasd";
    private static final String PREFS = "presets";
    private static final String PRESETS_JSON = "presets_json";
    private static final String APPLIED_NAME = "applied_name";
    private static final String APPLIED_VERSION = "applied_version";
    private static final String[] TARGETS = new String[]{
            "KEY_W","KEY_A","KEY_S","KEY_D","KEY_Q","KEY_E","KEY_R","KEY_F","KEY_Z","KEY_X","KEY_C","KEY_V","KEY_B",
            "KEY_SPACE","KEY_ENTER","KEY_ESC","KEY_TAB","KEY_LEFTCTRL","KEY_LEFTSHIFT","KEY_LEFTALT",
            "MOUSE_LEFT","MOUSE_RIGHT","MOUSE_MIDDLE"
    };

    private LinearLayout root;
    private ScrollView scroll;
    private TextView output;
    private SharedPreferences prefs;
    private ArrayList<Preset> presets = new ArrayList<>();
    private Preset editing;
    private EditText nameField, sourceField, mouseXField, mouseYField, centerXField, centerYField, deadzoneField, speedField, intervalField;
    private LinearLayout mappingsList;
    private ArrayList<MappingRow> mappingRows = new ArrayList<>();

    @Override public void onCreate(Bundle b) { super.onCreate(b); prefs=getSharedPreferences(PREFS, MODE_PRIVATE); loadPresets(); showGateThenMain(); }

    private void showGateThenMain(){ setBase(); output.setText("Checking backend..."); setContentView(scroll); new Thread(() -> { BackendInfo info = getBackendInfo(); runOnUiThread(() -> { if(!info.installed) showInstallOnly(info); else showMain(info); }); }).start(); }

    private void setBase(){ scroll = new ScrollView(this); root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24); scroll.addView(root); output = new TextView(this); output.setTextIsSelectable(true); }

    private void showInstallOnly(BackendInfo info){ setBase(); title("RG505 Input Mapper"); addButton(root,"Install", v -> installBackendThenMain()); root.addView(output); output.setText("Backend not installed."); setContentView(scroll); }

    private void showMain(BackendInfo info){ setBase(); title("RG505 Input Mapper"); if(info.outdated){ addButton(root,"Update", v -> installBackendThenMain()); TextView hint=small("Backend update available: installed " + info.installedCode + ", bundled " + info.assetCode); root.addView(hint); }
        String applied = prefs.getString(APPLIED_NAME, null); int appliedVersion = prefs.getInt(APPLIED_VERSION, 0); TextView appliedText = label("Applied: " + (applied==null ? "None" : applied + " v" + appliedVersion)); root.addView(appliedText);
        addButton(root,"New", v -> { Preset p = Preset.create("New Preset"); presets.add(p); savePresets(); showPreset(p); });
        if(presets.isEmpty()){ Preset p = Preset.create("Starbound"); presets.add(p); savePresets(); }
        for(Preset p: presets){ Button b=new Button(this); b.setText(p.name + " v" + p.version); b.setOnClickListener(v -> showPreset(p)); root.addView(b); }
        root.addView(space()); addButton(root,"Restart", v -> runAndShowAsync("sh " + MODDIR + "/mapctl restart")); addButton(root,"Stop", v -> runAndShowAsync("sh " + MODDIR + "/mapctl stop")); root.addView(space()); addButton(root,"Status", v -> runAndShowAsync("sh " + MODDIR + "/mapctl status")); addButton(root,"Logs", v -> runAndShowAsync("sh " + MODDIR + "/mapctl logs 160")); root.addView(output); output.setText("Ready"); setContentView(scroll); }

    private void showPreset(Preset p){ editing=p; setBase(); title("Preset"); LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); Button back=new Button(this); back.setText("Back"); back.setOnClickListener(v -> showGateThenMain()); Button apply=new Button(this); apply.setText("Apply"); apply.setOnClickListener(v -> applyPreset()); Button save=new Button(this); save.setText("Save"); save.setOnClickListener(v -> savePresetOnly()); top.addView(back,new LinearLayout.LayoutParams(0,-2,1)); top.addView(apply,new LinearLayout.LayoutParams(0,-2,1)); top.addView(save,new LinearLayout.LayoutParams(0,-2,1)); root.addView(top);
        root.addView(label("Name")); nameField=edit(p.name); root.addView(nameField);
        root.addView(section("Mouse")); addButton(root,"Learn Stick", v -> learnStick()); TextView mouseSummary=small("Current: " + p.mouseAxisX + " / " + p.mouseAxisY); root.addView(mouseSummary);
        LinearLayout adv = new LinearLayout(this); adv.setOrientation(LinearLayout.VERTICAL); adv.setVisibility(View.GONE); Button advBtn = new Button(this); advBtn.setText("Advanced ▸"); advBtn.setOnClickListener(v -> { boolean hidden=adv.getVisibility()!=View.VISIBLE; adv.setVisibility(hidden?View.VISIBLE:View.GONE); advBtn.setText(hidden?"Advanced ▾":"Advanced ▸"); }); root.addView(advBtn);
        sourceField=edit(p.sourceName); mouseXField=edit(p.mouseAxisX); mouseYField=edit(p.mouseAxisY); centerXField=edit(String.valueOf(p.centerX)); centerYField=edit(String.valueOf(p.centerY)); deadzoneField=edit(String.valueOf(p.deadzone)); speedField=edit(String.valueOf(p.speed)); intervalField=edit(String.valueOf(p.intervalMs));
        adv.addView(row("Source", sourceField)); adv.addView(row("X axis", mouseXField)); adv.addView(row("Y axis", mouseYField)); adv.addView(row("Center X", centerXField)); adv.addView(row("Center Y", centerYField)); adv.addView(row("Deadzone", deadzoneField)); adv.addView(row("Speed", speedField)); adv.addView(row("Interval", intervalField)); root.addView(adv);
        root.addView(section("Mappings")); mappingsList=new LinearLayout(this); mappingsList.setOrientation(LinearLayout.VERTICAL); mappingRows.clear(); root.addView(mappingsList); for(MapEntry m:p.maps) addMappingRow(m.input,m.target); addButton(root,"Add Map", v -> addMappingRow("Listen", "KEY_SPACE")); root.addView(output); output.setText("Ready"); setContentView(scroll); }

    private void savePresetOnly(){ if(editing==null)return; collectPreset(editing); savePresets(); output.setText("Saved"); }
    private void applyPreset(){ if(editing==null)return; collectPreset(editing); String h=editing.contentHash(); if(!h.equals(editing.lastAppliedHash)){ editing.version++; editing.lastAppliedHash=h; } savePresets(); String config=editing.toConfig(); String cmd="cat > " + MODDIR + "/config <<'CFG'\n" + config + "CFG\nsh " + MODDIR + "/mapctl restart"; runAndShowAsync(cmd, () -> { prefs.edit().putString(APPLIED_NAME, editing.name).putInt(APPLIED_VERSION, editing.version).apply(); }); }

    private void collectPreset(Preset p){ p.name=str(nameField, p.name); p.sourceName=str(sourceField,"Xbox Wireless Controller"); p.mouseAxisX=str(mouseXField,"ABS_Z"); p.mouseAxisY=str(mouseYField,"ABS_RZ"); p.centerX=num(centerXField,0); p.centerY=num(centerYField,0); p.deadzone=num(deadzoneField,200); p.speed=num(speedField,1); p.intervalMs=num(intervalField,8); p.maps.clear(); for(MappingRow r:mappingRows){ String in=r.inputButton.getText().toString().trim(); String target=r.target.getText().toString().trim(); if(in.length()>0 && !in.equals("Listen") && target.length()>0) p.maps.add(new MapEntry(in,target)); } }

    private void addMappingRow(String input, String target){ LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); Button listen=new Button(this); listen.setText(input); listen.setAllCaps(false); AutoCompleteTextView t=new AutoCompleteTextView(this); t.setSingleLine(true); t.setText(target); t.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, TARGETS)); Button remove=new Button(this); remove.setText("−"); MappingRow mr=new MappingRow(row,listen,t); mappingRows.add(mr); listen.setOnClickListener(v -> listenForInput(mr)); remove.setOnClickListener(v -> { mappingsList.removeView(row); mappingRows.remove(mr); }); row.addView(listen,new LinearLayout.LayoutParams(0,-2,2)); row.addView(t,new LinearLayout.LayoutParams(0,-2,2)); row.addView(remove,new LinearLayout.LayoutParams(90,-2)); mappingsList.addView(row); }

    private void listenForInput(MappingRow row){ output.setText("Press or move input..."); String source = sourceField!=null ? sourceField.getText().toString() : "Xbox Wireless Controller"; new Thread(() -> { String detected = detectInput(source,5); runOnUiThread(() -> { if(detected==null) output.setText("No input detected"); else { row.inputButton.setText(detected); output.setText("Detected " + detected); } }); }).start(); }

    private void learnStick(){ output.setText("Center stick..."); String source = sourceField!=null ? sourceField.getText().toString() : "Xbox Wireless Controller"; new Thread(() -> { try { sleepMs(1200); runOnUiThread(() -> output.setText("Move LEFT...")); AxisSample left=sampleAxis(source, "left"); runOnUiThread(() -> output.setText("Move RIGHT...")); AxisSample right=sampleAxis(source, "right"); runOnUiThread(() -> output.setText("Move UP...")); AxisSample up=sampleAxis(source, "up"); runOnUiThread(() -> output.setText("Move DOWN...")); AxisSample down=sampleAxis(source, "down"); runOnUiThread(() -> { if(left==null||right==null||up==null||down==null){ output.setText("Could not detect all directions. Try again."); return; } String xAxis = sameAxis(left,right) ? left.axis : right.axis; String yAxis = sameAxis(up,down) ? up.axis : down.axis; mouseXField.setText(xAxis); mouseYField.setText(yAxis); centerXField.setText(centerFor(left.value,right.value)); centerYField.setText(centerFor(up.value,down.value)); if(deadzoneField.getText().toString().trim().isEmpty()) deadzoneField.setText("200"); if(speedField.getText().toString().trim().isEmpty()) speedField.setText("1"); if(intervalField.getText().toString().trim().isEmpty()) intervalField.setText("8"); output.setText("Learned " + xAxis + " / " + yAxis); }); } catch(Exception e){ runOnUiThread(() -> output.setText("Learn failed: " + e)); } }).start(); }

    private AxisSample sampleAxis(String sourceName, String label){ String dev=findDevice(sourceName); if(dev==null)return null; String out=runRootCaptureSilent("timeout 3 getevent -l " + dev); return largestAbs(out); }
    private AxisSample largestAbs(String out){ AxisSample best=null; String[] lines=out.split("\\n"); for(String line:lines){ if(!line.contains("EV_ABS")) continue; String[] parts=line.trim().split("\\s+"); if(parts.length<4) continue; String axis=parts[2]; int v=parseEventValue(parts[3]); if(v==0) continue; if(best==null || Math.abs(v)>Math.abs(best.value)) best=new AxisSample(axis,v); } return best; }
    private String centerFor(int a,int b){ if((a<0&&b>0)||(a>0&&b<0)){ if(Math.abs(Math.abs(a)-Math.abs(b)) < Math.max(Math.abs(a),Math.abs(b))/3) return "0"; } return String.valueOf((a+b)/2); }
    private boolean sameAxis(AxisSample a, AxisSample b){ return a!=null && b!=null && a.axis.equals(b.axis); }

    private String detectInput(String sourceName, int seconds){ String dev=findDevice(sourceName); if(dev==null)return null; String out=runRootCaptureSilent("timeout " + seconds + " getevent -l " + dev); String[] lines=out.split("\\n"); for(String line:lines){ String[] parts=line.trim().split("\\s+"); if(parts.length<4) continue; if(line.contains("EV_KEY")){ int v=parseEventValue(parts[3]); if(v!=0) return "button:" + parts[2]; } if(line.contains("EV_ABS")){ int v=parseEventValue(parts[3]); if(v>0) return "axis:" + parts[2] + ":+"; if(v<0) return "axis:" + parts[2] + ":-"; } } return null; }
    private int parseEventValue(String s){ try { if(s==null)return 0; s=s.trim(); if(s.length()==8 && s.matches("[0-9a-fA-F]{8}")){ long l=Long.parseLong(s,16); if((l & 0x80000000L)!=0) l-=0x100000000L; return (int)l; } return Integer.parseInt(s); } catch(Exception e){ return 0; } }
    private String findDevice(String name){ String out=runRootCaptureSilent("getevent -lp"); String dev=null, nm=""; for(String line:out.split("\\n")){ if(line.startsWith("add device")){ if(dev!=null && nm.contains(name)) return dev; int idx=line.indexOf("/dev/input/event"); dev=idx>=0?line.substring(idx).trim().split("\\s+")[0]:null; nm=""; } else if(line.contains("name:")){ int q=line.indexOf('"'); int r=line.lastIndexOf('"'); if(q>=0&&r>q) nm=line.substring(q+1,r); } } if(dev!=null&&nm.contains(name)) return dev; return null; }

    private void installBackendThenMain(){ output.setText("Installing..."); new Thread(() -> { String result; try { copyAssetFolder("module", getCacheDir()); result=runRoot("mkdir -p " + MODDIR + " && cp -f " + getCacheDir().getAbsolutePath() + "/module/* " + MODDIR + "/ && chmod 755 " + MODDIR + " " + MODDIR + "/*"); } catch(Exception e){ result="Install failed: "+e; } String finalResult=result; runOnUiThread(() -> { output.setText(finalResult); showGateThenMain(); }); }).start(); }
    private void runAndShowAsync(String cmd){ runAndShowAsync(cmd,null); }
    private void runAndShowAsync(String cmd, Runnable after){ output.setText("Running..."); new Thread(() -> { String r=runRootCaptureSilent(cmd); runOnUiThread(() -> { output.setText(r); if(after!=null) after.run(); }); }).start(); }
    private String runRootCaptureSilent(String cmd){ try { return runRoot(cmd); } catch(Exception e){ return "Command failed: " + e; } }
    private String runRoot(String cmd) throws Exception { Process p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start(); ByteArrayOutputStream baos=new ByteArrayOutputStream(); InputStream is=p.getInputStream(); byte[] buf=new byte[4096]; int n; while((n=is.read(buf))!=-1) baos.write(buf,0,n); int rc=p.waitFor(); return "$ " + cmd + "\nexit=" + rc + "\n" + baos.toString(); }

    private BackendInfo getBackendInfo(){ BackendInfo bi=new BackendInfo(); String out=runRootCaptureSilent("test -f " + MODDIR + "/module.prop && cat " + MODDIR + "/module.prop || true"); bi.installed=out.contains("id=rg505_dpad_wasd"); bi.installedCode=parseVersionCode(out); bi.assetCode=getAssetVersionCode(); bi.outdated=bi.installed && bi.assetCode>bi.installedCode; return bi; }
    private int getAssetVersionCode(){ try { ByteArrayOutputStream b=new ByteArrayOutputStream(); InputStream in=getAssets().open("module/module.prop"); byte[] buf=new byte[1024]; int n; while((n=in.read(buf))!=-1)b.write(buf,0,n); return parseVersionCode(b.toString()); } catch(Exception e){ return 0; } }
    private int parseVersionCode(String s){ for(String line:s.split("\\n")){ line=line.trim(); if(line.startsWith("versionCode=")){ try{return Integer.parseInt(line.substring(12).trim());}catch(Exception ignored){} } } return 0; }

    private void loadPresets(){ presets.clear(); String raw=prefs.getString(PRESETS_JSON,null); if(raw!=null){ try{ JSONArray arr=new JSONArray(raw); for(int i=0;i<arr.length();i++) presets.add(Preset.fromJson(arr.getJSONObject(i))); }catch(Exception ignored){} } if(presets.isEmpty()) presets.add(Preset.create("Starbound")); }
    private void savePresets(){ try { JSONArray arr=new JSONArray(); for(Preset p:presets) arr.put(p.toJson()); prefs.edit().putString(PRESETS_JSON, arr.toString()).apply(); } catch(Exception ignored){} }

    private TextView title(String s){ TextView v=new TextView(this); v.setText(s); v.setTextSize(22); root.addView(v); return v; }
    private TextView section(String s){ TextView v=label(s); v.setTextSize(18); root.addView(space()); return v; }
    private TextView label(String s){ TextView v=new TextView(this); v.setText(s); v.setTextSize(16); return v; }
    private TextView small(String s){ TextView v=new TextView(this); v.setText(s); v.setTextSize(13); return v; }
    private View space(){ Space sp=new Space(this); sp.setMinimumHeight(18); return sp; }
    private EditText edit(String s){ EditText e=new EditText(this); e.setSingleLine(true); e.setText(s); e.setInputType(InputType.TYPE_CLASS_TEXT); return e; }
    private View row(String label, EditText e){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); TextView t=new TextView(this); t.setText(label+": "); t.setWidth(220); l.addView(t); l.addView(e, new LinearLayout.LayoutParams(-1,-2)); return l; }
    private void addButton(LinearLayout root, String text, View.OnClickListener l){ Button b=new Button(this); b.setText(text); b.setOnClickListener(l); root.addView(b); }
    private String str(EditText e,String d){ String s=e.getText().toString().trim(); return s.isEmpty()?d:s; }
    private int num(EditText e,int d){ try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception ex){return d;} }
    private void sleepMs(long ms){ try{Thread.sleep(ms);}catch(Exception ignored){} }
    private void copyAssetFolder(String assetPath, File outBase) throws IOException { String[] list=getAssets().list(assetPath); File out=new File(outBase, assetPath); out.mkdirs(); if(list==null) return; for(String name:list){ String child=assetPath+"/"+name; String[] sub=getAssets().list(child); if(sub!=null && sub.length>0) copyAssetFolder(child,outBase); else copyAsset(child,new File(out,name)); } }
    private void copyAsset(String asset, File out) throws IOException { out.getParentFile().mkdirs(); try(InputStream in=getAssets().open(asset); OutputStream os=new FileOutputStream(out)){ byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) os.write(b,0,n); } out.setReadable(true,false); out.setExecutable(true,false); }

    private static class BackendInfo { boolean installed, outdated; int installedCode, assetCode; }
    private static class MappingRow { LinearLayout row; Button inputButton; AutoCompleteTextView target; MappingRow(LinearLayout r, Button b, AutoCompleteTextView t){row=r;inputButton=b;target=t;} }
    private static class AxisSample { String axis; int value; AxisSample(String a,int v){axis=a;value=v;} }
    private static class MapEntry { String input,target; MapEntry(String i,String t){input=i;target=t;} JSONObject toJson() throws JSONException { JSONObject o=new JSONObject(); o.put("input",input); o.put("target",target); return o; } static MapEntry fromJson(JSONObject o){ return new MapEntry(o.optString("input"), o.optString("target")); } }
    private static class Preset { String name="Preset", sourceName="Xbox Wireless Controller", mouseAxisX="ABS_Z", mouseAxisY="ABS_RZ", lastAppliedHash=""; int version=0, centerX=0, centerY=0, deadzone=200, speed=1, intervalMs=8; ArrayList<MapEntry> maps=new ArrayList<>();
        static Preset create(String name){ Preset p=new Preset(); p.name=name; p.maps.add(new MapEntry("axis:ABS_HAT0X:-","KEY_A")); p.maps.add(new MapEntry("axis:ABS_HAT0X:+","KEY_D")); p.maps.add(new MapEntry("axis:ABS_HAT0Y:-","KEY_W")); p.maps.add(new MapEntry("axis:ABS_HAT0Y:+","KEY_S")); return p; }
        String toConfig(){ StringBuilder sb=new StringBuilder(); sb.append("EVENT_NAME=\"").append(sourceName).append("\"\nOUTPUT_NAME=\"RG505 D-pad WASD\"\nOUTPUT_MOUSE_NAME=\"RG505 Mapper Mouse\"\nDEBUG=0\n"); sb.append("MOUSE_AXIS_X=\"").append(mouseAxisX).append("\"\nMOUSE_AXIS_Y=\"").append(mouseAxisY).append("\"\nMOUSE_CENTER_X=").append(centerX).append("\nMOUSE_CENTER_Y=").append(centerY).append("\nMOUSE_DEADZONE=").append(deadzone).append("\nMOUSE_SPEED=").append(speed).append("\nMOUSE_INTERVAL_MS=").append(intervalMs).append("\n"); for(int i=0;i<maps.size();i++) sb.append("MAP_").append(i+1).append("=\"").append(maps.get(i).input).append(":").append(maps.get(i).target).append("\"\n"); return sb.toString(); }
        String contentHash(){ try{ MessageDigest md=MessageDigest.getInstance("SHA-256"); byte[] h=md.digest(toConfig().getBytes("UTF-8")); StringBuilder sb=new StringBuilder(); for(byte b:h) sb.append(String.format(Locale.US,"%02x",b)); return sb.toString(); }catch(Exception e){ return String.valueOf(toConfig().hashCode()); } }
        JSONObject toJson() throws JSONException { JSONObject o=new JSONObject(); o.put("name",name); o.put("version",version); o.put("sourceName",sourceName); o.put("mouseAxisX",mouseAxisX); o.put("mouseAxisY",mouseAxisY); o.put("centerX",centerX); o.put("centerY",centerY); o.put("deadzone",deadzone); o.put("speed",speed); o.put("intervalMs",intervalMs); o.put("lastAppliedHash",lastAppliedHash); JSONArray a=new JSONArray(); for(MapEntry m:maps)a.put(m.toJson()); o.put("maps",a); return o; }
        static Preset fromJson(JSONObject o){ Preset p=new Preset(); p.name=o.optString("name","Preset"); p.version=o.optInt("version",0); p.sourceName=o.optString("sourceName","Xbox Wireless Controller"); p.mouseAxisX=o.optString("mouseAxisX","ABS_Z"); p.mouseAxisY=o.optString("mouseAxisY","ABS_RZ"); p.centerX=o.optInt("centerX",0); p.centerY=o.optInt("centerY",0); p.deadzone=o.optInt("deadzone",200); p.speed=o.optInt("speed",1); p.intervalMs=o.optInt("intervalMs",8); p.lastAppliedHash=o.optString("lastAppliedHash",""); JSONArray a=o.optJSONArray("maps"); if(a!=null) for(int i=0;i<a.length();i++) p.maps.add(MapEntry.fromJson(a.optJSONObject(i))); return p; }
    }
}
