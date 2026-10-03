

# Especificación Técnica: Gobernanza Dinámica de Identidad Nuclear por Subcanal (`ReasoningService`)

* **Módulo Afectado:** `io.github.jjdelcerro.noema.lib.services.reasoning` / `io.github.jjdelcerro.noema.lib.impl.services.reasoning`
* **Subsistema Consumidor:** `io.github.jjdelcerro.noema.main.NoemaWebServer`
* **Fecha:** Octubre 2026

---

## 1. Contexto y Objetivo

Noema estructura la personalidad y reglas operativas de su agente a través de módulos declarativos ubicados en `var/identity/core/` (ej. `01_identidad_base.md`, `02_protocolo_operativo.md`). Históricamente, la activación de estos módulos se resolvía de forma global a través de la lista persistente `reasoning/identity/core` en `settings.json`.

Dado que Noema es un sistema multi-terminal capaz de atender sesiones simultáneas mediante identificadores de conversación (`subchannel`), se requiere un mecanismo que permita **activar o desactivar módulos de identidad en memoria de forma dinámica y acotada a un subcanal específico**, sin alterar la configuración global del espacio de trabajo ni contaminar otros canales concurrentes (como la consola CLI, la GUI de escritorio o sesiones paralelas).

El caso de uso principal es la activación del **Protocolo Oral** (`05_protocolo_oral.md`) mientras una sesión web mantiene abierto su canal de audio en tiempo real.

---

## 2. Definición del Contrato

### 2.1. Ampliación de la Interfaz `ReasoningService`
Se añade a la interfaz pública del servicio cognitivo el método de control de módulos del núcleo:

```java
package io.github.jjdelcerro.noema.lib.services.reasoning;

public interface ReasoningService extends AgentService {
    // ... métodos existentes ...

    /**
     * Activa o desactiva dinámicamente un módulo de identidad nuclear (var/identity/core/)
     * para un subcanal conversacional concreto en tiempo de ejecución.
     *
     * @param subchannel Identificador técnico del terminal o canal (ej: "default", "web-voice-1").
     * @param coreFileName Nombre base o relativo del fichero en var/identity/core/ (ej: "05_protocolo_oral").
     * @param active true para forzar su inclusión en el System Prompt; false para forzar su exclusión.
     */
    void setActiveIdentityCore(String subchannel, String coreFileName, boolean active);

    /**
     * Limpia cualquier sobreescritura dinámica de identidad para un subcanal,
     * devolviéndolo a las directivas por defecto de la configuración global.
     *
     * @param subchannel Identificador del subcanal a restablecer.
     */
    void clearActiveIdentityCoreOverrides(String subchannel);
}
```

---

## 3. Implementación en `ReasoningServiceImpl`

### 3.1. Estado Interno en Memoria
La clase implementadora mantendrá una tabla concurrente para gestionar las sobreescrituras sin persistencia a disco:

```java
// Estructura: subchannel -> (coreFileName -> active)
private final Map<String, Map<String, Boolean>> subchannelCoreOverrides = new ConcurrentHashMap<>();
```

Implementación de los métodos de gestión:

```java
@Override
public void setActiveIdentityCore(String subchannel, String coreFileName, boolean active) {
    if (StringUtils.isBlank(subchannel) || StringUtils.isBlank(coreFileName)) {
        return;
    }
    String cleanName = FilenameUtils.getBaseName(coreFileName.trim());
    this.subchannelCoreOverrides
            .computeIfAbsent(subchannel.trim(), k -> new ConcurrentHashMap<>())
            .put(cleanName, active);
}

@Override
public void clearActiveIdentityCoreOverrides(String subchannel) {
    if (StringUtils.isNotBlank(subchannel)) {
        this.subchannelCoreOverrides.remove(subchannel.trim());
    }
}
```

### 3.2. Resolución de Identidad Nuclear en Cascada
Se modifica la resolución del prompt de sistema para desacoplar el ensamblado estático global y parametrizarlo según el subcanal que está ejecutando el turno.

Dentro de `getBaseSystemPrompt(String subchannel)`:

```java
// CAPA 2a: Constitución e Identidad Core
sb.append("# CONSTITUCIÓN Y REGLAS OPERATIVAS\n");

AgentSettingsCheckedList coreSettings = agent.getSettings().getPropertyAsCheckedList("reasoning/identity/core");
List<Path> coreFiles = agent.getPaths().listAgentPath("var/identity/core");

if (coreFiles != null && !coreFiles.isEmpty()) {
    Collections.sort(coreFiles);
    for (Path path : coreFiles) {
        String fileName = path.getFileName().toString();
        if (StringUtils.equalsIgnoreCase(fileName, "readme.md")) {
            continue;
        }

        String baseName = FilenameUtils.getBaseName(fileName);
        boolean isActive = false;

        // 1. Prioridad: Sobreescritura dinámica en memoria específica para este subcanal
        Map<String, Boolean> overrides = this.subchannelCoreOverrides.get(subchannel);
        if (overrides != null && overrides.containsKey(baseName)) {
            isActive = overrides.get(baseName);
        } else {
            // 2. Fallback: Resolución contra la configuración persistida global
            if (coreSettings != null) {
                isActive = coreSettings.getItems().stream()
                        .filter(item -> baseName.equals(item.getValue()))
                        .anyMatch(AgentSettingsCheckedList.CheckedItem::isChecked);
            }
        }

        if (isActive) {
            String content = agent.getResourceAsString("var/identity/core/" + fileName);
            if (StringUtils.isNotBlank(content)) {
                sb.append("## Módulo: ").append(fileName).append("\n");
                sb.append(content).append("\n\n");
            }
        }
    }
}
```

