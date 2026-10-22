package io.github.jjdelcerro.noema.ui.webclient.workspace.notes;

import io.github.jjdelcerro.noema.lib.api.Agent;

import java.nio.file.Path;

/**
 * Punto unico de construccion del almacen de notas para la capa web.
 *
 * Los handlers no deberian saber como se compone la ruta de las notas: si
 * cambia su ubicacion, se cambia aqui y no en seis sitios distintos.
 *
 * @author jjdelcerro
 */
public final class NotesSupport {

  /** Nombre de la carpeta de notas dentro de la carpeta del agente. */
  public static final String NOTES_FOLDER = "notes";

  /** Subcarpeta de cache de busquedas dentro de la carpeta del agente. */
  private static final String CACHE_FOLDER = "var/cache/notes-search";

  private NotesSupport() {
  }

  /**
   * Construye el almacen de notas sobre la carpeta del agente en curso.
   *
   * @param agent agente propietario
   * @return almacen de notas
   */
  public static NoteStore store(Agent agent) {
    return new NoteStore(agent.getPaths().getAgentFolder().resolve(NOTES_FOLDER));
  }

  /**
   * Devuelve la carpeta donde vive la cache de busquedas de notas.
   *
   * Cuelga de 'var' y no de 'notes': es material regenerable y no debe
   * mezclarse con el corpus.
   *
   * @param agent agente propietario
   * @return carpeta de cache
   */
  public static Path cacheFolder(Agent agent) {
    return agent.getPaths().getAgentFolder().resolve(CACHE_FOLDER);
  }
}
