package org.joget.marketplace;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.joget.apps.app.model.UiHtmlInjectorPluginAbstract;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.app.service.MarketplaceUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class MarketplaceUpdatesPlugin extends UiHtmlInjectorPluginAbstract implements PluginWebSupport  {
    
    @Override
    public String getName() {
        return "MarketplaceUpdatesPlugin";
    }

    @Override
    public String getVersion() {
        return "8.2.0";
    }

    @Override
    public String getDescription() {
        return "Webservice to determine if Joget has plugins with newer versions on marketplace.";
    }

    @Override
    public String[] getInjectUrlPatterns() {
        return new String[] {"/web/console/setting/plugin"};
    }
    
    @Override
    public String getHtml(HttpServletRequest request) {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        Map data = new HashMap();
        data.put("plugin", this);
        data.put("request", request);
        data.put("pluginsSize", getMarketplacePlugins(getLocalPlugins()).size());
        
        return pluginManager.getPluginFreeMarkerTemplate(data, getClassName(), "/templates/MarketplaceUpdatesUiHtmlInjector.ftl", null);
    }

    @Override
    public boolean isIncludeForAjaxThemePageSwitching() {
        return false;
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        List<Map<String, Object>> localPlugins = getLocalPlugins();

        // Set response type to JSON
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        List<Map<String, Object>> allPlugins = getMarketplacePlugins(localPlugins);

        Integer start = Integer.valueOf(request.getParameter("start"));
        Integer rows = Integer.valueOf(request.getParameter("rows"));
        // Pagination simulation
        List<Map<String, Object>> pagedPlugins = subList(allPlugins, start, rows);

        Map<String, Object> jsonOutput = new HashMap<>();
        jsonOutput.put("total", allPlugins.size());
        jsonOutput.put("page", 1);
        jsonOutput.put("data", pagedPlugins);

        PrintWriter out = response.getWriter();
        out.print(new ObjectMapper().writeValueAsString(jsonOutput)); // use Jackson for proper JSON
        out.flush();
    }

    public List<Map<String, Object>> getLocalPlugins() {
        List<Map<String, Object>> localPlugins = new ArrayList<>();

        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");

        File pluginDir = new File(pluginManager.getBaseDirectory());
        File[] jarFiles = pluginDir.listFiles(new FilenameFilter() { 
            @Override public boolean accept(File dir, String name) { 
                return name.toLowerCase().endsWith(".jar"); 
            } 
        });
        

        if (jarFiles == null || jarFiles.length == 0) {
            //LogUtil.info(getClassName(), "No JARs found in " + pluginDir.getAbsolutePath());
            return localPlugins;
        }

        //LogUtil.info(getClassName(), "===== Extracted Versions from JAR Files =====");

        for (File jar : jarFiles) {
            Map<String, Object> jarFileInfoMap = extractPluginInfoFromJar(jar);
            String version = jarFileInfoMap.get("version").toString();
            String id = jarFileInfoMap.get("id").toString(); 
            //LogUtil.info(getClassName(), "Plugin " + id + " is version " + version);
            localPlugins.add(jarFileInfoMap);
        }
        return localPlugins;
    }
    /**
     * Paging the data 
     * 
     * @param data
     * @param start
     * @param rows
     * @return 
     */
    protected List<Map<String, Object>> subList(List<Map<String, Object>> data, Integer start, Integer rows) {
        if (data == null) {
            return null;
        }
        int total = data.size();
        if (total > 0) {
            int begin = (start != null) ? start : 0;
            int end;
            if (begin < 0) {
                begin = 0;
            }
            if (rows == null || rows < 0) {
                end = total;
            } else {
                end = begin + rows;
            }
            if (end > total) {
                end = total;
            }
            List newList = data.subList(begin, end);
            return newList;
        } else {
            return data;
        }
    }

    public Map<String, Object> extractPluginInfoFromJar(File jarFile) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", "Unknown");
        info.put("version", "Unknown");

        // We'll store classes in a list
        List<String> classes = new ArrayList<>();

        try (JarFile jar = new JarFile(jarFile)) {

            // Try reading from MANIFEST.MF
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                Attributes attr = manifest.getMainAttributes();
                String version = attr.getValue("Implementation-Version");
                if (version == null) version = attr.getValue("Bundle-Version");
                if (version == null) version = attr.getValue("Specification-Version");
                if (version != null) info.put("version", version.trim());
            }

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                // Extract POM info
                if (entry.getName().startsWith("META-INF/maven/") && entry.getName().endsWith("pom.xml")) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        org.w3c.dom.Document doc = builder.parse(is);
                        doc.getDocumentElement().normalize();

                        String artifactId = getTagValue("artifactId", doc);
                        String version = getTagValue("version", doc);

                        if (artifactId != null) info.put("id", artifactId.trim());
                        if (version != null && !"Unknown".equals(info.get("version")))
                            info.put("version", version.trim());
                    }
                }

                // Collect .class entries
                else if (entry.getName().endsWith(".class")) {
                    // Convert path to canonical class name
                    String className = entry.getName()
                            .replace('/', '.')   // convert folder separators
                            .replace('\\', '.')
                            .replace(".class", "");
                    classes.add(className);
                }
            }

            // Store classes in info map
            info.put("classes", classes);

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error reading from " + jarFile.getName() + ": " + e.getMessage());
        }

        return info;
    }

    // Helper to extract a tag’s text value
    private static String getTagValue(String tag, org.w3c.dom.Document doc) {
        org.w3c.dom.NodeList nodes = doc.getElementsByTagName(tag);
        if (nodes != null && nodes.getLength() > 0) {
            org.w3c.dom.Node node = nodes.item(0);
            return node.getTextContent();
        }
        return null;
    }


    public List<Map<String, Object>> getMarketplacePlugins(List<Map<String, Object>> localPlugins) {
        List<Map<String, Object>> matchedList = new ArrayList<>();
        JSONArray marketplacePluginsArray = MarketplaceUtil.getList(null, null, null, null, null, null, null, null);

        // Build a lookup map for quick id→JSONObject access
        Map<String, JSONObject> marketplaceMap = new HashMap<>();
        for (int i = 0; i < marketplacePluginsArray.length(); i++) {
            JSONObject plugin = marketplacePluginsArray.getJSONObject(i);
            String filename = plugin.optString("fileName", "");

            // Extract base name from filename (remove version and .jar)
            String baseName = filename.replaceAll("\\.jar$", "");
            baseName = baseName.replaceAll("[-_v]?\\d+(\\.\\d+)*(-SNAPSHOT|-BETA|-PREVIEW)?$", "");

            marketplaceMap.put(baseName, plugin);
        }

        //LogUtil.info(getClassName(), "===== Compare Local to Marketplace Versions =====");

        // Compare versions
        for (Map<String, Object> local : localPlugins) {
            String id = (String) local.get("id");
            String localVersion = (String) local.get("version");
            JSONObject matchedPlugin = marketplaceMap.get(id);

            // Match by ID first
            if (matchedPlugin != null) {
                String remoteVersion = matchedPlugin.optString("version", "Unknown");
                int cmp = compareVersions(localVersion, remoteVersion);
                if (cmp < 0) {
                    //LogUtil.info(getClassName(), "Plugin " + id + " has newer version available: " + localVersion + " -> " + remoteVersion);
                    
                    Map<String, Object> matchedPluginsMap = new HashMap<>();
                    matchedPluginsMap.put("id", id);
                    matchedPluginsMap.put("pluginClass", "test");
                    matchedPluginsMap.put("label", "<a href='" + matchedPlugin.get("url") + "' target='_blank' onclick='event.stopPropagation();'>" + matchedPlugin.get("name") +"</a>"); // <--- use label
                    matchedPluginsMap.put("description", retrieveBrief(matchedPlugin.getString("brief")));
                    matchedPluginsMap.put("version", localVersion.replaceAll("[^0-9.]", "")); // installed version
                    matchedPluginsMap.put("latestVersion", remoteVersion.replaceAll("[^0-9.]", ""));
                    matchedList.add(matchedPluginsMap);
                } else {
                    //LogUtil.info(getClassName(), "Plugin " + id + " is up-to-date (" + localVersion + ")");
                }
            }
        }

        return matchedList;
    }

    private int compareVersions(String v1, String v2) {
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        String[] parts1 = v1.replaceAll("[^0-9.]", "").split("\\.");
        String[] parts2 = v2.replaceAll("[^0-9.]", "").split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (p1 < p2) return -1;
            if (p1 > p2) return 1;
        }
        return 0;
    }
    
    protected static String retrieveBrief(String text) {
        try {
            Document temp = Jsoup.parse(text);
            Element span = temp.select("span").first();
            if (span != null) {
                text = span.text();
            }
        } catch (Exception e) {
            LogUtil.debug(MarketplaceUtil.class.getName(), e.getMessage());
        }
        return text;
    }
}