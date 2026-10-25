package io.github.jjdelcerro.noema.lib.impl.utils.search;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jjdelcerro
 */
public class SearchResult {

  public static class SearchResultEntry {
    private final String content;
    private final int score;
    private final Path file;
    private final int line;

    public SearchResultEntry(Path file, int line, String content, int score) {
      this.content = content;
      this.score = score;
      this.file = file;
      this.line = line;
    }

    public Path file() {
      return this.file;
    }

    public int line() {
      return this.line;
    }

    public String content() {
      return this.content;
    }

    public int score() {
      return this.score;
    }

    @Override
    public String toString() {
      return String.format("%s:%d:%d %s", file, line, score, content);
    }
  }  
  
  private int foundfiles;
  private int totalFiles;  
  private boolean limitReached;
  private List<SearchResultEntry> results;
  
  public SearchResult() {
    this.limitReached = false;
    this.totalFiles = 0;
    this.foundfiles = 0;
    this.results = new ArrayList<>();
  }
  
  public boolean isLimitReached() {
    return this.limitReached;
  }

  public void setLimitReached(boolean limitReached) {
    this.limitReached = limitReached;
  }
  
  public void add(Path file, int line, String content, int score) {
    this.results.add(new SearchResultEntry(file, line, content, score));
  }
  
  public int size() {
    return this.results.size();
  }

  public int getTotalfiles() {
    return totalFiles;
  }
  
  public int getFoundfiles() {
    return foundfiles;
  }

  public void incrementFoundFiles() {
    this.foundfiles++;
  }

  public void incrementTotalFiles() {
    this.totalFiles++;
  }

  public List<SearchResultEntry> results() {
    return this.results;
  }

}
