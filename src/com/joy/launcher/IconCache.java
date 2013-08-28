/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.joy.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class IconCache {
    private static final String TAG = "Launcher.IconCache";

    private static final int INITIAL_ICON_CACHE_CAPACITY = 50;

    private static class CacheEntry {
        public Bitmap icon;
        public String title;
    }

    private final Bitmap mDefaultIcon;
    private final LauncherApplication mContext;
    private final PackageManager mPackageManager;
    private final HashMap<ComponentName, CacheEntry> mCache =
            new HashMap<ComponentName, CacheEntry>(INITIAL_ICON_CACHE_CAPACITY);
    private int mIconDpi;
    //add by huangming for icon
    private ArrayList<String> mPackageFilter = new ArrayList<String>();
    private ArrayList<String> mActivityFilter = new ArrayList<String>();
    //end

    public IconCache(LauncherApplication context) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        int density = context.getResources().getDisplayMetrics().densityDpi;
        if (LauncherApplication.isScreenLarge()) {
            if (density == DisplayMetrics.DENSITY_LOW) {
                mIconDpi = DisplayMetrics.DENSITY_MEDIUM;
            } else if (density == DisplayMetrics.DENSITY_MEDIUM) {
                mIconDpi = DisplayMetrics.DENSITY_HIGH;
            } else if (density == DisplayMetrics.DENSITY_HIGH) {
                mIconDpi = DisplayMetrics.DENSITY_XHIGH;
            } else if (density == DisplayMetrics.DENSITY_XHIGH) {
                // We'll need to use a denser icon, or some sort of a mipmap
                mIconDpi = DisplayMetrics.DENSITY_XHIGH;
            } else {
                // Non-standard dpi, default to prevent crash
                mIconDpi = DisplayMetrics.DENSITY_HIGH;
            }
        } else {
            mIconDpi = context.getResources().getDisplayMetrics().densityDpi;
        }
        // need to set mIconDpi before getting default icon
        mDefaultIcon = makeDefaultIcon();
    }

    public Drawable getFullResDefaultActivityIcon() {
        return getFullResIcon(Resources.getSystem(),
                com.android.internal.R.mipmap.sym_def_app_icon);
    }

    public Drawable getFullResIcon(Resources resources, int iconId) {
        Drawable d;
        try {
            d = resources.getDrawableForDensity(iconId, mIconDpi);
        } catch (Resources.NotFoundException e) {
            d = null;
        }

        return (d != null) ? d : getFullResDefaultActivityIcon();
    }

    public Drawable getFullResIcon(String packageName, int iconId) {
        Resources resources;
        try {
            resources = mPackageManager.getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            if (iconId != 0) {
                return getFullResIcon(resources, iconId);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    public Drawable getFullResIcon(ResolveInfo info) {
        Resources resources;
        try {
            resources = mPackageManager.getResourcesForApplication(
                    info.activityInfo.applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            int iconId = info.activityInfo.getIconResource();
            if (iconId != 0) {
                return getFullResIcon(resources, iconId);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    private Bitmap makeDefaultIcon() {
        Drawable d = getFullResDefaultActivityIcon();
        Bitmap b = Bitmap.createBitmap(Math.max(d.getIntrinsicWidth(), 1),
                Math.max(d.getIntrinsicHeight(), 1),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, b.getWidth(), b.getHeight());
        d.draw(c);
        c.setBitmap(null);
        return b;
    }

    /**
     * Remove any records for the supplied ComponentName.
     */
    public void remove(ComponentName componentName) {
        synchronized (mCache) {
            mCache.remove(componentName);
        }
    }

    /**
     * Empty out the cache.
     */
    public void flush() {
        synchronized (mCache) {
            mCache.clear();
        }
    }

    /**
     * Fill in "application" with the icon and label for "info."
     */
    public void getTitleAndIcon(ApplicationInfo application, ResolveInfo info,
            HashMap<Object, CharSequence> labelCache) {
        synchronized (mCache) {
            CacheEntry entry = cacheLocked(application.componentName, info, labelCache);

            application.title = entry.title;
            application.iconBitmap = entry.icon;
        }
    }

    public Bitmap getIcon(Intent intent) {
        synchronized (mCache) {
            final ResolveInfo resolveInfo = mPackageManager.resolveActivity(intent, 0);
            ComponentName component = intent.getComponent();

            if (resolveInfo == null || component == null) {
                return mDefaultIcon;
            }

            CacheEntry entry = cacheLocked(component, resolveInfo, null);
            return entry.icon;
        }
    }

    public Bitmap getIcon(ComponentName component, ResolveInfo resolveInfo,
            HashMap<Object, CharSequence> labelCache) {
        synchronized (mCache) {
            if (resolveInfo == null || component == null) {
                return null;
            }

            CacheEntry entry = cacheLocked(component, resolveInfo, labelCache);
            return entry.icon;
        }
    }

    public boolean isDefaultIcon(Bitmap icon) {
        return mDefaultIcon == icon;
    }

    private CacheEntry cacheLocked(ComponentName componentName, ResolveInfo info,
            HashMap<Object, CharSequence> labelCache) {
        CacheEntry entry = mCache.get(componentName);
        //Modify by huangming for icon
        String packageName = componentName.getPackageName();
        String activityName = componentName.getClassName();
        if (entry == null) {
            entry = new CacheEntry();

            mCache.put(componentName, entry);

            ComponentName key = LauncherModel.getComponentNameFromResolveInfo(info);
            if (labelCache != null && labelCache.containsKey(key)) {
                entry.title = labelCache.get(key).toString();
            } else {
                entry.title = info.loadLabel(mPackageManager).toString();
                if (labelCache != null) {
                    labelCache.put(key, entry.title);
                }
            }
            if (entry.title == null) {
                entry.title = info.activityInfo.name;
            }

            Resources res = mContext.getResources();
            if(mPackageFilter.size() <= 0)
            {
            	mPackageFilter.clear();
            	mActivityFilter.clear();
            	String[] systemIcons = res.getStringArray(R.array.system_icons_filter);
            	if(systemIcons != null)
            	{
            		for(String systemIcon : systemIcons)
            		{
            			String[] paStr = systemIcon.split("_");
            			if(paStr.length == 2)
            			{
            				mPackageFilter.add(paStr[0]);
            				mActivityFilter.add(paStr[1]);
            			}
            		}
            		
            	}
            }
            boolean isSystem = false;
            if(packageName != null && activityName != null && checkIsSystemApp(mContext, packageName))
            {
            	for(int i = 0; i < mPackageFilter.size(); i++)
            	{
            		if(packageName.toLowerCase().contains(mPackageFilter.get(i)) 
            				&& activityName.toLowerCase().contains(mActivityFilter.get(i)))
            		{
            			String iconName = "joy_system_icon_" + mActivityFilter.get(i);
            			int iconId = res == null ?0 : res.getIdentifier(iconName, "drawable", mContext.getPackageName());
                    	if(iconId > 0)
                    	{
                    		Drawable d = getFullResIcon(res, iconId);
                    		if(d != null)
                    		{
                    			entry.icon = Utilities.createIconBitmap(
                                        d, 
                                        mContext);
                    			isSystem = true;
                    		}
                    		
                    	}
            			break;
            		}
            	}
            }
            
            if(!isSystem)
            {
            	entry.icon = Utilities.createIconBitmap(
                        getFullResIcon(info), mContext, packageName);
            }
            /*entry.icon = Utilities.createIconBitmap(
                    getFullResIcon(info), mContext, packageName);*/
        }
        //end
        return entry;
    }
    
    public static boolean checkIsSystemApp(Context context, String packageName)
    {
    	boolean isSystemApp = false;
    	try
    	{
    		PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, 0);
    		if((pi.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) !=0)
    		{
    			
    			isSystemApp = true;
    		}
    	}
    	catch(Exception e)
    	{
    		e.printStackTrace();
    	}
    	
    	return isSystemApp;
    }

    public HashMap<ComponentName,Bitmap> getAllIcons() {
        synchronized (mCache) {
            HashMap<ComponentName,Bitmap> set = new HashMap<ComponentName,Bitmap>();
            for (ComponentName cn : mCache.keySet()) {
                final CacheEntry e = mCache.get(cn);
                set.put(cn, e.icon);
            }
            return set;
        }
    }
}