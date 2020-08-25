package util;

import net.dv8tion.jda.api.EmbedBuilder;

import javax.annotation.Nullable;

public class Helper
{
    /** get item from array with range checks */
    @Nullable
    public static String get(String[] arr, int index)
    {
        if(arr.length <= index) return null;
        return arr[index];
    }

    /** get item from array with range checks */
    public static String get(String[] arr, int index, String dflt)
    {
        if(arr.length <= index) return dflt;
        return arr[index];
    }

    /** get item from array with range checks */
    public static Object get(Object[] arr, int index, Object dflt)
    {
        if(arr.length <= index) return dflt;
        return arr[index];
    }

    /**
     * Checks if the url is valid or returns null otherwise
     * @param url
     * @return url or null
     */
    @Nullable
    public static String getUrl(String url)
    {
        return url != null && EmbedBuilder.URL_PATTERN.matcher(url).matches() ? url : null;
    }
}
