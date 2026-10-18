package android.content;
public class SharedPreferences {
    public final java.util.HashMap<String, Integer> ints = new java.util.HashMap<String, Integer>();
    public int getInt(String k, int def) { Integer v = ints.get(k); return v == null ? def : v.intValue(); }
}
