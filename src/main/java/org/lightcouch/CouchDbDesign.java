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

import static org.lightcouch.CouchDbUtil.*;
import static org.lightcouch.URIBuilder.builder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Provides methods to create and save CouchDB design documents.
 * <h3>Usage Example:</h3>
 * <pre>
 * DesignDocument exampleDoc = dbClient.design().getFromDesk("example");
 * Response response = dbClient.design().synchronizeWithDb(exampleDoc);
 * DesignDocument documentFromDb = dbClient.design().getFromDb("_design/example");
 * </pre>
 * @see DesignDocument
 * @author Ahmed Yehia
 */
public class CouchDbDesign {

    private static final Log log = LogFactory.getLog(CouchDbDesign.class);

	private static final String DESIGN_DOCS_DIR = "design-docs";

    private Set<String> allDesignResources = new HashSet<String>();
    private Set<String> allDesignDocs = new HashSet<String>();
    private Map<String, List<String>> docLists = new HashMap<String, List<String>>();
    private Map<String, List<String>> docFilters = new HashMap<String, List<String>>();
    private Map<String, List<String>> docShows = new HashMap<String, List<String>>();
    private Map<String, List<String>> docValidators = new HashMap<String, List<String>>();
    private Map<String, Map<String, List<String>>> docViews = new HashMap<String, Map<String, List<String>>>();
    private Map<String, Map<String, List<String>>> docFulltext = new HashMap<String, Map<String, List<String>>>();

	private static final String JAVASCRIPT      = "javascript";
	private static final String DESIGN_PREFIX   = "_design/";
	private static final String VALIDATE_DOC    = "validate_doc_update";
	private static final String VIEWS           = "views";
	private static final String FILTERS         = "filters";
	private static final String SHOWS           = "shows";
	private static final String LISTS           = "lists";
	private static final String FULLTEXT        = "fulltext";

	private CouchDbClient dbc;

	CouchDbDesign(CouchDbClient dbc) {
		this.dbc = dbc;
	}

    private void enumerateDesignResources() {
        if (!allDesignDocs.isEmpty())
            return;
        Enumeration<URL> urls;
        try {
            urls = getURLs(DESIGN_DOCS_DIR);
            if (!urls.hasMoreElements() && log.isDebugEnabled())
                log.debug("No URLs returned by classloader for " + DESIGN_DOCS_DIR + " resource");
        } catch (IOException e) {
            log.warn("Cannot enumerate design doc resources in " + DESIGN_DOCS_DIR, e);
            return;
        }

        while (urls.hasMoreElements()) {
            URL url = null;
            try {
                url = urls.nextElement();
                if (log.isDebugEnabled())
                    log.debug("URL from classloader: " + url);

                String proto = url.getProtocol();
                String path = url.getPath();
                if (log.isDebugEnabled())
                    log.debug("proto = " + proto + "; path = " + path);
                if ("file".equals(proto)) {
                    enumerateFiles(new File(path));
                } else if ("jar".equals(proto)) {
                    enumerateJar(path);
                } else {
                    if (log.isDebugEnabled())
                        log.debug("Not enumerating design doc resources in " + url);
                    continue;
                }
            } catch (IOException e) {
                log.debug("Cannot read entries in URL: " + url, e);
            }
        }

        extractDesignResourceNames();
        allDesignResources.clear();
    }

    private void extractDesignResourceNames() {
        if (log.isDebugEnabled())
            log.debug("extracting design resource names");
        for (String n : allDesignResources) {
            if (log.isTraceEnabled())
                log.trace("matching " + n);
            if (n.matches("[^/\\\\]+/")) { // does not contain / (unix, mac) and \ (win) in path
                String doc = n.substring(0, n.length() - 1);
                if (log.isTraceEnabled())
                    log.trace("adding design resource " + doc);
                allDesignDocs.add(doc);
            }
        }
        for (String doc : allDesignDocs) {
            populateFunctionNames(doc, LISTS, allDesignResources, docLists);
            populateFunctionNames(doc, FILTERS, allDesignResources, docFilters);
            populateFunctionNames(doc, SHOWS, allDesignResources, docShows);
            populateFunctionNames(doc, VALIDATE_DOC, allDesignResources, docValidators);
            populateFunctionGroups(doc, VIEWS, allDesignResources, docViews, "map|reduce");
            populateFunctionGroups(doc, FULLTEXT, allDesignResources, docFulltext, "index|defaults|analyzer");
        }
    }

