package io.github.jjdelcerro.noema.lib.services.sensors;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

/**
 * Extensión de SensorEvent que define el API necesario para que el orquestador
 * pueda consumir el evento e integrarlo en el flujo de conversación del LLM.
 */
public interface ConsumableSensorEvent extends SensorEvent {

  /**
   * Devuelve la representación JSON del evento que será enviada al LLM.
   *
   * @return
   */
  String toJson();

  /**
   * Genera el mensaje de IA (ficticio) que solicita la herramienta pool_event.
   *
   * @return
   */
  ChatMessage getChatMessage();

  /**
   * Genera el mensaje de resultado de ejecución de herramienta con el contenido
   * del evento.
   *
   * @return
   */
  ToolExecutionResultMessage getResponseMessage();
}
