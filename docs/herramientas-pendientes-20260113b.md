Implementar una herramienta para leer correos es el siguiente paso lógico para convertir a tu agente en un "asistente de operaciones" completo. La forma más estándar y "simple" en Java es usar el protocolo **IMAP** a través de la librería **Jakarta Mail** (antiguamente conocida como JavaMail).

Para tu PoC, lo ideal es dividir esto en dos herramientas: una para **listar** los últimos correos (cabeceras) y otra para **leer** el contenido de uno específico. Así no saturamos la ventana de contexto del LLM.

### 1. Dependencia necesaria
Añade esto a tu `pom.xml`. Usaremos la implementación de referencia actual (Angus Mail):

```xml
<dependency>
    <groupId>org.eclipse.angus</groupId>
    <artifactId>jakarta.mail</artifactId>
    <version>2.0.3</version>
</dependency>
```

### 2. El Servicio de Conexión (EmailService)
Dado que leer correos implica lidiar con sesiones SSL y parseo de contenido `Multipart` (texto plano vs HTML), he preparado una lógica simplificada.

**Nota de seguridad:** Para Gmail u Outlook, no uses tu contraseña normal; usa una **"Contraseña de Aplicación"** (App Password).

### 3. Implementación de `EmailListTool` (Listar correos)

```java
package io.github.jjdelcerro.chatagent.lib.impl.tools.email;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import jakarta.mail.*;
import java.util.*;

public class EmailListTool implements AgenteTool {
    private final String host, user, password;
    private final Gson gson = new Gson();

    public EmailListTool(String host, String user, String password) {
        this.host = host; this.user = user; this.password = password;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("email_list_recent")
                .description("Lista los asuntos y remitentes de los correos más recientes en la bandeja de entrada.")
                .addParameter("limit", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("Número de correos a listar (máx 10)"))
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, Double> args = gson.fromJson(jsonArguments, Map.class);
            int limit = args.containsKey("limit") ? args.get("limit").intValue() : 5;

            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            Session session = Session.getInstance(props);
            
            try (Store store = session.getStore()) {
                store.connect(host, user, password);
                Folder inbox = store.getFolder("INBOX");
                inbox.open(Folder.READ_ONLY);

                int total = inbox.getMessageCount();
                int start = Math.max(1, total - limit + 1);
                Message[] messages = inbox.getMessages(start, total);

                List<Map<String, String>> emailList = new ArrayList<>();
                for (int i = messages.length - 1; i >= 0; i--) {
                    Message m = messages[i];
                    emailList.add(Map.of(
                        "index", String.valueOf(m.getMessageNumber()),
                        "from", m.getFrom()[0].toString(),
                        "subject", m.getSubject(),
                        "date", m.getSentDate().toString()
                    ));
                }
                return gson.toJson(Map.of("status", "success", "emails", emailList));
            }
        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
```

### 4. Implementación de `EmailReadTool` (Leer un correo)

```java
package io.github.jjdelcerro.chatagent.lib.impl.tools.email;

import com.google.gson.Gson;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.github.jjdelcerro.chatagent.lib.tools.AgenteTool;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMultipart;
import java.util.Map;
import java.util.Properties;

public class EmailReadTool implements AgenteTool {
    private final String host, user, password;
    private final Gson gson = new Gson();

    public EmailReadTool(String host, String user, String password) {
        this.host = host; this.user = user; this.password = password;
    }

    @Override
    public ToolSpecification getSpecification() {
        return ToolSpecification.builder()
                .name("email_read_body")
                .description("Lee el contenido de un correo específico usando su índice.")
                .addParameter("index", JsonSchemaProperty.INTEGER, JsonSchemaProperty.description("El índice del correo obtenido con email_list_recent"))
                .build();
    }

    @Override
    public String execute(String jsonArguments) {
        try {
            Map<String, Double> args = gson.fromJson(jsonArguments, Map.class);
            int index = args.get("index").intValue();

            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            Session session = Session.getInstance(props);

            try (Store store = session.getStore()) {
                store.connect(host, user, password);
                Folder inbox = store.getFolder("INBOX");
                inbox.open(Folder.READ_ONLY);

                Message m = inbox.getMessage(index);
                String content = getTextFromMessage(m);

                return gson.toJson(Map.of(
                    "status", "success",
                    "subject", m.getSubject(),
                    "body", content
                ));
            }
        } catch (Exception e) {
            return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // Helper para extraer texto plano de un mensaje (omitimos HTML complejo por simplicidad)
    private String getTextFromMessage(Message message) throws Exception {
        if (message.isMimeType("text/plain")) return message.getContent().toString();
        if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            for (int i = 0; i < mimeMultipart.getCount(); i++) {
                BodyPart bodyPart = mimeMultipart.getBodyPart(i);
                if (bodyPart.isMimeType("text/plain")) return bodyPart.getContent().toString();
            }
        }
        return "[No se pudo extraer texto plano]";
    }
}
```

---

### ¿Cómo encaja esto en tu Experimento de Memoria?

1.  **Contexto para el `MemoryManager`:** Cuando el agente revise el correo, el `MemoryManager` (DeepSeek) podrá narrar en "El Viaje":
    > *"El agente consultó la bandeja de entrada para verificar si habían llegado instrucciones nuevas {cite: 900}. Identificó un correo del cliente sobre el bug del POM y procedió a leer el cuerpo del mensaje para obtener los detalles técnicos {cite: 901}."*
2.  **Uso de Tika:** Como mencionaste antes, si luego introduces **Tika**, podrás mejorar `getTextFromMessage`. Tika es excelente para "limpiar" correos que vienen solo en HTML pesado o para extraer texto de archivos adjuntos.
3.  **Configuración en `Main`:**
    ```java
    // Ejemplo para Gmail
    String host = "imap.gmail.com";
    String mailUser = "tu_correo@gmail.com";
    String mailPass = "tu_app_password"; 

    agent.addTool(new EmailListTool(host, mailUser, mailPass));
    agent.addTool(new EmailReadTool(host, mailUser, mailPass));
    ```

### El valor para el LLM (Devstral)
Con estas herramientas, tu PoC puede reaccionar a eventos externos. Por ejemplo, podrías decirle: *"Revisa si me han enviado el error del servidor por correo y, si es así, busca en la base de datos de turnos si ya lo habíamos discutido antes"*. 

Esto pone a prueba toda tu arquitectura: **Email (Input externo) -> DB Query (Introspección) -> El Viaje (Contexto histórico)**. ¡Es un test de integración perfecto!