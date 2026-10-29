package dev.zxcwsurx.zetacard;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

final class ArtStore {
    private ArtStore() { }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("art", 0);
    }

    static boolean valid(String id) {
        return id != null && id.matches("[0-9a-f]{64}");
    }

    static File file(Context context, String id) {
        return new File(new File(context.getFilesDir(), "art"), id + ".png");
    }

    static File stockFile(Context context, String id) {
        return new File(new File(context.getFilesDir(), "stock"), id + ".png");
    }

    static ArrayList<String> ids(Context context) {
        ArrayList<String> result = new ArrayList<>(prefs(context).getStringSet("ids", Collections.emptySet()));
        Collections.sort(result);
        return result;
    }
}
