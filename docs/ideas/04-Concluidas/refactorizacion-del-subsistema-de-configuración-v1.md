
### **Documento de Tareas: Refactorización del Subsistema de Configuración (`AgentSettings`)**

**1. Objetivo**

El objetivo de esta tarea es refactorizar el subsistema `AgentSettings` para reemplazar la actual estructura plana basada en `java.util.Properties` por un modelo de datos jerárquico, con tipos definidos y persistencia en formato JSON. Esto mejorará la organización, la seguridad de tipos y la flexibilidad de la configuración del agente.

**2. Arquitectura General**

El nuevo subsistema se basará en un árbol de objetos de configuración, donde cada nodo es un `AgentSettingsItem`. Habrá tres tipos de nodos:
1.  **Grupo (`AgentSettingsGroup`):** Un nodo que contiene otros `AgentSettingsItem`s, similar a un objeto JSON.
2.  **String (`AgentSettingsString`):** Un nodo hoja que contiene un valor de texto.
3.  **Lista de Rutas (`AgentSettingsPaths`):** Un nodo hoja que contiene una lista de rutas de fichero.

La clase principal `AgentSettings` será, en sí misma, el grupo raíz de toda la configuración. El acceso a las propiedades se realizará mediante una sintaxis de "path" (ej: `"conversation/provider/model_id"`). La persistencia se realizará en un único fichero `settings.json`.

**3. Tareas Detalladas de Implementación**

#### Tarea 1: Estructura de Paquetes

Crear dos nuevos paquetes para organizar las clases e interfaces relacionadas con esta refactorización:
*   `io.github.jjdelcerro.noema.lib.settings`
*   `io.github.jjdelcerro.noema.lib.impl.settings`

#### Tarea 2: Definición de las Interfaces (Contrato Público)

Dentro del paquete `io.github.jjdelcerro.noema.lib.settings`, crear las siguientes interfaces:

1.  **`AgentSettingsItem.java`**:
    *   Será la interfaz base o marcadora. No necesita métodos por ahora.

2.  **`AgentSettingsString.java`**:
    *   Debe extender `AgentSettingsItem`.
    *   Debe declarar un método: `String getValue()`.

3.  **`AgentSettingsPaths.java`**:
    *   Debe extender `AgentSettingsItem`.
    *   Debe declarar un método: `List<Path> getValues()`.

4.  **`AgentSettingsGroup.java`**:
    *   Debe extender `AgentSettingsItem`.
    *   Debe declarar los siguientes métodos para el acceso a propiedades:
        *   `AgentSettingsItem getProperty(String path)`: Devuelve el nodo `Item` genérico en la ruta especificada, o `null` si no se encuentra.
        *   `String getPropertyAsString(String path)`: Devuelve el valor `String` de una propiedad. Devuelve `null` si la ruta no existe o si el nodo no es de tipo `String`.
        *   `String getPropertyAsString(String path, String defaultValue)`: Sobrecarga que devuelve un valor por defecto.
        *   `int getPropertyAsInt(String path, int defaultValue)`: Método de conveniencia que lee como `String` y lo convierte a `int`.
        *   `long getPropertyAsLong(String path, long defaultValue)`: Ídem para `long`.
        *   `List<Path> getPropertyAsPaths(String path)`: Devuelve el valor `List<Path>` de una propiedad. Devuelve una lista vacía si la ruta no existe o no es de tipo `Paths`.
        *   `AgentSettingsGroup getPropertyGroup(String path)`: Devuelve un subgrupo. Devuelve `null` si la ruta no existe o no es de tipo `Group`.
    *   Debe declarar los siguientes métodos para la modificación de propiedades:
        *   `void setProperty(String path, String value)`: Establece un valor de tipo `String`.
        *   `void setProperty(String path, List<String> values)`: Establece un valor de tipo `List` de rutas (como strings).

5.  **`AgentSettings.java`**:
    *   Debe extender `AgentSettingsGroup`.
    *   Debe mantener los métodos de gestión existentes: `load()`, `save()`, `setupSettings()`, `getPaths()`, etc.

#### Tarea 3: Implementación de las Clases del Modelo

Dentro del paquete `io.github.jjdelcerro.noema.lib.impl.settings`, crear las siguientes clases:

1.  **`AgentSettingsStringImpl.java`**:
    *   Implementará `AgentSettingsString`.
    *   Tendrá un campo privado `String value`.
    *   Será una clase simple, similar a un POJO o un Record.

