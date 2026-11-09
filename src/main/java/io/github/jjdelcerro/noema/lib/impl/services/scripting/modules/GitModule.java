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
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
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
  private static final int MAX_STAT_FILES = 100;

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
• status([path]): Map(branch, clean, ahead, behind, stagedAdded[], stagedModified[],
  stagedDeleted[], modified[], deleted[], renamed[{path,oldPath}], untracked[],
  conflicted[], summary). modified/deleted = working tree; staged* = indice.
• log([path], [n<=200], [file], [author], [since], [until], [grep], [reverse]):
  List<Commit>. 'file' es relativo a la raiz del repo.
• show(rev, [path], [includePatch=false], [maxBytes]): Map(commit, stat, patch, truncated)
  stat.files se recorta a 100 entradas; ver stat.filesTotal y stat.statTruncated.
  insertions/deletions son conteos reales de lineas (0/0 solo si el commit es
  binario o vacio), aunque files este recortado.
• diff([path], [staged=false], [from], [to], [includePatch=false], [maxBytes]):
  Map(files, patch, truncated). staged=false: indice<->working tree, NO incluye
  lo que ya esta staged; staged=true: HEAD<->indice. Siempre de repo completo,
  nunca de un fichero.
• fileLog(filePath, [n=20]): List<Commit> (con --follow)
• blame(filePath, [start], [end]): List(Line(line, content, sha, shortSha, author, email, date))
• whoTouched(filePath, start, end): List(Map(author, email, lines, commits))
  start/end son numeros de linea del fichero, no fechas ni commits.
• pickaxe([path], text, [n=20]): List<Commit>   // log -S
  Matchea si el commit cambia el NUMERO de apariciones de text en algun
  fichero que toca. Tocar un fichero que ya contenia el texto no cuenta.
• resolve([path], rev): Map(sha, shortSha, type, exists). exists=false es una
  respuesta valida, no un error.
