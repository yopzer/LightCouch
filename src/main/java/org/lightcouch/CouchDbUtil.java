/*
 * Copyright (C) 2011 Ahmed Yehia (ahmed.yehia.m@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lightcouch;

import static java.lang.String.format;

import java.io.*;
import java.net.URL;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Provides various utility methods, for internal use.
 * @author Ahmed Yehia
 */
final class CouchDbUtil {
    private static final Log log = LogFactory.getLog(CouchDbUtil.class);

    private CouchDbUtil() {
		// Utility class
	}

	public static void assertNotEmpty(Object object, String prefix) throws IllegalArgumentException {
		if(object == null) {
			throw new IllegalArgumentException(format("%s may not be null.", prefix));
		} else if(object instanceof String && ((String)object).length() == 0) {
			throw new IllegalArgumentException(format("%s may not be empty.", prefix));
		}
	}

	public static void assertNull(Object object, String prefix) throws IllegalArgumentException {
		if(object != null) {
			throw new IllegalArgumentException(format("%s should be null.", prefix));
		}
	}

	public static String generateUUID() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	public static String removeExtension(String fileName) {
		return fileName.substring(0, fileName.lastIndexOf('.'));
	}

	// ------------------------------------------------------- JSON

	public static JsonObject objectToJson(Gson gson, Object object) {
		if(object instanceof JsonObject) {
			return (JsonObject) object;
		}
		return gson.toJsonTree(object).getAsJsonObject();
	}

	public static <T> T JsonToObject(Gson gson, JsonElement elem, String key, Class<T> classType) {
		return gson.fromJson(elem.getAsJsonObject().get(key), classType);
	}

	/**
	 * @return A JSON element as a String, or null if not found.
	 */
	public static String getElement(JsonObject j, String e) {
		return (j.get(e) == null) ? null : j.get(e).getAsString();
	}

	public static long getElementAsLong(JsonObject j, String e) {
		return (j.get(e) == null) ? 0L : j.get(e).getAsLong();
	}

	public static int getElementAsInt(JsonObject j, String e) {
		return (j.get(e) == null) ? 0 : j.get(e).getAsInt();
	}

	// ----------------------------------------------------- Streams

	private static final String LINE_SEP = System.getProperty("line.separator");

    public static String streamToString(InputStream stream, String charset) throws IOException {
        if (stream == null) return null;
        return streamToString(stream, new InputStreamReader(stream, charset));
    }

    public static String utf8StreamToString(InputStream stream) throws IOException {
        return streamToString(stream, "UTF-8");
    }

    public static String streamToString(InputStream stream, InputStreamReader reader) throws IOException {
        char[] chrArr = new char[stream.available()];
        int cnt = reader.read(chrArr);
        reader.close();
        stream.close();
        return new String(chrArr, 0, cnt);
    }

	public static String readTextResource(String resource) {
        try {
            return utf8StreamToString(getResource(resource));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

	public static String readFile(File file) {
		StringBuilder content = new StringBuilder((int)file.length());
		Scanner scanner = null;
		try {
			scanner = new Scanner(file);
			while(scanner.hasNextLine()) {
				content.append(scanner.nextLine() + LINE_SEP);
			}
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException(e);
		} finally {
			scanner.close();
		}
		return content.toString();
	}

	public static URL getURL(String resource) {
        URL u = Thread.currentThread().getContextClassLoader().getResource(resource);
        if (u == null)
            u = CouchDbUtil.class.getClassLoader().getResource(resource);
		return u;
	}

	public static Enumeration<URL> getURLs(String resource) throws IOException {
        Enumeration<URL> u = Thread.currentThread().getContextClassLoader().getResources(resource);
        if (u == null || !u.hasMoreElements())
            u = CouchDbUtil.class.getClassLoader().getResources(resource);
        return u;
	}

    public static InputStream getResource(String resource) {
        InputStream s = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (s == null)
            s = CouchDbUtil.class.getClassLoader().getResourceAsStream(resource);
        if (s == null && log.isDebugEnabled())
            log.debug("no resource found on classpath '" + resource + "'");
        return s;
    }

	  /**
	 * Closes the response input stream.
	 *
	 * @param response The {@link HttpResponse}
	 */
	public static void close(HttpResponse response) {
		try {
			response.getEntity().getContent().close();
		} catch (Exception e) {}
	}

	/**
	 * Closes a resource.
	 *
	 * @param c The {@link Closeable} resource.
	 */
	public static void close(Closeable c) {
		try {
			c.close();
		} catch (Exception e) {}
	}
}
