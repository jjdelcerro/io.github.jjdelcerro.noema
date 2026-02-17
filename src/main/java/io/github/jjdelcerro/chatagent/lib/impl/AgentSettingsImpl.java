package io.github.jjdelcerro.chatagent.lib.impl;

import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class AgentSettingsImpl implements AgentSettings {

    private final Map<String, String> values = new ConcurrentHashMap<>();
    private File file;

    @Override
    public String getProperty(String name) {
        return values.get(name);
    }

    @Override
    public long getPropertyAsLong(String name, long defaultValue) {
      try {
        String s = values.get(name);
        if( StringUtils.isBlank(s) ) {
          return defaultValue;
        }
        Long v = Long.getLong(s);
        return v;
      } catch(Exception ex) {
        return defaultValue; // TODO: enviar al log.
      }
    }
    
    @Override
    public String setProperty(String name, String value) {
        if (value == null) {
            return values.remove(name);
        }
        return values.put(name, value);
    }

    @Override
    public void load(File f) {
        this.file = f;
        if (!f.exists()) {
            return;
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(f)) {
            props.load(fis);
            for (String key : props.stringPropertyNames()) {
                values.put(key, props.getProperty(key));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error loading settings from " + f.getAbsolutePath(), e);
        }
    }

    @Override
    public void save() {
        if (file == null) {
            return;
        }
        Properties props = new Properties();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, "Agent Settings");
        } catch (Exception e) {
            throw new RuntimeException("Error saving settings to " + file.getAbsolutePath(), e);
        }
    }
}