---

## 4. Integración en `NoemaWebServer`

El ciclo de vida del WebSocket de audio (`/api/voice/{terminalId}`) gestiona de forma transparente la activación y desactivación del protocolo:

### 4.1. Apertura del Canal de Audio (Handshake)
Al conectarse el cliente web e iniciar la sesión de voz:
```java
ws.onConnect(ctx -> {
    String terminalId = ctx.pathParam("terminalId");
    ReasoningService reasoning = (ReasoningService) agent.getService(ReasoningService.NAME);
    if (reasoning != null) {
        reasoning.setActiveIdentityCore(terminalId, "05_protocolo_oral", true);
        LOGGER.info("Subcanal '{}': activado protocolo oral de voz en el núcleo.", terminalId);
    }
});
```

### 4.2. Cierre o Interrupción del Canal de Audio
Al colgar el usuario, cerrarse la pestaña o perderse la conexión de red:
```java
ws.onClose(ctx -> {
    String terminalId = ctx.pathParam("terminalId");
    ReasoningService reasoning = (ReasoningService) agent.getService(ReasoningService.NAME);
    if (reasoning != null) {
        reasoning.setActiveIdentityCore(terminalId, "05_protocolo_oral", false);
        LOGGER.info("Subcanal '{}': restaurado modo de texto estándar.", terminalId);
    }
});
```

---

## 5. Módulo de Directivas: `05_protocolo_oral.md`

El archivo físico se ubicará en la jerarquía estándar: `src/main/resources/io/github/jjdelcerro/noema/lib/impl/resources/var/identity/core/05_protocolo_oral.md`

### Contenido Normativo:
```markdown
# 05. PROTOCOLO OPERATIVO PARA SESIONES ORALES EN TIEMPO REAL

## 1. Naturaleza del Canal Activo
Estás conectado al usuario mediante una llamada de voz interactiva en tiempo real.
Tu respuesta textual será procesada inmediatamente por un motor de síntesis de voz (TTS) 
para ser reproducida por los altavoces o auriculares del usuario.

## 2. Restricciones Absolutas de Expresión (Sintaxis Prohibida)
* PROHIBIDO EL USO DE MARKDOWN TÉCNICO: No generes encabezados (#), tablas, bloques de 
  código (```), listas numeradas extensas ni enlaces de hipertexto en tu respuesta oral. 
  El sintetizador intentará leer estos símbolos y degradará la experiencia.
* BREVEDAD ESTRICTA: Limita tus respuestas a un rango óptimo de entre 1 y 4 oraciones 
  (30 a 80 palabras). Habla de forma natural, cadenciosa y directa.
* PROHIBIDAS LAS FÓRMULAS DE CORTESÍA O DISCULPAS: No comiences con agradecimientos ni 
  explicaciones procedimentales innecesarias.

## 3. Desdoblamiento Visual ("La Pizarra"): Uso de display_screen
Si para responder a la consulta del usuario es estrictamente necesario mostrar código 
fuente, estructuras de datos, tablas comparativas, parches diff o volcados de logs:
1. TIENES PROHIBIDO dictar o describir la sintaxis de estos bloques por el canal de voz.
2. Invoca OBLIGATORIAMENTE la herramienta `display_screen` pasando el contenido completo, 
   su lenguaje o formato y un título descriptivo. Este contenido se proyectará 
   silenciosamente en la pantalla del usuario.
3. En tu respuesta oral, limítate a resumir el concepto clave e invitar al usuario a 
   mirar la pantalla (ejemplo: "Te he dejado la implementación en pantalla; observa cómo 
   utilizamos un AtomicBoolean para coordinar el hilo").
```

---

## 6. Garantías Arquitectónicas

1. **Inmutabilidad de Almacenamiento:** No se escribe en `settings.json` ni en disco durante los cambios de modo. Si la máquina virtual termina de forma abrupta, no quedan estados corruptos.
2. **Concurrencia Segura:** La tabla `subchannelCoreOverrides` opera sobre estructuras seguras para entornos multihilo.
3. **Aislamiento Multi-terminal:** Cada subcanal conversa bajo su propio conjunto de directivas activas. Una llamada de voz en el navegador no altera la interacción de texto que ocurra en paralelo en la interfaz Swing o en la TUI.
4. **Generalización del Patrón:** La infraestructura queda preparada para gobernar cualquier otro comportamiento operativo especializado mediante módulos declarativos bajo demanda.