2.  **`AgentSettingsPathsImpl.java`**:
    *   Implementará `AgentSettingsPaths`.
    *   Tendrá un campo privado `List<String> rawValues`.
    *   El método `getValues()` se encargará de convertir la `List<String>` a `List<Path>` al momento de ser llamado (usando `stream().map(Path::of)`).

3.  **`AgentSettingsGroupImpl.java`**:
    *   Implementará `AgentSettingsGroup`.
    *   Tendrá un campo principal: `private final Map<String, AgentSettingsItem> items = new ConcurrentHashMap<>();`.
    *   **Implementar la lógica de acceso por `path`**:
        *   Crear un método privado `findNode(String path, boolean createParents)` que será el núcleo.
        *   Este método debe separar el `path` por `/`.
        *   Debe recorrer las partes del path, descendiendo en el `Map`.
        *   Si `createParents` es `true` (para los `setters`), debe crear `AgentSettingsGroupImpl` intermedios si no existen.
        *   Debe manejar correctamente los casos de error (intentar descender sobre un nodo que no es un grupo, etc.).
    *   **Implementar los `get...`**: Llamarán a `findNode` (con `createParents=false`), verificarán que el `Item` devuelto es del tipo correcto (`instanceof`), harán el `cast` y devolverán el valor. Si algo falla, devuelven `null` o el `defaultValue`.
    *   **Implementar los `set...`**: Llamarán a `findNode` (con `createParents=true`) para obtener el nodo padre, y luego crearán e insertarán el `Item` (`StringImpl` o `PathsImpl`) en el `Map` del padre.

4.  **`AgentSettingsImpl.java`**:
    *   Hacer que extienda `AgentSettingsGroupImpl` e implemente `AgentSettings`.
    *   **Reimplementar `load()`**:
        *   Debe leer el fichero `settings.json`.
        *   Debe usar una instancia de `Gson` configurada con el deserializador personalizado (ver Tarea 4).
        *   Debe deserializar el JSON en un `Map<String, AgentSettingsItem>` y poblar su `Map` interno.
        *   **No es necesario implementar la migración desde `.properties`**.
    *   **Reimplementar `save()`**:
        *   Debe usar una instancia de `Gson` (configurada con `setPrettyPrinting()`).
        *   Debe serializar su `Map` interno a un `String` JSON.
        *   Debe escribir este `String` en el fichero `settings.json`.

#### Tarea 4: Implementar el Deserializador de Gson

1.  **`AgentSettingsItemDeserializer.java`**:
    *   Crear esta clase en el paquete `...impl.settings`.
    *   Debe implementar `JsonDeserializer<AgentSettingsItem>`.
    *   En el método `deserialize(JsonElement json, ...)`:
        *   Verificar si `json` es un `JsonPrimitive` de tipo `String`. Si es así, crear y devolver un `new AgentSettingsStringImpl(json.getAsString())`.
        *   Verificar si `json` es un `JsonArray`. Si es así, iterar el array, extraer los strings y devolver un `new AgentSettingsPathsImpl(listOfStrings)`.
        *   Verificar si `json` es un `JsonObject`. Si es así, crear un `new AgentSettingsGroupImpl()` y, para cada entrada del objeto JSON, llamar recursivamente a `context.deserialize(entry.getValue(), AgentSettingsItem.class)` para poblar el `Map` del grupo.

#### Tarea 5: Refactorización del Código Existente

1.  **Actualizar `AgentManagerImpl`**: El método `createSettings()` debe instanciar el nuevo `AgentSettingsImpl`.
2.  **Actualizar todos los Servicios**: Realizar una búsqueda global de `settings.getProperty(...)`. Reemplazar todas las llamadas por el método correspondiente (`getPropertyAsString`, `getPropertyAsLong`, etc.) y la nueva estructura de `path`.
3.  **Actualizar UI de Configuración**: Modificar `AgentConsoleSettingsImpl` y `AgentSwingSettingsImpl`. El campo `variableName` en el `settingsui.json` ahora contendrá el path completo. El código Java que lee y escribe estos valores deberá usar la nueva API de `AgentSettings`.
4.  **Eliminar Clases/Lógica obsoleta**: Una vez completada la migración, se puede limpiar el código relacionado con la carga/gestión de `Properties`.

---
**Ejemplo de fichero `settings.json` final:**

