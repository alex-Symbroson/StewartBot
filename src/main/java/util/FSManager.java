package util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map.Entry;

import core.Bot;
import org.json.JSONObject;

/** filesystem operations*/
public class FSManager
{
	/**
	 * Reads a file from the file system
	 * @param path
	 * @return file content
	 */
	public static String readFile(String path)
	{
		try
		{
			return new String(Files.readAllBytes(getFile(path).toPath()));
		}
		catch (IOException e) {
	        Bot.Log(path + " could not be read");
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Writes a file to the file system
	 * @param path
	 * @param data
	 */
	public static void writeFile(String path, String data)
	{
		try
		{
			FileOutputStream fos = new FileOutputStream(getFile(path));
			fos.write(data.getBytes());
			fos.close();
		}
		catch(Exception e) {
	        Bot.Log(path + " could not be saved");
	        e.printStackTrace();
		}
	}

	/**
	 * Read a json file from the file system
	 * @param jsonFile
	 * @return json
	 */
	public static JSONObject loadJSON(File jsonFile)
	{
	    try
		{
	        return new JSONObject(new String(Files.readAllBytes(jsonFile.toPath())));
	    }
	    catch (Exception e) {
	        Bot.Log(jsonFile.getPath() + " could not be loaded");
	        e.printStackTrace();
	    }
		return null;
	}

	/**
	 * reads and parses a sbf file from file system
	 * @param sbfFile
	 * @return json
	 */
	public static JSONObject loadSBF(File sbfFile)
	{
		JSONObject o = null;

		try
	    {
	    	byte[] arr = Files.readAllBytes(sbfFile.toPath());
	    	String sb = new String(Arrays.copyOfRange(arr, 0, 10));

			int version = 0;
			for(Entry<Integer, String> v : Bot.sbfVersions.entrySet())
				if(sb.startsWith(v.getValue()))
				{
					version = v.getKey();
					arr = Arrays.copyOfRange(arr, v.getValue().getBytes().length, arr.length);
					break;
				}

			if(version == 0) throw new Exception("Invalid file version. Starts with " + sb.replaceAll("[^!-z]+", ""));
			else arr = Bot.crypt.decrypt(arr);

	    	o = new JSONObject(new String(arr));
	    	o.put("version", version);
	    }
	    catch (Exception e) {
	        Bot.Log(sbfFile.getPath() + " could not be loaded");
	        e.printStackTrace();
	    }

		return o;
	}

	/**
	 * writes json to a sbf file
	 * @param sbfFile
	 * @param data json data
	 */
	public static void saveSBF(File sbfFile, JSONObject data)
	{
	    try
	    {
	    	data.remove("version");
	        FileOutputStream fos = new FileOutputStream(sbfFile);
	        fos.write(Bot.sbfVersions.get(Bot.sbfVersion).getBytes());
			fos.write(Bot.crypt.encrypt(data.toString()));
	        fos.close();
	    }
	    catch (Exception e) {
	        Bot.Log(sbfFile.getPath() + " could not be saved");
	        e.printStackTrace();
	    }
	}

	/**
	 * returns a file from the default {@link Bot#dataDir}
	 * @param folder
	 * @param filePath
	 * @return dataFile
	 */
	public static File getDataFile(String folder, String filePath)
	{
	    return getFile(String.format(Bot.dataDir, folder) + filePath);
	}

	/**
	 * Create file and subdirs if not existent
	 * @param path
	 * @return file
	 */
	public static File getFile(String path)
	{
	    File f = new File(path);
	    if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
	    return f;
	}

}