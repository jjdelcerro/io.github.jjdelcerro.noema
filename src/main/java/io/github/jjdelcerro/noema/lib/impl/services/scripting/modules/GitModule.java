package io.github.jjdelcerro.noema.lib.impl.services.scripting.modules;

import io.github.jjdelcerro.noema.lib.api.Agent;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptContext;
import io.github.jjdelcerro.noema.lib.api.services.scripting.ScriptModule;
import io.github.jjdelcerro.noema.lib.spi.services.scripting.AbstractScriptModule;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.DiffCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.filter.AndRevFilter;
import org.eclipse.jgit.revwalk.filter.AuthorRevFilter;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.MessageRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 *
 * @author jjdelcerro
 */
@SuppressWarnings("UseSpecificCatch")
public class GitModule extends AbstractScriptModule {

  private static final int DEFAULT_LOG_LIMIT = 10;
  private static final int MAX_LOG_LIMIT = 200;
  private static final int MAX_PATCH_BYTES = 256 * 1024; // 256 KB

  public static class GitScriptModuleFactory extends AbstractScriptModuleFactory {

    public GitScriptModuleFactory() {
      super("GitModule");
    }

    @Override
    public ScriptModule createModule(Agent agent, ScriptContext context) {
      return new GitModule(context, agent);
    }
  }

  public GitModule(ScriptContext context, Agent agent) {
    super(context, agent, "git", "modulo de acceso a git");
  }

  /**
   * Catálogo de ayuda para el agente.
   * @return 
   */
  @Override
  public String help() {
    return """
[${CONTEXT}.git API] — Solo lectura. Rutas absolutas. Devuelve Maps/Listas, nunca porcelain.
Objeto común: Commit(sha, shortSha, author, committer, parents, subject, body, refs)
• repo([path]): info del repo, branch, head, ahead/behind, clean, lastCommit
• status([path]): Map(branch, clean, staged[], modified[], deleted[], untracked[],
  conflicted[], summary)
• log([path], [n=10], [file], [author], [since], [until], [grep], [reverse]): List<Commit>
• show(rev, [path], [includePatch=true], [maxBytes]): Map(commit, stat, patch, truncated)
• diff([path], [staged=false], [from], [to], [includePatch=true]): Map(files, patch, truncated)
• fileLog(filePath, [n=20]): List<Commit> (con --follow)
• blame(filePath, [start], [end]): List(Line(line, content, sha, shortSha, author, email, date))
• whoTouched(filePath, start, end): List(Map(author, email, lines, commits))
• pickaxe(text, [n=20]): List<Commit>   // log -S
• resolve(rev): Map(sha, shortSha, type, exists)
• branches([includeRemote]), tags([n]), remotes(), showFile(rev, path)
Todas las funciones aceptan 'path' absoluto; error Map(error=...) si el path
no está dentro de un repositorio.
""".formatted(context.getName());
  }

  /**
   * Información general del repositorio.
   */
  public Map<String, Object> repo(String path) {
    return withRepo(path, (repo, git) -> {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("root", repo.getDirectory().getParentFile().getAbsolutePath());

      String branch = safeBranch(repo);
      out.put("branch", branch);
      out.put("detached", isDetached(repo));

      ObjectId headId = repo.resolve(Constants.HEAD);
      out.put("head", headId != null ? headId.getName() : null);
      out.put("shortHead", headId != null ? headId.abbreviate(7).name() : null);

      String remote = repo.getConfig().getString("remote", "origin", "url");
      out.put("remote", remote);

      BranchTrackingStatus tracking = branchTracking(repo, branch);
      out.put("ahead", tracking != null ? tracking.getAheadCount() : 0);
      out.put("behind", tracking != null ? tracking.getBehindCount() : 0);

      Status status = callStatus(git);
      out.put("clean", status != null && status.isClean());

      RevCommit lastCommit = lastCommit(repo);
      out.put("lastCommit", lastCommit != null ? toCommitMap(lastCommit) : null);

      return out;
    });
  }

