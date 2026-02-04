package io.github.jjdelcerro.chatagent.lib.impl.services.email;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentService;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import static io.github.jjdelcerro.chatagent.lib.impl.services.email.EmailService.EMAIL_AUTHORIZED_SENDER;
import static io.github.jjdelcerro.chatagent.lib.impl.services.email.EmailService.EMAIL_IMAP_HOST;
import static io.github.jjdelcerro.chatagent.lib.impl.services.email.EmailService.EMAIL_PASSWORD;
import static io.github.jjdelcerro.chatagent.lib.impl.services.email.EmailService.EMAIL_SMTP_HOST;
import static io.github.jjdelcerro.chatagent.lib.impl.services.email.EmailService.EMAIL_USER;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class EmailServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return EmailService.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new EmailService(this, agent);
  }
  
  @Override
  public boolean canStart(AgentSettings settings) {
    String[] names = new String[]{
      EMAIL_IMAP_HOST,
      EMAIL_SMTP_HOST,
      EMAIL_USER,
      EMAIL_PASSWORD,
      EMAIL_AUTHORIZED_SENDER
    };
    for (String name : names) {
      String v = settings.getProperty(name);
      if (StringUtils.isBlank(v)) {
        return false;
      }
    }
    return true;
  }  
}