• branches([path], [includeRemote]), tags([path], [n]), remotes([path])
• showFile(rev, filePath, [maxBytes])
'n' esta acotado: log y fileLog a 200; pickaxe a 100; tags a 500.
ERRORES: las funciones lanzan GitException cuando no pueden responder; nunca
devuelven un valor de error. Codigos: not_a_repo, unknown_revision,
path_outside_repo, file_not_found, empty_text, status_failed, show_failed,
diff_failed, log_failed, fileLog_failed, blame_failed, branches_failed,
tags_failed, remotes_failed, resolve_failed, pickaxe_failed, showFile_failed,
git_error. El mensaje llega como 'codigo: mensaje', sin stack trace.
Captura con: try { ... } catch (e) { println e.code }
Vacio NO es error: log sin resultados, blame sin historia o repo sin ramas
devuelven lista vacia.
'path' es siempre la raiz de un repo; para ficheros usa filePath (blame,
fileLog, whoTouched) o showFile.
Si omites path en repo, status, log, show, diff, pickaxe, resolve, branches,
tags o remotes, se usa el repo del directorio de trabajo por defecto, NO el de
la ultima ruta usada en otra llamada. Pasa path siempre que no sea el cwd.
""".formatted(context.getName());
  }

  // ------------------------------------------------------------------
  // Convenience overloads. The long signatures stay available for
  // scripts that need every knob; these apply the documented defaults
  // so that help() does not advertise parameters that cannot be
  // omitted. Without them, log(path, 10) threw MissingMethodException
  // and aborted the whole script.
  // ------------------------------------------------------------------

  public Map<String, Object> repo() {
    return repo(null);
  }

  public Map<String, Object> status() {
    return status(null);
  }

  public List<Map<String, Object>> log(String path) {
    return log(path, null, null, null, null, null, null, null);
  }

  public List<Map<String, Object>> log(String path, Integer n) {
    return log(path, n, null, null, null, null, null, null);
  }

  public List<Map<String, Object>> log(String path, Integer n, String file) {
    return log(path, n, file, null, null, null, null, null);
  }

  public Map<String, Object> show(String rev, String path) {
    return show(rev, path, null, null);
  }

  public Map<String, Object> show(String rev, String path, Boolean includePatch) {
    return show(rev, path, includePatch, null);
  }

  public Map<String, Object> diff(String path) {
    return diff(path, null, null, null, null, null);
  }

  public Map<String, Object> diff(String path, Boolean staged) {
    return diff(path, staged, null, null, null, null);
  }

  public Map<String, Object> diff(String path, Boolean staged, String from, String to) {
    return diff(path, staged, from, to, null, null);
  }

  public List<Map<String, Object>> fileLog(String filePath) {
    return fileLog(filePath, null);
  }

  public List<Map<String, Object>> blame(String filePath) {
    return blame(filePath, null, null);
  }

  public List<Map<String, Object>> branches() {
    return branches(null);
  }

  public List<Map<String, Object>> tags() {
    return tags(null);
  }

  public List<Map<String, Object>> pickaxe(String text) {
    return pickaxe(null, text, null);
  }

  /**
   * Pickaxe sobre un repositorio concreto.
   */
  public List<Map<String, Object>> pickaxe(String path, String text) {
    return pickaxe(path, text, null);
  }

  public Map<String, Object> showFile(String rev, String filePath) {
    return showFile(rev, filePath, null);
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
        throw new GitException("status_failed", "git status returned no information");
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("branch", safeBranch(repo));
      out.put("clean", st.isClean());

      BranchTrackingStatus tracking = branchTracking(repo, safeBranch(repo));
      out.put("ahead", tracking != null ? tracking.getAheadCount() : 0);
      out.put("behind", tracking != null ? tracking.getBehindCount() : 0);

      List<Map<String, Object>> renamed = renamedEntries(repo);
      int stagedCount = st.getAdded().size() + st.getChanged().size() + st.getRemoved().size();

      // Working tree vs index vs HEAD are three different planes. Keep them apart.
      out.put("stagedAdded", entries(st.getAdded(), "added"));
      out.put("stagedModified", entries(st.getChanged(), "modified"));
      out.put("stagedDeleted", entries(st.getRemoved(), "deleted"));
      out.put("modified", entries(st.getModified(), "modified"));
      out.put("deleted", entries(st.getMissing(), "deleted"));
      out.put("renamed", renamed);
      out.put("untracked", entries(st.getUntracked(), "untracked"));
      out.put("conflicted", entries(st.getConflicting(), "conflicted"));

      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("staged", stagedCount);
      summary.put("stagedAdded", st.getAdded().size());
      summary.put("stagedModified", st.getChanged().size());
      summary.put("stagedDeleted", st.getRemoved().size());
      summary.put("modified", st.getModified().size());
      summary.put("deleted", st.getMissing().size());
      summary.put("renamed", renamed.size());
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
        throw wrap("log_failed", e);
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
          throw new GitException("unknown_revision", "cannot resolve revision: " + rev);
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
        throw wrap("show_failed", e);
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
            throw new GitException("unknown_revision", "cannot resolve revision: " + from);
          }
          if (toId == null) {
            throw new GitException("unknown_revision", "cannot resolve revision: " + to);
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
        throw wrap("diff_failed", e);
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
        throw new GitException("path_outside_repo", "file is not inside the working tree: " + filePath);
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
        throw wrap("fileLog_failed", e);
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
        throw new GitException("path_outside_repo", "file is not inside the working tree: " + filePath);
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
        throw wrap("blame_failed", e);
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
    return branches(null, includeRemote);
  }

  /**
   * Lista de ramas de un repositorio concreto.
   */
  public List<Map<String, Object>> branches(String path, Boolean includeRemote) {
    return withRepo(path, (repo, git) -> {
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
        throw wrap("branches_failed", e);
      }
    });
  }

  /**
   * Lista de tags.
   */
  public List<Map<String, Object>> tags(Integer n) {
    return tags(null, n);
  }

  /**
   * Lista de tags de un repositorio concreto.
   */
  public List<Map<String, Object>> tags(String path, Integer n) {
    int limit = clamp(n, 50, 1, 500);
    return withRepo(path, (repo, git) -> {
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
        throw wrap("tags_failed", e);
      }
    });
  }

  /**
   * Lista de remotos.
   */
  public List<Map<String, Object>> remotes() {
    return remotes(null);
  }

  /**
   * Lista de remotos de un repositorio concreto.
   */
  public List<Map<String, Object>> remotes(String path) {
    return withRepo(path, (repo, git) -> {
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
        throw wrap("remotes_failed", e);
      }
    });
  }

  /**
   * Resuelve una revisión (rama, tag, short SHA, HEAD~2...) a SHA.
   */
  public Map<String, Object> resolve(String rev) {
    return resolve(null, rev);
  }

  /**
   * Resuelve una revision en un repositorio concreto.
   */
  public Map<String, Object> resolve(String path, String rev) {
    return withRepo(path, (repo, git) -> {
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
        throw wrap("resolve_failed", e);
      }
    });
  }

  /**
   * Pickaxe: commits que añaden o eliminan el texto dado (git log -S).
   * Implementación coste O(commits * diff); acotada por n. Los recursos de
   * lectura se comparten durante todo el recorrido del historial.
   */
  public List<Map<String, Object>> pickaxe(String text, Integer n) {
    return pickaxe(null, text, n);
  }

  /**
   * Pickaxe sobre un repositorio concreto (git log -S).
   */
  public List<Map<String, Object>> pickaxe(String path, String text, Integer n) {
    int limit = clamp(n, 20, 1, 100);
    if (text == null || text.isEmpty()) {
      throw new GitException("empty_text", "pickaxe needs a non-empty text to look for");
    }

    return withRepo(path, (repo, git) -> {
      // One walk, one reader and one formatter for the whole scan, instead of
      // reopening them for every commit. No measurable speedup was observed
      // (the formatter keeps its own internal reader anyway); they are shared
      // only so the resource lifecycle spans the whole query.
      try (RevWalk walk = new RevWalk(repo);
              ObjectReader reader = repo.newObjectReader();
              DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        df.setRepository(repo);
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
          if (containsTextInDiff(walk, df, reader, commit, text)) {
            out.add(toCommitMap(commit));
          }
        }
        return out;
      } catch (Exception e) {
        throw wrap("pickaxe_failed", e);
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
        throw new GitException("path_outside_repo", "file is not inside the working tree: " + filePath);
      }
      try (RevWalk walk = new RevWalk(repo)) {
        ObjectId id = repo.resolve(rev);
        if (id == null) {
          throw new GitException("unknown_revision", "cannot resolve revision: " + rev);
        }
        RevCommit commit = walk.parseCommit(id);
        try (TreeWalk tw = TreeWalk.forPath(repo, rel, commit.getTree())) {
          if (tw == null) {
            throw new GitException("file_not_found",
                    "file " + rel + " does not exist in " + rev);
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
        throw wrap("showFile_failed", e);
      }
    });
  }

  // ---------------------------------------------------------------------
  // Infraestructura interna
  // ---------------------------------------------------------------------
  /**
   * Raised when the module cannot answer a request. It is never returned as a
   * value: it aborts the calling script on purpose, so a failed call cannot be
   * mistaken for an empty result.
   * <p>
   * Codes: not_a_repo, unknown_revision, path_outside_repo, file_not_found,
   * empty_text, status_failed, show_failed, diff_failed, log_failed,
   * fileLog_failed, blame_failed, branches_failed, tags_failed, remotes_failed,
   * resolve_failed, pickaxe_failed, showFile_failed, git_error.
   * <p>
   * The stack trace is not filled on purpose: a script only needs the message.
   */
  public static class GitException extends RuntimeException {

    private final String code;

    public GitException(String code, String message) {
      super(code + ": " + message, null, false, false);
      this.code = code;
    }

    public String getCode() {
      return code;
    }
  }

  @FunctionalInterface
  private interface RepoAction<T> {

    T run(Repository repo, Git git) throws Exception;
  }

  private <T> T withRepo(String path, RepoAction<T> action) {
    Repository repo = null;
    try {
      repo = openRepo(path);
      if (repo == null) {
        throw new GitException("not_a_repo",
                "not a git working tree: " + pathOrUserDir(path));
      }
      return action.run(repo, new Git(repo));
    } catch (GitException e) {
      throw e;
    } catch (Exception e) {
      throw wrap("git_error", e);
    } finally {
      if (repo != null) {
        repo.close();
      }
    }
  }

  /**
   * Keeps the original GitException if the cause already is one, so that a
   * specific code is not masked by the generic catch of the enclosing block.
   */
  private static GitException wrap(String code, Exception cause) {
    if (cause instanceof GitException) {
      return (GitException) cause;
    }
    String message = cause.getMessage();
    if (message == null || message.isBlank()) {
      message = cause.getClass().getSimpleName();
    }
    return new GitException(code, message);
  }

  private static String pathOrUserDir(String path) {
    if (path != null && !path.isBlank()) {
      return path;
    }
    return System.getProperty("user.dir");
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

  /**
   * Real renames, comparing HEAD against the index. Status.getRenamed() only
   * returns a set of paths with no origin, so the pair has to come from
   * DiffEntry with rename detection enabled.
   *
   * @param repo repository to inspect
   * @return list of {path, oldPath, status}
   */
  private List<Map<String, Object>> renamedEntries(Repository repo) {
    List<Map<String, Object>> out = new ArrayList<>();
    try {
      ObjectId headId = repo.resolve(Constants.HEAD);
      if (headId == null) {
        return out;
      }
      try (RevWalk walk = new RevWalk(repo);
              ObjectReader reader = repo.newObjectReader()) {
        RevCommit head = walk.parseCommit(headId);
        CanonicalTreeParser oldTree = new CanonicalTreeParser(null, reader, head.getTree());
        DirCacheIterator newTree = new DirCacheIterator(repo.readDirCache());
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
          df.setRepository(repo);
          df.setDetectRenames(true);
          for (DiffEntry e : df.scan(oldTree, newTree)) {
            if (e.getChangeType() != DiffEntry.ChangeType.RENAME) {
              continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", e.getNewPath());
            m.put("oldPath", e.getOldPath());
            m.put("status", "renamed");
            out.add(m);
          }
        }
      }
    } catch (Exception e) {
      // Empty repository, locked index or unreadable refs: no renames to report.
      return new ArrayList<>();
    }
    return out;
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

  /**
   * Returns the tree of the first parent, or null for a root commit.
   *
   * RevWalk.parseCommit() only parses the body of the commit that was asked
   * for. The parents it links stay unparsed, and getTree() on an unparsed
   * RevCommit returns null without raising. DiffFormatter.scan(null, tree)
   * means "diff against the empty tree", so every entry would look like an
   * ADD and deletions would always be zero. The parent must be parsed here
   * before handing its tree to the formatter.
   */
  private static AnyObjectId parentTree(Repository repo, RevCommit commit) throws IOException {
    try (RevWalk walk = new RevWalk(repo)) {
      return parentTree(walk, commit);
    }
  }

  /**
   * Same as above, reusing a caller-owned walk instead of opening a new one per
   * commit. The parent of commit N is usually the commit N+1 already parsed in
   * the previous iteration, but no measurable speedup was observed; this exists
   * so the caller keeps control over the walk lifecycle.
   */
  private static AnyObjectId parentTree(RevWalk walk, RevCommit commit) throws IOException {
    if (commit.getParentCount() == 0) {
      return null;
    }
    RevCommit parent = walk.parseCommit(commit.getParent(0).getId());
    return parent.getTree().copy();
  }

  private Map<String, Object> commitStat(Repository repo, RevCommit commit) throws IOException {
    Map<String, Object> stat = new LinkedHashMap<>();
    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      df.setRepository(repo);
      List<DiffEntry> entries = df.scan(parentTree(repo, commit), commit.getTree());

      int filesChanged = entries.size();
      int insertions = 0;
      int deletions = 0;
      List<Map<String, Object>> fileList = new ArrayList<>();

      for (DiffEntry e : entries) {
        // Line counts come from the edit ranges, there is no need to render
        // the patch as text. Binary entries produce no edits and count 0/0.
        FileHeader fh = df.toFileHeader(e);
        for (Edit ed : fh.toEditList()) {
          insertions += ed.getEndB() - ed.getBeginB();
          deletions += ed.getEndA() - ed.getBeginA();
        }
        if (fileList.size() < MAX_STAT_FILES) {
          Map<String, Object> f = new LinkedHashMap<>();
          String p = e.getNewPath().equals(DiffEntry.DEV_NULL) ? e.getOldPath() : e.getNewPath();
          f.put("path", p);
          f.put("changeType", e.getChangeType().name());
          fileList.add(f);
        }
      }
      stat.put("filesChanged", filesChanged);
      stat.put("filesTotal", filesChanged);
      stat.put("insertions", insertions);
      stat.put("deletions", deletions);
      stat.put("files", fileList);
      stat.put("statTruncated", filesChanged > fileList.size());
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
    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      df.setRepository(repo);
      List<DiffEntry> entries = df.scan(parentTree(repo, commit), commit.getTree());
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

  /**
   * True if the commit changes the number of occurrences of text in any of the
   * files it touches. This is the semantics of git log -S (pickaxe): a commit
   * matches only when it adds or removes occurrences, not when it merely
   * touches a file that already contains the text somewhere else.
   */
  private boolean containsTextInDiff(RevWalk walk, DiffFormatter df, ObjectReader reader, RevCommit commit, String text) {
    try {

      List<DiffEntry> entries = df.scan(parentTree(walk, commit), commit.getTree());

      for (DiffEntry e : entries) {
        String before = readBlobText(reader, e.getOldId());
        String after = readBlobText(reader, e.getNewId());
        if (countOccurrences(before, text) != countOccurrences(after, text)) {
          return true;
        }
      }
      return false;

    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Text of the blob behind an AbbreviatedObjectId, or null when it cannot be
   * resolved. The absent side of an ADD or a DELETE lands here as zero
   * matches, which yields null and therefore zero occurrences.
   */
  private String readBlobText(ObjectReader reader, AbbreviatedObjectId abbrev) throws IOException {
    if (abbrev == null) {
      return null;
    }
    // DiffEntry guarda AbbreviatedObjectId; resolve() devuelve una
    // Collection con 0, 1 o varias coincidencias.
    Collection<ObjectId> matches = reader.resolve(abbrev);
    if (matches == null || matches.size() != 1) {
      return null;
    }
    return extractBlobText(reader, matches.iterator().next());
  }

  /**
   * Counts non-overlapping occurrences of needle in haystack.
   */
  private static int countOccurrences(String haystack, String needle) {
    if (haystack == null || needle == null || needle.isEmpty()) {
      return 0;
    }
    int count = 0;
    int idx = haystack.indexOf(needle);
    while (idx >= 0) {
      count++;
      idx = haystack.indexOf(needle, idx + needle.length());
    }
    return count;
  }

  private String extractBlobText(ObjectReader reader, ObjectId blobId) {
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
