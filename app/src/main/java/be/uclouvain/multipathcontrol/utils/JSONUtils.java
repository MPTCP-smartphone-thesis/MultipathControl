package be.uclouvain.multipathcontrol.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by quentin on 8/11/16.
 */

public class JSONUtils {
    private static void insertValueInJson(JSONObject holder, Map.Entry pairs) throws JSONException {
        holder.put(pairs.getKey().toString(), pairs.getValue().toString());
    }

    private static void insertCollectionInJsonArray(JSONArray holder, Collection c) throws JSONException {
        // It's an array
        JSONArray ja = new JSONArray();

        // Get the values
        Iterator iter = c.iterator();
        while (iter.hasNext())
        {
            Object obj = iter.next();
            if (obj instanceof Map) {
                ja.put(getJsonObjectFromMap((Map) obj));
            } else if (obj instanceof Collection)
                insertCollectionInJsonArray(ja, (Collection) obj);
            else
                ja.put(obj.toString());
        }

        holder.put(ja);
    }

    private static void insertCollectionInJson(JSONObject holder, Map.Entry pairs) throws JSONException {
        // Create a key for Map
        String key = (String) pairs.getKey();

        // It's an array
        JSONArray ja = new JSONArray();

        // Get the values
        Iterator iter = ((Collection) pairs.getValue()).iterator();
        while (iter.hasNext())
        {
            Object obj = iter.next();
            if (obj instanceof Map)
                ja.put(getJsonObjectFromMap((Map) obj));
            else if (obj instanceof Collection)
                insertCollectionInJsonArray(ja, (Collection) obj);
            else
                ja.put(obj.toString());
        }

        holder.put(key, ja);
    }

    private static void insertMapInJson(JSONObject holder, Map.Entry pairs) throws JSONException {
        //creates a key for Map
        String key = (String)pairs.getKey();

        //Create a new map
        Map m = (Map)pairs.getValue();

        //object for storing Json
        JSONObject data = new JSONObject();

        //gets the value
        Iterator iter2 = m.entrySet().iterator();
        while (iter2.hasNext())
        {
            insertMapEntryInJson(data, (Map.Entry) iter2.next());
        }

        //puts email and 'foo@bar.com'  together in map
        holder.put(key, data);
    }

    private static void insertMapEntryInJson(JSONObject holder, Map.Entry pairs) throws JSONException {
        if (pairs.getValue() instanceof Map)
            insertMapInJson(holder, pairs);
        else if (pairs.getValue() instanceof Collection)
            insertCollectionInJson(holder, pairs);
        else
            insertValueInJson(holder, pairs);
    }

    public static JSONObject getJsonObjectFromMap(Map params) throws JSONException {

        //all the passed parameters from the post request
        //iterator used to loop through all the parameters
        //passed in the post request
        Iterator iter = params.entrySet().iterator();

        //Stores JSON
        JSONObject holder = new JSONObject();

        //using the earlier example your first entry would get email
        //and the inner while would get the value which would be 'foo@bar.com'
        //{ fan: { email : 'foo@bar.com' } }

        //While there is another entry
        while (iter.hasNext())
        {
            insertMapEntryInJson(holder, (Map.Entry) iter.next());
        }
        return holder;
    }
}
