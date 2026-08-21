package io.github.jjdelcerro.noema.lib.impl.services.reasoning;

import dev.langchain4j.data.message.ChatMessage;
import java.nio.file.Path;
import java.util.List;

public interface ProjectedMemory {

  /**
   * Devuelve la lista inmutable y curada de mensajes lista para ser enviada al
   * LLM.
   *
   * @return
   */
  List<ChatMessage> getMessages();

  /**
   * Devuelve la lista de resource_ids que estan en zona de riesgo y no han sido
   * anotados.
   *
   * @return
   */
  List<String> getPendingAnnotationResources();

  /**
   * Vuelca a disco la proyeccion exacta en formato JSON para depuracion.
   *
   * @param path
   */
  void dump(Path path);
}
