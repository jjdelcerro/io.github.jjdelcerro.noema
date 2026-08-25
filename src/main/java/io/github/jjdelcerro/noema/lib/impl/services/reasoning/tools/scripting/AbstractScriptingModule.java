package io.github.jjdelcerro.noema.lib.impl.services.reasoning.tools.scripting;

import io.github.jjdelcerro.noema.lib.Agent;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractScriptingModule implements ScriptingModule {
  protected  static final Logger LOGGER = LoggerFactory.getLogger(AbstractScriptingModule.class);
  
  final String name;
  final String description;
  protected final Agent agent;
  protected final ScriptContext context;

  protected AbstractScriptingModule(ScriptContext context, Agent agent, String name, String description) {
    this.context = context;
    this.agent = Objects.requireNonNull(agent, "Agent cannot be null");
    this.name = name;
    this.description = description;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String help() {
    return "";
  }
  
  public static class AutoClosingLineIterator implements Iterator<String> {

    private final BufferedReader reader;
    private String nextLine = null;
    private boolean finished = false;

    public AutoClosingLineIterator(BufferedReader reader) {
      this.reader = Objects.requireNonNull(reader, "BufferedReader cannot be null");
      advance();
    }

    private void advance() {
      if (finished) {
        return;
      }
      try {
        nextLine = reader.readLine();
        if (nextLine == null) {
          close();
        }
      } catch (IOException e) {
        close();
        throw new RuntimeException("Error reading line: " + e.getMessage(), e);
      }
    }

    private void close() {
      finished = true;
      nextLine = null;
      IOUtils.closeQuietly(reader);
    }

    @Override
    public boolean hasNext() {
      return nextLine != null;
    }

    @Override
    public String next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      String current = nextLine;
      advance();
      return current;
    }
  }  
}