    private void enumerateFiles(File f) {
        String path = f.getAbsolutePath();
        if (log.isTraceEnabled())
            log.trace("enumerating " + path);
        path = path.substring(path.lastIndexOf(DESIGN_DOCS_DIR));
        if (!DESIGN_DOCS_DIR.equals(path)) {
            path = path.substring(DESIGN_DOCS_DIR.length()+1);
            if (f.isDirectory())
                path += "/";
            else if (log.isWarnEnabled() && allDesignResources.contains(path))
                log.warn("Design resource duplicate: " + f.getAbsolutePath());
            if (log.isTraceEnabled())
                log.trace("adding " + path);
            allDesignResources.add(path);
        }
        if (f.isDirectory()) {
            for (File s : f.listFiles())
                enumerateFiles(s);
        }
    }

    private void enumerateJar(String path) throws IOException {
        path = path.substring(5, path.indexOf("!"));
        JarFile j = new JarFile(path);
        Enumeration<JarEntry> e = j.entries();
        while (e.hasMoreElements()) {
            JarEntry f = e.nextElement();
            String name = f.getName();
            if (name.startsWith(DESIGN_DOCS_DIR + "/") && name.length() > DESIGN_DOCS_DIR.length()+1) {
                name = name.substring(DESIGN_DOCS_DIR.length()+1);
                if (log.isWarnEnabled() && !name.endsWith("/") && allDesignResources.contains(name))
                    log.warn("Design resource duplicate: " + name);
                allDesignResources.add(name);
            }
        }
    }

    private void populateFunctionNames(String doc, String what, Set<String> res, Map<String, List<String>> list) {
        List<String> files = new ArrayList<String>();
        final String SEP = "[/\\\\]+";
        for (String n : res) {
            if (n.matches("^" + doc + SEP + what + SEP + ".+\\.js$"))
                files.add(n);
        }
        list.put(doc, files);
    }

    private void populateFunctionGroups(String doc, String what, Set<String> res,
                                        Map<String, Map<String, List<String>>> list, String pattern) {
        Map<String, List<String>> dirs = new HashMap<String, List<String>>();
        final String SEP = "[/\\\\]+";
        Pattern p = Pattern.compile("^" + doc + SEP + what + SEP + "(.+)/$");
        for (String n : res) {
            Matcher m = p.matcher(n);
            if (m.matches())
                dirs.put(m.group(1), new ArrayList<String>());
        }
        for (String dir : dirs.keySet()) {
            List<String> files = dirs.get(dir);
            p = Pattern.compile("^" + doc + SEP + what + SEP + dir + SEP + "(" + pattern + ")\\.js$");
            for (String n : res) {
                Matcher m = p.matcher(n);
                if (m.matches())
                    files.add(n);
            }
        }
        list.put(doc, dirs);
    }

    /**
	 * Synchronizes a design document to the Database.
	 * <p>This method will first try to find a document in the database with the same id
	 * as the given document, if it is not found then the given document will be saved to the database.
	 * <p>If the document was found in the database, it will be compared with the given document using
	 *  {@code equals()}. If both documents are not equal, then the given document will be saved to the
	 *  database and updates the existing document.
	 * @param document The design document to synchronize
	 * @return {@link Response} as a result of a document save or update, or returns {@code null} if no
	 * action was taken and the document in the database is up-to-date with the given document.
	 */
	public Response synchronizeWithDb(DesignDocument document) {
		assertNotEmpty(document, "Document");
		DesignDocument documentFromDb = null;
		try {
			documentFromDb = getFromDb(document.getId());
		} catch (NoDocumentException e) {
			return dbc.save(document);
		}
		if(!document.equals(documentFromDb)) {
			document.setRevision(documentFromDb.getRevision());
			return dbc.update(document);
		}
		return null;
	}

	/**
	 * Synchronize all design documents from desk to the database.
	 * @see #synchronizeWithDb(DesignDocument)
	 */
	public void synchronizeAllWithDb() {
		List<DesignDocument> documents = getAllFromDesk();
		for (DesignDocument dd : documents) {
			synchronizeWithDb(dd);
		}
	}

	/**
	 * Gets a design document from the database.
	 * @param id The document id
	 * @return {@link DesignDocument}
	 */
	public DesignDocument getFromDb(String id) {
		assertNotEmpty(id, "id");
		URI uri = builder(dbc.getDBUri()).path(id).build();
		return dbc.get(uri, DesignDocument.class);
	}

	/**
	 * Gets a design document from the database.
	 * @param id The document id
	 * @param rev The document revision
	 * @return {@link DesignDocument}
	 */
	public DesignDocument getFromDb(String id, String rev) {
		assertNotEmpty(id, "id");
		assertNotEmpty(id, "rev");
		URI uri = builder(dbc.getDBUri()).path(id).query("rev", rev).build();
		return dbc.get(uri, DesignDocument.class);
	}

