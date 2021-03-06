/**
 * This file is part of UnToasted.
 *
 * Copyright 2014 Eric Gingell (c)
 *
 *     UnToasted is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     UnToasted is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with UnToasted.  If not, see <http://www.gnu.org/licenses/>.
 *     
 *     Xposed log: tail -f -n 100 /data/data/de.robv.android.xposed.installer/log/error.log  >/sdcard/UnToaster.log
 *     Logcat: logcat | grep "UnToaster"
 *     Logcat: logcat | grep "AndroidRuntime"
 */

package com.egingell.untoaster.xposed;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

import com.egingell.untoaster.common.MySettings;
import com.egingell.untoaster.common.Util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
//import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage, IXposedHookZygoteInit {

	//private static String PATH = null;
	private HashMap<String,MySettings> prefs = new HashMap<String,MySettings>();
	public Hook() {}

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
		prefs.put(loadPackageParam.packageName, new MySettings(loadPackageParam.packageName));
		prefs.get(loadPackageParam.packageName).reload();
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
    	try {
	    	XC_MethodHook hook = new XC_MethodHook() {
				final private Pattern splitPattern = Pattern.compile("[\r\n]+");
			    private final HashMap<String,Pattern> patternCache = new HashMap<String,Pattern>();
			    private boolean filter(String needle, String haystack) {
				    if (! patternCache.containsKey(needle)) {
				 	   patternCache.put(needle, Pattern.compile(needle));
				    }
				    boolean b = patternCache.get(needle).matcher(haystack).find();
				    //Util.log("UnToaster#filter: checking\n    pattern " + needle + "\n    in " + haystack + "\n    result " + (b ? "true" : "false"));
				    return b;
			    }
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					try {
						View layout = (View) ((Toast) param.thisObject).getView();
						Context context = layout.getContext();
						TextView view = (TextView) layout.findViewById(android.R.id.message);
				    	boolean show = true;
						try {
							final PackageManager pm = context.getPackageManager();
							String packageName = context.getPackageName();
							String appName = pm.getApplicationLabel(context.getApplicationInfo()).toString();
							ArrayList<String> list = new ArrayList<String>();
							try {
								list.add(splitPattern.matcher(view.getText().toString()).replaceAll(" ").trim());
							} catch (Throwable e) {}
							int i = 0;
							try {
								do {
									try {
										list.add(splitPattern.matcher(((TextView) ((ViewGroup) layout).getChildAt(i++)).getText().toString()).replaceAll(" ").trim());
									} catch (ClassCastException e) {}
								} while (((ViewGroup) layout).getChildCount() > i);
							} catch (Throwable e) {
								//Util.log("Unable to find a layout for " + packageName + " (" + appName + ")");
							}
							
							MySettings settings = prefs.get(packageName), pAll = new MySettings("all"), appSettings = new MySettings("com.egingell.untoaster");
							
				    		appSettings.reload();
				    		settings.reload();
				    		pAll.reload();
				    		
							String blocked = "allowed";
							
							String all = pAll.get("content", "NA").trim();
							String fPackage = settings.get("content", "NA").trim();
					 		String patterns = "";
					 		
					 		if (! fPackage.equals("NA")) {
					 			if (fPackage.equals("")) {
					 				fPackage = ".*";
					 			}
						 		if (! all.equals("NA")) {
						 			patterns += all + "\n" + fPackage;
						 		} else {
						 			patterns += fPackage;
						 		}
					 		} else if (! all.equals("NA")) {
					 			patterns += all;
					 		}
					 		for (String content : list) {
					 			if (! patterns.trim().equals("NA") && ! patterns.trim().equals("")) {
						 			for (String s : splitPattern.split(patterns)) {
										if (filter(s, content)) {
											blocked = "blocked";
											show = false;
										}
						 			}
						 		}
								Intent intent = new Intent();
								intent.setAction("com.egingell.untoaster.LOG_RECEIVED");
								intent.putExtra("logdata", "package: " + packageName + "\n    app: " + appName + "\n    haystack: " + content + "\n    needles (all): " + all + "\n    needles (" + packageName + "): " + fPackage + "\n    toast: " + blocked + "\n ");
								context.sendBroadcast(intent);
						 		//Log.i("UnToaster", packageName + " (" + appName + ")\n    haystack: " + content + "\n    needles (all): " + all + "\n    needles (" + packageName + "): " + fPackage + "\n    toast: " + blocked);
					 		}
						} catch (Throwable e) {
							Util.log(e);
						}
				        if (!show) {
				        	param.setResult(null);
				        }
					} catch (Throwable e) {
						Util.log(e);
					}
				}
			};
			Method method = XposedHelpers.findMethodExact(Toast.class, "show");
			method.setAccessible(true);
			XposedBridge.hookMethod(method, hook);
	        //PATH = startupParam.modulePath;
	    } catch (Throwable e) {
	    	Util.log(e);
	    }
    } // void initZygote(StartupParam startupParam)
} // class Hook implements IXposedHookLoadPackage, IXposedHookZygoteInit