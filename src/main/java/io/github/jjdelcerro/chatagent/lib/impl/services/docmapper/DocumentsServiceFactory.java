package io.github.jjdelcerro.chatagent.lib.impl.services.docmapper;

import io.github.jjdelcerro.chatagent.lib.Agent;
import io.github.jjdelcerro.chatagent.lib.AgentService;
import io.github.jjdelcerro.chatagent.lib.AgentServiceFactory;
import io.github.jjdelcerro.chatagent.lib.AgentSettings;
import static io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.DocumentsService.DOCMAPPER_BASIC_MODEL_ID;
import static io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.DocumentsService.DOCMAPPER_BASIC_PROVIDER_API_KEY;
import static io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.DocumentsService.DOCMAPPER_BASIC_PROVIDER_URL;
import static io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.DocumentsService.DOCMAPPER_REASONING_MODEL_ID;
import static io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.DocumentsService.DOCMAPPER_REASONING_PROVIDER_API_KEY;
import static io.github.jjdelcerro.chatagent.lib.impl.services.docmapper.DocumentsService.DOCMAPPER_REASONING_PROVIDER_URL;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author jjdelcerro
 */
public class DocumentsServiceFactory implements AgentServiceFactory {

  @Override
  public String getName() {
    return DocumentsService.NAME;
  }

  @Override
  public AgentService createService(Agent agent) {
    return new DocumentsServiceImpl(this, agent);
  }
  
  @Override
  public boolean canStart(AgentSettings settings) {
    String[] names = new String[]{
      DOCMAPPER_REASONING_PROVIDER_URL,
      DOCMAPPER_REASONING_PROVIDER_API_KEY,
      DOCMAPPER_REASONING_MODEL_ID,
      DOCMAPPER_BASIC_PROVIDER_URL,
      DOCMAPPER_BASIC_PROVIDER_API_KEY,
      DOCMAPPER_BASIC_MODEL_ID
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