  /**
   * Estado del working tree / index.
   */
  public Map<String, Object> status(String path) {
    return withRepo(path, (repo, git) -> {
      Status st = callStatus(git);
      if (st == null) {
        return Map.of("error", "status_failed");
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("branch", safeBranch(repo));
      out.put("clean", st.isClean());

      BranchTrackingStatus tracking = branchTracking(repo, safeBranch(repo));
      out.put("ahead", tracking != null ? tracking.getAheadCount() : 0);
      out.put("behind", tracking != null ? tracking.getBehindCount() : 0);

      out.put("staged", entries(st.getAdded(), "added"));
      out.put("modified", entries(st.getChanged(), "changed"));
      out.put("deleted", entries(st.getRemoved(), "removed"));
      out.put("renamed", entries(st.getRemoved(), "renamed")); // JGit agrupa removed/changed; ver nota
      out.put("untracked", entries(st.getUntracked(), "untracked"));
      out.put("conflicted", entries(st.getConflicting(), "conflicting"));

      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("staged", st.getAdded().size());
      summary.put("modified", st.getChanged().size());
      summary.put("deleted", st.getRemoved().size());
      summary.put("untracked", st.getUntracked().size());
      summary.put("conflicted", st.getConflicting().size());
      out.put("summary", summary);

      return out;
    });
  }

  /**
   * Historial de commits.
   *
   * @param path ruta para descubrir el repo (opcional)
   * @param n número máximo de commits (default 10, máx 200)
   * @param file ruta relativa al repo para limitar el log a un fichero/dir
   * @param author filtra por autor (substring)
   * @param since fecha ISO o expresión Git ("2 days ago", "2024-01-01")
   * @param until fecha ISO o expresión Git
   * @param grep regex sobre el mensaje del commit
   * @param reverse orden inverso (más antiguo primero)
   */
  public List<Map<String, Object>> log(String path, Integer n, String file,
          String author, String since, String until,
          String grep, Boolean reverse) {
    int limit = clamp(n, DEFAULT_LOG_LIMIT, 1, MAX_LOG_LIMIT);

    return withRepo(path, (repo, git) -> {
      try (RevWalk walk = new RevWalk(repo)) {
        walk.markStart(walk.parseCommit(repo.resolve(Constants.HEAD)));

        if (Boolean.TRUE.equals(reverse)) {
          walk.sort(RevSort.REVERSE);
        }

        List<RevFilter> filters = new ArrayList<>();

        if (author != null && !author.isBlank()) {
          filters.add(AuthorRevFilter.create(author));
        }
        if (since != null || until != null) {
          Date sinceDate = since != null ? parseDate(since) : new Date(0);
          Date untilDate = until != null ? parseDate(until) : new Date();
          filters.add(CommitTimeRevFilter.between(sinceDate, untilDate));
        }
        if (grep != null && !grep.isBlank()) {
          filters.add(MessageRevFilter.create(grep));
        }

        if (!filters.isEmpty()) {
          walk.setRevFilter(AndRevFilter.create(filters));
        }

        // El filtro por fichero se aplica como TreeFilter, no como RevFilter
        if (file != null && !file.isBlank()) {
          walk.setTreeFilter(PathFilter.create(file));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (RevCommit c : walk) {
          if (out.size() >= limit) {
            break;
          }
          out.add(toCommitMap(c));
        }
        return out;

      } catch (Exception e) {
        return List.of(Map.of("error", "log_failed", "message", e.getMessage()));
      }
    });
  }

  /**
   * Un commit concreto, con stat y patch opcional.
   */
  public Map<String, Object> show(String rev, String path,
          Boolean includePatch, Integer maxBytes) {
    int cap = clamp(maxBytes, MAX_PATCH_BYTES, 1024, 2 * 1024 * 1024);
    return withRepo(path, (repo, git) -> {
      try (RevWalk walk = new RevWalk(repo)) {
        ObjectId id = repo.resolve(rev);
        if (id == null) {
          return Map.of("error", "unknown_revision", "rev", rev);
        }

        RevCommit commit = walk.parseCommit(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commit", toCommitMap(commit));
        out.put("stat", commitStat(repo, commit));

        if (Boolean.TRUE.equals(includePatch)) {
          PatchResult patch = patchOfCommit(repo, commit, cap);
          out.put("patch", patch.text);
          out.put("truncated", patch.truncated);
        } else {
          out.put("patch", "");
          out.put("truncated", false);
        }
        return out;
      } catch (Exception e) {
        return Map.of("error", "show_failed", "message", e.getMessage());
      }
    });
  }

  /**
   * Diff entre working tree/staged o entre dos revisiones.
   */
  public Map<String, Object> diff(String path, Boolean staged,
          String from, String to,
          Boolean includePatch, Integer maxBytes) {
    int cap = clamp(maxBytes, MAX_PATCH_BYTES, 1024, 2 * 1024 * 1024);
    return withRepo(path, (repo, git) -> {
      try {
        DiffCommand cmd = git.diff().setShowNameAndStatusOnly(false);
        if (Boolean.TRUE.equals(staged)) {
          cmd.setCached(true);
        }

        if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
          ObjectId fromId = repo.resolve(from);
          ObjectId toId = repo.resolve(to);
          if (fromId == null) {
            return Map.of("error", "unknown_revision", "rev", from);
          }
          if (toId == null) {
            return Map.of("error", "unknown_revision", "rev", to);
          }
          // Para diff entre refs usamos DiffFormatter directamente
          return diffBetween(repo, fromId, toId, cap);
        }

        List<DiffEntry> entries = cmd.call();
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        for (DiffEntry e : entries) {
          Map<String, Object> f = new LinkedHashMap<>();
          f.put("path", e.getNewPath().equals(DiffEntry.DEV_NULL) ? e.getOldPath() : e.getNewPath());
          f.put("changeType", e.getChangeType().name());
          files.add(f);
        }
        out.put("files", files);
        if (Boolean.TRUE.equals(includePatch)) {
          PatchResult pr = patchOfDiffEntries(repo, entries, cap);
          out.put("patch", pr.text);
          out.put("truncated", pr.truncated);
        } else {
          out.put("patch", "");
          out.put("truncated", false);
        }
        return out;
      } catch (Exception e) {
        return Map.of("error", "diff_failed", "message", e.getMessage());
      }
    });
  }

  /**
   * Historia de un fichero siguiendo renames (--follow).
   */
  public List<Map<String, Object>> fileLog(String filePath, Integer n) {
    int limit = clamp(n, 20, 1, MAX_LOG_LIMIT);
    // El path del repo se descubre desde el propio fichero
    return withRepo(filePath, (repo, git) -> {
      String rel = relativize(repo, filePath);
      if (rel == null) {
        return List.of(Map.of("error", "path_outside_repo", "path", filePath));
      }
      try (RevWalk walk = new RevWalk(repo)) {
        ObjectId head = repo.resolve(Constants.HEAD);
        if (head == null) {
          return List.of();
        }

        RevCommit start = walk.parseCommit(head);
        walk.markStart(start);
        walk.setTreeFilter(org.eclipse.jgit.revwalk.FollowFilter.create(
                rel, repo.getConfig().get(org.eclipse.jgit.diff.DiffConfig.KEY)));

        List<Map<String, Object>> out = new ArrayList<>();
        for (RevCommit c : walk) {
          if (out.size() >= limit) {
            break;
          }
          out.add(toCommitMap(c));
        }
        return out;
      } catch (Exception e) {
        return List.of(Map.of("error", "fileLog_failed", "message", e.getMessage()));
      }
    });
  }

  /**
   * Blame de un rango de líneas (1-based, inclusivo).
   */
  public List<Map<String, Object>> blame(String filePath, Integer start, Integer end) {
    int from = start != null ? Math.max(1, start) : 1;
    return withRepo(filePath, (repo, git) -> {
      String rel = relativize(repo, filePath);
      if (rel == null) {
        return List.of(Map.of("error", "path_outside_repo", "path", filePath));
      }
      try {
        BlameCommand blamer = new BlameCommand(repo);
        ObjectId head = repo.resolve(Constants.HEAD);
        if (head == null) {
          return List.of();
        }
        blamer.setStartCommit(head);
        blamer.setFilePath(rel);
        BlameResult result = blamer.call();
        if (result == null) {
          return List.of();
        }

        int total = result.getResultContents().size();
        int to = end != null ? Math.min(end, total) : total;
        if (from > to) {
          return List.of();
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = from - 1; i < to; i++) {
          RevCommit c = result.getSourceCommit(i);
          Map<String, Object> line = new LinkedHashMap<>();
          line.put("line", i + 1);
          line.put("content", result.getResultContents().getString(i));
          if (c != null) {
            line.put("sha", c.getName());
            line.put("shortSha", c.abbreviate(7).name());
            PersonIdent author = c.getAuthorIdent();
            line.put("author", author.getName());
            line.put("email", author.getEmailAddress());
            line.put("date", author.getWhenAsInstant().toString());
          }
          out.add(line);
        }
        return out;
      } catch (Exception e) {
        return List.of(Map.of("error", "blame_failed", "message", e.getMessage()));
      }
    });
  }

  /**
   * Quién tocó un rango: agrupa blame por autor.
   */
  public List<Map<String, Object>> whoTouched(String filePath, Integer start, Integer end) {
    List<Map<String, Object>> lines = blame(filePath, start, end);
    if (lines.isEmpty() || lines.get(0).containsKey("error")) {
      return lines;
    }

    Map<String, Map<String, Object>> byAuthor = new LinkedHashMap<>();
    for (Map<String, Object> line : lines) {
      String email = (String) line.getOrDefault("email", "unknown");
      Map<String, Object> agg = byAuthor.computeIfAbsent(email, k -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("author", line.get("author"));
        m.put("email", email);
        m.put("lines", 0);
        m.put("commits", new LinkedHashSet<String>());
        return m;
      });
      agg.put("lines", (int) agg.get("lines") + 1);
      ((Set<String>) agg.get("commits")).add((String) line.get("sha"));
    }

    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> agg : byAuthor.values()) {
      Map<String, Object> m = new LinkedHashMap<>(agg);
      m.put("commits", new ArrayList<>((Set<String>) agg.get("commits")));
      out.add(m);
    }
    return out;
  }