	/**
	 * Gets all design documents from desk.
	 * @see #getFromDesk(String)
	 */
	public List<DesignDocument> getAllFromDesk() {
        enumerateDesignResources();
        List<DesignDocument> designDocsList = new ArrayList<DesignDocument>(allDesignDocs.size());
        for (String doc : allDesignDocs)
            designDocsList.add(getFromDesk(doc));
		return designDocsList;
	}

	/**
	 * Gets a design document from desk.
	 * @param id The document id to get.
	 * @return {@link DesignDocument}
	 */
	public DesignDocument getFromDesk(String id) {
		assertNotEmpty(id, "id");
        enumerateDesignResources();
        if (!allDesignDocs.contains(id))
            throw new IllegalArgumentException("No design document found: " + id);

		DesignDocument dd = new DesignDocument();
        dd.setId(DESIGN_PREFIX + id);
        dd.setLanguage(JAVASCRIPT);
        dd.setLists(readFunctions(id, LISTS, docLists));
        dd.setFilters(readFunctions(id, FILTERS, docFilters));
        dd.setShows(readFunctions(id, SHOWS, docShows));
        List<String> validators = docValidators.get(id);
        if (validators != null && !validators.isEmpty()) {
            if (validators.size() > 1)
                throw new IllegalArgumentException("Expecting exactly one validate_doc_update function file: " + id);
            String name = validators.get(0);
            dd.setValidateDocUpdate(processCodeMacro(id, name, readTextResource(DESIGN_DOCS_DIR + "/" + name)));
        }
        dd.setViews(readFunctionGroups(id, VIEWS, docViews));
        dd.setFulltext(readFunctionGroups(id, FULLTEXT, docFulltext));
		return dd;
	}

    private String basename(String name) {
        return name.substring(Math.max(name.lastIndexOf("/"), name.lastIndexOf("\\")) + 1, name.lastIndexOf(".js"));
    }

    private static final Pattern codeMacroPattern = Pattern.compile("(//\\s*!code\\s+([\\w\\./-]+)\\s*)$", Pattern.MULTILINE);
    private String processCodeMacro(String id, String from, String body) {
        Matcher m = codeMacroPattern.matcher(body);
        if (!m.find())
            return body;
        m.reset();
        StringBuilder b = new StringBuilder(body.length()*2);
        int end = 0;
        while (m.find()) {
            if (log.isDebugEnabled())
                log.debug("replacing macro " + m.start() + ":" + m.end() + " " + m.group(2));
            b.append(body.substring(end, m.start()));
            String filePath = m.group(2);
            String macro = readTextResource(DESIGN_DOCS_DIR + "/" + id + "/" + filePath);
            if (macro == null)
                macro = readTextResource(DESIGN_DOCS_DIR + "/" + filePath); // global macro
            if (macro == null)
                throw new RuntimeException("Code file '" + filePath + "' not found on classpath; referenced by "
                        + from + " :: '" + m.group(1) + "'");
            b.append("// ==> " + filePath + "\n");
            b.append(macro);
            b.append("\n");
            b.append("// <== " + filePath + "\n");
            end = m.end();
        }
        b.append(body.substring(end));
        body = b.toString();
        if (log.isTraceEnabled())
            log.trace(from + " after // !code substitutions:\n" + body);
        return body;
    }

    private Map<String, String> readFunctions(String id, String what, Map<String, List<String>> all) {
        List<String> functions = all.get(id);
        if (functions == null || functions.isEmpty())
            return null;
        Map<String, String> functionsMap = new HashMap<String, String>();
        for (String name : functions)
            functionsMap.put(basename(name), processCodeMacro(id, name, readTextResource(DESIGN_DOCS_DIR + "/" + name)));
        return functionsMap;
    }

    private Map<String, Map<String, String>> readFunctionGroups(String id, String what, Map<String, Map<String, List<String>>> all) {
        Map<String, List<String>> groups = all.get(id);
        if (groups == null || groups.isEmpty())
            return null;
        Map<String, Map<String, String>> groupFunctionsMap = new HashMap<String, Map<String, String>>();
        for (String group : groups.keySet()) {
            List<String> groupFunctions = groups.get(group);
            Map<String, String> functionsMap = new HashMap<String, String>();
            for (String name : groupFunctions)
                functionsMap.put(basename(name), processCodeMacro(id, name, readTextResource(DESIGN_DOCS_DIR + "/" + name)));
            groupFunctionsMap.put(group, functionsMap);
        }
        return groupFunctionsMap;
    }
}
