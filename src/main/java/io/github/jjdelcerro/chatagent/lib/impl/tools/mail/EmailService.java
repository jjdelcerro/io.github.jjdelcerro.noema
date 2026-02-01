package io.github.jjdelcerro.chatagent.lib.impl.tools.mail;

import com.google.gson.Gson;
import io.github.jjdelcerro.chatagent.lib.impl.ConversationManagerImpl;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.apache.tika.Tika;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Gestor unificado de comunicaciones por correo electrónico que implementa una
 * estrategia de
 * <b>"Secretaria Virtual"</b> para el Agente.
 * <p>
 * A diferencia de las herramientas de mensajería instantánea, esta clase
 * gestiona la asimetría entre el volumen de datos de un email y la ventana de
 * contexto del LLM mediante una arquitectura de tres pilares:
 * <ol>
 * <li><b>Sensor Proactivo (Push de Metadatos):</b> Un hilo demonio monitorea la
 * bandeja de entrada usando IMAP IDLE. Al detectar un correo relevante,
 * <b>NO</b> descarga el contenido. En su lugar, inyecta una notificación ligera
 * (UID + Asunto) en el flujo del agente vía {@code putEvent}.</li>
 *
 * <li><b>Efector de Lectura (Pull de Contenido):</b> Proporciona métodos para
 * que el agente, tras decidir que un correo es importante, solicite
 * explícitamente su contenido. Utiliza
 * <b>Apache Tika</b> para sanear el HTML y adjuntos, entregando solo texto
 * limpio al modelo.</li>
 *
 * <li><b>Efector de Acción (Envío):</b> Permite al agente redactar y enviar
 * respuestas vía SMTP.</li>
 * </ol>
 * <p>
 * <b>Seguridad y Filtros:</b>
 * El servicio implementa un filtrado estricto mediante
 * {@code authorizedSender}. El sensor ignorará silenciosamente cualquier correo
 * que no provenga de la dirección autorizada, actuando como un firewall
 * cognitivo para evitar spam o inyecciones de prompt no autorizadas.
 *
 * @author jjdelcerro
 */
public class EmailService {

  private final String user, password, imapHost, smtpHost;
  private final String authorizedSender;
  private final Tika tika = new Tika();
  private final Gson gson = new Gson();

  public EmailService(String imapHost, String smtpHost, String user, String password, String authorizedSender) {
    this.imapHost = imapHost;
    this.smtpHost = smtpHost;
    this.user = user;
    this.password = password;
    this.authorizedSender = authorizedSender;
  }

  // --- Lógica de Actuación ---
  public String send(String to, String subject, String body) {
    try {
      Properties props = new Properties();
      props.put("mail.smtp.auth", "true");
      props.put("mail.smtp.starttls.enable", "true");
      props.put("mail.smtp.host", smtpHost);
      props.put("mail.smtp.port", "587");

      Session session = Session.getInstance(props, new Authenticator() {
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(user, password);
        }
      });

      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(user));
      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
      message.setSubject(subject);
      message.setText(body);
      Transport.send(message);
      return "{\"status\":\"success\"}";
    } catch (Exception e) {
      return "{\"status\":\"error\", \"msg\":\"" + e.getMessage() + "\"}";
    }
  }

  // --- Lógica de Consulta (Cabeceras) ---
  public String listHeaders() {
    try {
      return executeInInbox(inbox -> {
        int count = inbox.getMessageCount();
        int start = Math.max(1, count - 9);
        Message[] msgs = inbox.getMessages(start, count);
        List<Map<String, String>> headers = new ArrayList<>();
        for (Message m : msgs) {
          long uid = ((UIDFolder) inbox).getUID(m);
          headers.add(Map.of("uid", String.valueOf(uid), "from", m.getFrom()[0].toString(), "subject", m.getSubject()));
        }
        return gson.toJson(headers);
      });
    } catch (Exception e) {
      return "{\"error\":\"" + e.getMessage() + "\"}";
    }
  }

  // --- Lógica de Lectura (Tika) ---
  public String read(long uid) {
    try {
      return executeInInbox(inbox -> {
        Message m = ((UIDFolder) inbox).getMessageByUID(uid);
        if (m == null) {
          return "{\"error\":\"Mensaje no encontrado\"}";
        }
        // Tika procesa el stream del mensaje (limpia HTML, extrae de adjuntos, etc.)
        String cleanContent = tika.parseToString(m.getInputStream());
        return gson.toJson(Map.of("uid", uid, "content", cleanContent.trim()));
      });
    } catch (Exception e) {
      return "{\"error\":\"" + e.getMessage() + "\"}";
    }
  }

  // --- Percepción Proactiva (IDLE) ---
  public void startListener(ConversationManagerImpl agent) {
    Thread t = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          executeInInbox(inbox -> {
            int lastCount = inbox.getMessageCount();
            while (inbox.isOpen()) {
              invokeIdleSafely(inbox);
              int current = inbox.getMessageCount();
              if (current > lastCount) {
                Message m = inbox.getMessage(current);
                String from = ((InternetAddress) m.getFrom()[0]).getAddress();
                if (from.equalsIgnoreCase(authorizedSender)) {
                  long uid = ((UIDFolder) inbox).getUID(m);
                  // INYECTAMOS SOLO LA NOTIFICACIÓN
                  String notify = String.format("NUEVO EMAIL [UID:%d] de %s. Asunto: %s", uid, from, m.getSubject());
                  agent.putEvent("Email", "normal", notify);
                }
                lastCount = current;
              }
            }
            return null;
          });
        } catch (Exception e) {
          try {
            Thread.sleep(10000);
          } catch (InterruptedException ex) {
            break;
          }
        }
      }
    }, "EmailListener");
    t.setDaemon(true);
    t.start();
  }

  // Helper para gestionar la apertura/cierre de sesión IMAP
  private <T> T executeInInbox(FolderAction<T> action) throws Exception {
    Properties props = new Properties();
    props.put("mail.store.protocol", "imaps");
    Session session = Session.getInstance(props);
    try (Store store = session.getStore("imaps")) {
      store.connect(imapHost, user, password);
      try (Folder inbox = store.getFolder("INBOX")) {
        inbox.open(Folder.READ_ONLY);
        return action.run(inbox);
      }
    }
  }

  private void invokeIdleSafely(Folder folder) {
    try {
      Method m = folder.getClass().getMethod("idle");
      m.invoke(folder);
    } catch (Exception e) {
      try {
        Thread.sleep(30000);
      } catch (InterruptedException ex) {
      }
    }
  }

  @FunctionalInterface
  private interface FolderAction<T> {

    T run(Folder f) throws Exception;
  }

  public static void install(ConversationManagerImpl agent, String imap, String smtp, String user, String pass, String boss) {
    EmailService service = new EmailService(imap, smtp, user, pass, boss);

    // Instalamos las herramientas para que el agente pueda ACTUAR
    agent.addTool(new EmailSendTool(service));
    agent.addTool(new EmailListTool(service));
    agent.addTool(new EmailReadTool(service));

    // Arrancamos el hilo para que el agente pueda PERCIBIR
    service.startListener(agent);
  }
}
