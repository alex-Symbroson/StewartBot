package util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map.Entry;

import core.Bot;
import org.json.JSONObject;

public class FSManager {

	public static String readFile(String path) {
		try {
			return new String(Files.readAllBytes(getFile(path).toPath()));
		} catch (IOException e) {
	        Bot.Log(path + " could not be read");
			e.printStackTrace();
		}
		return null;
	}

	public static void writeFile(String path, String data) {
		try {
			FileOutputStream fos = new FileOutputStream(getFile(path));
			fos.write("sbfv2".getBytes());
			fos.write(data.getBytes());
			fos.close();
		} catch(Exception e) {
	        Bot.Log(path + " could not be saved");
	        e.printStackTrace();
		}
	}

	public static int host() {
	    try
	    {
	        return InetAddress.getLocalHost().getHostName().hashCode() ^ alt(System.getenv("USERNAME"), System.getenv("USER")).hashCode();
	    }
	    catch (UnknownHostException | NullPointerException e)
	    {
	        Bot.Log("Hostname can not be resolved");
	        e.printStackTrace();
	    }
	    return 0xF417;
	}

	private static String alt(String a, String b) {
		return a == null ? b : a;
	}

	public static JSONObject loadJSON(File f) {
	    try
	    {
	        return new JSONObject(new String(Files.readAllBytes(f.toPath())));
	    }
	    catch (Exception e)
	    {
	        Bot.Log(f.getPath() + " could not be loaded");
	        e.printStackTrace();
	    }
		return null;
	}

	public static JSONObject loadSBF(File f, int key) {
		JSONObject o = null;

		try
	    {
	    	byte[] arr = Files.readAllBytes(f.toPath());
	    	String sb = new String(Arrays.copyOfRange(arr, 0, 10));

			int version = 0;
			for(Entry<Integer, String> v : Bot.versions.entrySet()) 
				if(sb.startsWith(v.getValue())) {
					version = v.getKey();
					arr = Arrays.copyOfRange(arr, v.getValue().getBytes().length, arr.length);
					break;
				}

	    	if(version == 10) arr = Base64.getDecoder().decode(arr);
	    	else if(version == 0) throw new Exception("Invalid file version. Starts with " + sb.replaceAll("[^!-z]+", ""));

	    	o = new JSONObject(AES.decrypt(arr, Integer.toString(key)));
	    	o.put("version", version);
	    }
	    catch (Exception e)
	    {
	        Bot.Log(f.getPath() + " could not be loaded");
	        e.printStackTrace();
	    }

		return o;
	}

	public static void saveSBF(File f, JSONObject data, int key) {
	    try
	    {
	    	data.remove("version");
	        FileOutputStream fos = new FileOutputStream(f);
	        fos.write(Bot.versions.get(Bot.version).getBytes());
			fos.write(AES.encrypt(data.toString(), Integer.toString(key)));
	        fos.close();
	    }
	    catch (Exception e)
	    {
	        Bot.Log(f.getPath() + " could not be saved");
	        e.printStackTrace();
	    }
	}

	public static File getDataFile(String folder, String filePath) {
	    return getFile(String.format(Bot.dataDir, folder) + filePath);
	}

	// Create file and subdirs if not existent
	public static File getFile(String path) {
	    File f = new File(path);
	    if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
	    return f;
	}

}