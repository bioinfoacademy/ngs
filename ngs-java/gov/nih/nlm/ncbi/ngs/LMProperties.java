package gov.nih.nlm.ncbi.ngs;


import java.util.Date;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class LMProperties extends java.util.Properties {
    LMProperties(String bitsStr, Map<String, String> libraryVersions) {
        bits = bitsStr;
        for (Map.Entry<String, String> entry : libraryVersions.entrySet()) {
            Version v = new Version(entry.getValue());
            libsMajorVersions.put(entry.getKey(), v.getMajor());
        }

        path = LibPathIterator.ncbiHome();

        if (path != null) {
            path += LibPathIterator.fileSeparator() + "LibManager.properties";

            try {
                FileInputStream inStream = new FileInputStream(path);
                load(inStream);
                inStream.close();
            } catch (IOException e) {}
        }
    }

    public Object setProperty(String key, String value) {
        String saved = getProperty(key);
        if (saved != null && saved.equals(value)) {
            return saved;
        } else {
            dirty = true;
            return super.setProperty(key, value);
        }
    }

    String get(String libname, Version minimalVersion) {
        return get(libname, minimalVersion, Logger.Level.FINE);
    }

    String cfgFilePath() {
        return path;
    }

    void setLastSearch(String libname) {
        setProperty(getLibRoot(libname) + "last-search", Long.toString(new Date().getTime()));
    }

    Date getLastSeach(String libname) {
        String dateLong = getProperty(getLibRoot(libname) + "last-search");
        if (dateLong == null) {
            return null;
        }

        return new Date(Long.valueOf(dateLong));
    }

    void setLatestVersion(String libname, String version) {
        String node = getLibRoot(libname);
        setProperty(node + "latest-version/value", version);
        setProperty(node + "latest-version/updated", Long.toString(new Date().getTime()));
    }

    String getLatestVersion(String libname, long cacheTrustInterval) {
        String node = getLibRoot(libname);
        String version = getProperty(node + "latest-version/value");
        String dateLong = getProperty(node + "latest-version/updated");
        if (dateLong == null || version == null) {
            return null;
        }

        if (new Date().getTime() - Long.valueOf(dateLong) > cacheTrustInterval) {
            remove(node + "latest-version/value");
            remove(node + "latest-version/updated");
            return null;
        }

        return version;
    }

    void notLoaded(String libname) {
        String node = getLibRoot(libname);
        remove(node + "loaded/path");
        remove(node + "loaded/version");
        remove(node + "last-search");
        dirty = true;
    }

    void loaded(String libname, String version, String path)
    {   set(libname, "loaded", version, path); }

    void saved(String libname, String version, String path)
    {   set(libname, "saved", version, path); }

    void store() {
        try {
            if (!dirty) {
                return;
            }

            File file = new File(cfgFilePath());
            File parent = file.getParentFile();
            if (parent == null) {
                Logger.finest
                        ("Cannot find parent directory to store properties");
                return;
            } else if (!parent.exists()) {
                if (!parent.mkdir()) {
                    Logger.finest("Cannot create " + parent.getName());
                    return;
                }
                parent.setExecutable(false, false);
                parent.setReadable(false, false);
                parent.setWritable(false, false);
                parent.setExecutable(true, true);
                parent.setReadable(true, true);
                parent.setWritable(true, true);
            }
            FileOutputStream fileOut = new FileOutputStream(file);
            store(fileOut, null);
            fileOut.close();

            dirty = false;
        } catch (IOException e) {
            Logger.finest(e);
        }
    }

////////////////////////////////////////////////////////////////////////////////

    private void set(String libname, String name, String version,
                     String path)
    {
        String node = getLibRoot(libname) + name +"/";

        setProperty(node + "path"   , path);
        setProperty(node + "version", version);
    }

    private String get(String libname, Version minimalVersion, Logger.Level level) {
        String path = get(libname, "loaded", minimalVersion, level);
        if (path == null) {
            path = get(libname, "saved" , minimalVersion, level);
        }
        return path;
    }

    private String get
            (String libname, String name, Version minimalVersion, Logger.Level level)
    {
        String node = getLibRoot(libname) + name +"/";
        String version = getProperty(node + "version");
        if (version != null) {
            String path = getProperty(node + "path");
            if (path != null) {
                File f = new File(path);
                // TODO: check with Kurt whether we should remove old version from config file
                /*if (f.exists() &&
                        new Version(version).compareTo(new Version(minimalVersion)) < 0)
                {
                    remove(node + "path");
                    remove(node + "version");
                    dirty = true;
                } else {*///new Exception().printStackTrace();
                    Logger.log(level, "The version of the most recently"
                            + " loaded " + libname + " = " + version);
                    return path;
                /*}*/
            } else {
                remove(node + "version");
                dirty = true;
            }
        }
        return null;
    }

    private String getLibRoot(String libname) {
        if (!libsMajorVersions.containsKey(libname)) {
            throw new RuntimeException("Cannot get library " + libname + " major version");
        }
        return "/dll/" + libname + "/v" + libsMajorVersions.get(libname) + "/" + bits +"/";
    }

    private String path;
    private String bits;
    private Map<String, Integer> libsMajorVersions = new HashMap<String, Integer>();
    private boolean dirty;
}