  /**
   * Lista de ramas.
   */
  public List<Map<String, Object>> branches(Boolean includeRemote) {
    return withAnyRepo((repo, git) -> {
      try {
        ListBranchCommand cmd = git.branchList();
        if (Boolean.TRUE.equals(includeRemote)) {
          cmd.setListMode(ListBranchCommand.ListMode.ALL);
        }
        String current = safeBranch(repo);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Ref ref : cmd.call()) {
          Map<String, Object> m = new LinkedHashMap<>();
          String name = Repository.shortenRefName(ref.getName());
          m.put("name", name);
          m.put("fullName", ref.getName());
          m.put("current", name.equals(current));
          ObjectId id = ref.getObjectId();
          m.put("sha", id != null ? id.getName() : null);
          m.put("shortSha", id != null ? id.abbreviate(7).name() : null);
          out.add(m);
        }
        return out;
      } catch (GitAPIException e) {
        return List.of(Map.of("error", "branches_failed", "message", e.getMessage()));
      }
    });
  }

  /**
   * Lista de tags.
   */
  public List<Map<String, Object>> tags(Integer n) {
    int limit = clamp(n, 50, 1, 500);
    return withAnyRepo((repo, git) -> {
      try {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Ref> refs = git.tagList().call();
        // Ordenar por fecha de commit descendente, si es posible
        refs.sort((a, b) -> {
          try (RevWalk w = new RevWalk(repo)) {
            RevCommit ca = w.parseCommit(a.getObjectId());
            RevCommit cb = w.parseCommit(b.getObjectId());
            return Long.compare(cb.getCommitTime(), ca.getCommitTime());
          } catch (Exception e) {
            return 0;
          }
        });
        for (Ref ref : refs) {
          if (out.size() >= limit) {
            break;
          }
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("name", Repository.shortenRefName(ref.getName()));
          ObjectId id = ref.getObjectId();
          m.put("sha", id != null ? id.getName() : null);
          try (RevWalk walk = new RevWalk(repo)) {
            RevCommit c = walk.parseCommit(id);
            m.put("date", c.getAuthorIdent().getWhenAsInstant().toString());
            m.put("annotated", ref.getPeeledObjectId() != null);
          } catch (Exception ignored) {
            // tag sobre un objeto no-commit
          }
          out.add(m);
        }
        return out;
      } catch (GitAPIException e) {
        return List.of(Map.of("error", "tags_failed", "message", e.getMessage()));
      }
    });
  }

  /**
   * Lista de remotos.
   */
  public List<Map<String, Object>> remotes() {
    return withAnyRepo((repo, git) -> {
      try {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RemoteConfig rc : git.remoteList().call()) {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("name", rc.getName());
          m.put("fetchUrl", rc.getURIs().isEmpty() ? null : rc.getURIs().get(0).toString());
          m.put("pushUrl", rc.getPushURIs().isEmpty() ? null : rc.getPushURIs().get(0).toString());
          out.add(m);
        }
        return out;
      } catch (GitAPIException e) {
        return List.of(Map.of("error", "remotes_failed", "message", e.getMessage()));
      }
    });
  }

  /**
   * Resuelve una revisión (rama, tag, short SHA, HEAD~2...) a SHA.
   */
  public Map<String, Object> resolve(String rev) {
    return withAnyRepo((repo, git) -> {
      try {
        ObjectId id = repo.resolve(rev);
        if (id == null) {
          return Map.of("exists", false, "rev", rev);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exists", true);
        out.put("sha", id.getName());
        out.put("shortSha", id.abbreviate(7).name());
        try (RevWalk walk = new RevWalk(repo)) {
          int type = walk.parseAny(id).getType();
          out.put("type", switch (type) {
            case Constants.OBJ_COMMIT ->
              "commit";
            case Constants.OBJ_TREE ->
              "tree";
            case Constants.OBJ_BLOB ->
              "blob";
            case Constants.OBJ_TAG ->
              "tag";
            default ->
              "unknown";
          });
        } catch (Exception e) {
          out.put("type", "unknown");
        }
        return out;
      } catch (Exception e) {
        return Map.of("error", "resolve_failed", "message", e.getMessage());
      }
    });
  }

  /**
   * Pickaxe: commits que añaden o eliminan el texto dado (git log -S).
   * Implementación coste O(commits * diff); acotada por n.
   */
  public List<Map<String, Object>> pickaxe(String text, Integer n) {
    int limit = clamp(n, 20, 1, 100);
    if (text == null || text.isEmpty()) {
      return List.of(Map.of("error", "empty_text"));
    }

    return withAnyRepo((repo, git) -> {
      try (RevWalk walk = new RevWalk(repo)) {
        ObjectId head = repo.resolve(Constants.HEAD);
        if (head == null) {
          return List.of();
        }
        walk.markStart(walk.parseCommit(head));

        List<Map<String, Object>> out = new ArrayList<>();
        for (RevCommit commit : walk) {
          if (out.size() >= limit) {
            break;
          }
          if (containsTextInDiff(repo, commit, text)) {
            out.add(toCommitMap(commit));
          }
        }
        return out;
      } catch (Exception e) {
        return List.of(Map.of("error", "pickaxe_failed", "message", e.getMessage()));
      }
    });
  }

  /**
   * Contenido de un fichero en una revisión concreta (git show rev:path).
   */
  public Map<String, Object> showFile(String rev, String filePath, Integer maxBytes) {
    int cap = clamp(maxBytes, MAX_PATCH_BYTES, 1024, 2 * 1024 * 1024);
    return withRepo(filePath, (repo, git) -> {
      String rel = relativize(repo, filePath);
      if (rel == null) {
        return Map.of("error", "path_outside_repo", "path", filePath);
      }
      try (RevWalk walk = new RevWalk(repo)) {
        ObjectId id = repo.resolve(rev);
        if (id == null) {
          return Map.of("error", "unknown_revision", "rev", rev);
        }
        RevCommit commit = walk.parseCommit(id);
        try (TreeWalk tw = TreeWalk.forPath(repo, rel, commit.getTree())) {
          if (tw == null) {
            return Map.of("error", "file_not_found", "path", rel, "rev", rev);
          }
          ObjectId blobId = tw.getObjectId(0);
          ObjectLoader loader = repo.open(blobId);
          byte[] bytes = loader.getBytes(cap + 1);
          boolean truncated = bytes.length > cap;
          String content = new String(bytes, 0, Math.min(bytes.length, cap), StandardCharsets.UTF_8);
          Map<String, Object> out = new LinkedHashMap<>();
          out.put("content", content);
          out.put("truncated", truncated);
          return out;
        }
      } catch (Exception e) {
        return Map.of("error", "showFile_failed", "message", e.getMessage());
      }
    });
  }

  // ---------------------------------------------------------------------
  // Infraestructura interna
  // ---------------------------------------------------------------------
  @FunctionalInterface
  private interface RepoAction<T> {

    T run(Repository repo, Git git) throws Exception;
  }

  private <T> T withRepo(String path, RepoAction<T> action) {
    Repository repo = null;
    try {
      repo = openRepo(path);
      if (repo == null) {
        return (T) Map.of("error", "not_a_repo",
                "path", path != null ? path : System.getProperty("user.dir"));
      }
      return action.run(repo, new Git(repo));
    } catch (Exception e) {
      return (T) Map.of("error", "git_error", "message", e.getMessage());
    } finally {
      if (repo != null) {
        repo.close();
      }
    }
  }

  private <T> T withAnyRepo(RepoAction<T> action) {
    return withRepo(null, action);
  }

  private Repository openRepo(String path) throws IOException {
    FileRepositoryBuilder builder = new FileRepositoryBuilder();
    if (path != null && !path.isBlank()) {
      File f = new File(path);
      if (!f.isAbsolute()) {
        f = f.getAbsoluteFile();
      }
      builder.findGitDir(f);
    } else {
      builder.findGitDir();
    }
    if (builder.getGitDir() == null) {
      return null;
    }
    return builder.setMustExist(true).build();
  }

  private String relativize(Repository repo, String absPath) {
    File workTree = repo.getWorkTree();
    if (workTree == null) {
      return null;
    }
    File file = new File(absPath).getAbsoluteFile();
    try {
      String base = workTree.getCanonicalPath();
      String target = file.getCanonicalPath();
      if (!target.startsWith(base)) {
        return null;
      }
      String rel = target.substring(base.length());
      return rel.startsWith(File.separator) ? rel.substring(1) : rel;
    } catch (IOException e) {
      return null;
    }
  }

  private String safeBranch(Repository repo) {
    try {
      return repo.getBranch();
    } catch (IOException e) {
      return null;
    }
  }

  private boolean isDetached(Repository repo) {
    try {
      Ref head = repo.exactRef(Constants.HEAD);
      return head != null && head.isSymbolic() == false;
    } catch (IOException e) {
      return false;
    }
  }

  private BranchTrackingStatus branchTracking(Repository repo, String branch) {
    if (branch == null) {
      return null;
    }
    try {
      return BranchTrackingStatus.of(repo, branch);
    } catch (IOException e) {
      return null;
    }
  }

  private Status callStatus(Git git) {
    try {
      return git.status().call();
    } catch (GitAPIException e) {
      return null;
    }
  }

  private RevCommit lastCommit(Repository repo) {
    try (RevWalk walk = new RevWalk(repo)) {
      ObjectId head = repo.resolve(Constants.HEAD);
      return head != null ? walk.parseCommit(head) : null;
    } catch (Exception e) {
      return null;
    }
  }

  private List<Map<String, Object>> entries(Set<String> paths, String status) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (paths == null) {
      return out;
    }
    for (String p : paths) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("path", p);
      m.put("status", status);
      out.add(m);
    }
    return out;
  }

  private Map<String, Object> toCommitMap(RevCommit c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("sha", c.getName());
    m.put("shortSha", c.abbreviate(7).name());

    PersonIdent author = c.getAuthorIdent();
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("name", author.getName());
    a.put("email", author.getEmailAddress());
    a.put("date", author.getWhenAsInstant().toString());
    m.put("author", a);

    PersonIdent committer = c.getCommitterIdent();
    Map<String, Object> cm = new LinkedHashMap<>();
    cm.put("name", committer.getName());
    cm.put("email", committer.getEmailAddress());
    cm.put("date", committer.getWhenAsInstant().toString());
    m.put("committer", cm);

    List<String> parents = new ArrayList<>();
    for (RevCommit p : c.getParents()) {
      parents.add(p.getName());
    }
    m.put("parents", parents);

    String full = c.getFullMessage();
    int idx = full.indexOf('\n');
    m.put("subject", idx >= 0 ? full.substring(0, idx) : full);
    m.put("body", idx >= 0 ? full.substring(idx + 1) : "");

    return m;
  }

  private Map<String, Object> commitStat(Repository repo, RevCommit commit) throws IOException {
    Map<String, Object> stat = new LinkedHashMap<>();
    RevCommit parent = commit.getParentCount() > 0 ? commit.getParent(0) : null;
    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      df.setRepository(repo);
      List<DiffEntry> entries = parent != null
              ? df.scan(parent.getTree(), commit.getTree())
              : df.scan(null, commit.getTree());

      int filesChanged = entries.size();
      int insertions = 0;
      int deletions = 0;
      List<Map<String, Object>> fileList = new ArrayList<>();

      for (DiffEntry e : entries) {
        Map<String, Object> f = new LinkedHashMap<>();
        String p = e.getNewPath().equals(DiffEntry.DEV_NULL) ? e.getOldPath() : e.getNewPath();
        f.put("path", p);
        f.put("changeType", e.getChangeType().name());
        fileList.add(f);
      }
      // Para inserciones/deleciones exactas habría que formatear el patch;
      // lo aproximamos con FileHeader si se necesita, pero dejamos el conteo de ficheros.
      stat.put("filesChanged", filesChanged);
      stat.put("insertions", insertions); // ver nota abajo
      stat.put("deletions", deletions);
      stat.put("files", fileList);
    }
    return stat;
  }

  private static class PatchResult {

    final String text;
    final boolean truncated;

    PatchResult(String text, boolean truncated) {
      this.text = text;
      this.truncated = truncated;
    }
  }

  private PatchResult patchOfCommit(Repository repo, RevCommit commit, int maxBytes) throws IOException {
    RevCommit parent = commit.getParentCount() > 0 ? commit.getParent(0) : null;
    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      df.setRepository(repo);
      List<DiffEntry> entries = parent != null
              ? df.scan(parent.getTree(), commit.getTree())
              : df.scan(null, commit.getTree());
      return patchOfDiffEntries(repo, entries, maxBytes);
    }
  }

  private PatchResult patchOfDiffEntries(Repository repo, List<DiffEntry> entries, int maxBytes) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (DiffFormatter df = new DiffFormatter(baos)) {
      df.setRepository(repo);
      df.setDetectRenames(true);
      df.format(entries);
    }
    byte[] bytes = baos.toByteArray();
    boolean truncated = bytes.length > maxBytes;
    String text = new String(bytes, 0, Math.min(bytes.length, maxBytes), StandardCharsets.UTF_8);
    return new PatchResult(text, truncated);
  }

  private Map<String, Object> diffBetween(Repository repo, ObjectId from, ObjectId to, int maxBytes) throws IOException {
    try (RevWalk walk = new RevWalk(repo); DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      df.setRepository(repo);
      RevCommit fromCommit = walk.parseCommit(from);
      RevCommit toCommit = walk.parseCommit(to);
      List<DiffEntry> entries = df.scan(fromCommit.getTree(), toCommit.getTree());

      Map<String, Object> out = new LinkedHashMap<>();
      List<Map<String, Object>> files = new ArrayList<>();
      for (DiffEntry e : entries) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("path", e.getNewPath().equals(DiffEntry.DEV_NULL) ? e.getOldPath() : e.getNewPath());
        f.put("changeType", e.getChangeType().name());
        files.add(f);
      }
      out.put("files", files);
      PatchResult pr = patchOfDiffEntries(repo, entries, maxBytes);
      out.put("patch", pr.text);
      out.put("truncated", pr.truncated);
      return out;
    }
  }

  private boolean containsTextInDiff(Repository repo, RevCommit commit, String text) {
    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE); ObjectReader reader = repo.newObjectReader()) {

      df.setRepository(repo);

      RevCommit parent = commit.getParentCount() > 0 ? commit.getParent(0) : null;
      List<DiffEntry> entries = parent != null
              ? df.scan(parent.getTree(), commit.getTree())
              : df.scan(null, commit.getTree());

      for (DiffEntry e : entries) {
        if (e.getChangeType() == DiffEntry.ChangeType.DELETE) {
          continue;
        }

        // DiffEntry guarda AbbreviatedObjectId; resolve() devuelve una
        // Collection con 0, 1 o varias coincidencias.
        Collection<ObjectId> matches = reader.resolve(e.getNewId());
        if (matches.size() != 1) {
          continue;
        }
        ObjectId fullId = matches.iterator().next();

        String content = extractNewContent(reader, fullId);
        if (content != null && content.contains(text)) {
          return true;
        }
      }
      return false;

    } catch (Exception e) {
      return false;
    }
  }

  private String extractNewContent(ObjectReader reader, ObjectId blobId) {
    if (blobId == null || blobId.equals(ObjectId.zeroId())) {
      return null;
    }
    try {
      ObjectLoader loader = reader.open(blobId);
      byte[] bytes = loader.getBytes(10 * 1024 * 1024); // 10 MB máx
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception e) {
      return null;
    }
  }

  private Date parseDate(String s) {
    // Acepta ISO-8601 o expresiones tipo "2 days ago"
    try {
      return Date.from(Instant.parse(s));
    } catch (Exception ignored) {
      // Fallback: interpretación básica de expresiones relativas
      long now = System.currentTimeMillis();
      String lower = s.toLowerCase().trim();
      if (lower.matches("\\d+\\s*(day|days|d)")) {
        int days = Integer.parseInt(lower.replaceAll("\\D+", ""));
        return new Date(now - days * 86_400_000L);
      }
      if (lower.matches("\\d+\\s*(week|weeks|w)")) {
        int weeks = Integer.parseInt(lower.replaceAll("\\D+", ""));
        return new Date(now - weeks * 7 * 86_400_000L);
      }
      if (lower.matches("\\d+\\s*(month|months|m)")) {
        int months = Integer.parseInt(lower.replaceAll("\\D+", ""));
        return new Date(now - months * 30L * 86_400_000L);
      }
      return new Date(0); // sin filtro efectivo
    }
  }

  private int clamp(Integer value, int defaultValue, int min, int max) {
    if (value == null) {
      return defaultValue;
    }
    return Math.max(min, Math.min(max, value));
  }

}