```json
{
  "conversation": {
    "provider": {
      "url": "https://openrouter.ai/api/v1",
      "model_id": "z-ai/glm-4.5-air:free",
      "api_key": "tu_api_key"
    }
  },
  "memory": {
    "provider": {
      "url": "https://openrouter.ai/api/v1",
      "model_id": "tngtech/deepseek-r1t2-chimera:free",
      "api_key": "tu_api_key"
    },
    "compaction_threshold": 40
  },
  "debug": {
    "h2_webport": "8082"
  },
  "allowed_external_paths": [
    "/tmp/noema_data",
    "/home/user/shared_docs"
  ]
}
```


# Anexo I: Introducción del tipo `AgentSettingsCheckedList`

## 1. Objetivo
Extender la jerarquía de tipos de `AgentSettings` para soportar listas de elementos donde cada uno posea un estado booleano independiente. Este tipo de dato es fundamental para permitir que el usuario active o desactive capacidades (herramientas) o marcos de referencia (documentos de gobierno) de forma dinámica desde la interfaz de usuario.

## 2. Definición de Interfaces (Paquete `...lib.settings`)

### 2.1. `AgentSettingsCheckedList`
Debe extender `AgentSettingsItem`.
*   **Método:** `List<CheckedItem> getItems()`
*   **Interfaz interna `CheckedItem`**:
    *   `boolean isChecked()`
    *   `String getValue()`

## 3. Implementación del Modelo (Paquete `...lib.impl.settings`)

### 3.1. `AgentSettingsCheckedListImpl`
*   Implementará `AgentSettingsCheckedList`.
*   Tendrá un campo privado: `private List<CheckedItemImpl> items`.
*   **Representación en JSON**: Se serializará como un array de objetos.
    ```json
    "active_tools": [
      {"checked": true, "value": "file_read"},
      {"checked": false, "value": "shell_execute"}
    ]
    ```

### 3.2. Actualización del Deserializador (`AgentSettingsItemDeserializer`)
El método `deserialize` debe detectar si un `JsonArray` contiene objetos con la propiedad `"checked"`.
*   Si el array contiene objetos con la estructura `{checked, value}` -> Instanciar `AgentSettingsCheckedListImpl`.
*   Si el array contiene solo strings -> Instanciar `AgentSettingsPathsImpl` (comportamiento original).

## 4. Implementación en la Interfaz de Usuario (Paquete `...ui.swing`)

### 4.1. Componente `CheckedList`
En la clase `AgentSwingSettingsImpl`, se añadirá la lógica para el tipo `"checkedlist"`:
*   **Componente:** Se utilizará una `JList` personalizada.
*   **Renderer/Editor:** Se implementará un `ListCellRenderer` que dibuje un `JCheckBox` para cada elemento.
*   **Interacción:** El usuario podrá marcar o desmarcar el checkbox directamente en la lista. Cada clic en un checkbox debe:
    1.  Actualizar el estado en el objeto `AgentSettingsCheckedListImpl`.
    2.  Marcar el subsistema de configuración como `dirty`.
    3.  Lanzar el proceso de `save()` automático (opcional, según política de UI).

## 5. Casos de Uso Principales

### 5.1. Gestión de Herramientas (Tools)
Permite al usuario definir qué herramientas son visibles para el LLM.
*   **Entrada en `settingsui.json`**:
    ```json
    {
      "type": "checkedlist",
      "label": "Herramientas Activas",
      "variableName": "conversation/active_tools",
      "actionName": "REFRESH_TOOLS"
    }
    ```
*   **Efecto:** Al desmarcar una herramienta, el `ConversationService` (vía la acción asociada) reconstruye la lista de `ToolSpecifications` que envía al modelo.

### 5.2. Gestión de Documentos de Gobierno (Anclajes)
Permite gestionar qué manuales o protocolos indexados rigen la conversación actual.
*   **Efecto:** Solo los documentos marcados como `checked: true` son procesados por el `DocumentsService` para ser inyectados en la **Capa 2** del "Sandwich de Contexto". Los desmarcados permanecen en el sistema pero no consumen tokens ni distraen al modelo.

## 6. Lógica de Modificación en el `Group`
Se añadirá a `AgentSettingsGroup` un método para facilitar la actualización de estos estados desde el código (por ejemplo, desde la herramienta `pin_document_as_context`):
*   `void setChecked(String path, String value, boolean checked)`: Busca el elemento `value` dentro de la lista en `path` y actualiza su estado. Si el elemento no existe en la lista, lo añade.

**Nota para el desarrollador:** *La implementación de la `JList` con checkboxes en Swing requiere un manejo cuidadoso de los eventos de ratón para que el cambio de estado del booleano sea fluido y se persista correctamente en el modelo de configuración.*
