package com.smschecker.app_java;

import android.content.pm.PackageManager;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;

public class PermissionUtils {
    public static JSONArray getGrantedPermissions(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();

            String[] requestedPermissions = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions;
            if (requestedPermissions == null) {
                requestedPermissions = new String[0];
            }

            JSONArray grantedPermissions = new JSONArray();

            for (String permission : requestedPermissions) {
                if (pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED) {
                    grantedPermissions.put(permission);
                }
            }

            return grantedPermissions;
        } catch (PackageManager.NameNotFoundException err) {
            err.printStackTrace();
            return null;
        }
    }
}
