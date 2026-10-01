package io.github.jjdelcerro.noema.lib.impl.model;

import io.github.jjdelcerro.noema.lib.Agent;
import io.github.jjdelcerro.noema.lib.model.ChatModel;
import io.github.jjdelcerro.noema.lib.model.ModelParameters;
import io.github.jjdelcerro.noema.lib.model.ModelType;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjdelcerro
 */
public abstract class AbstractChatModel implements ChatModel {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractChatModel.class);

  protected final ModelParameters parameters;

  protected AbstractChatModel(ModelParameters parameters) {
    this.parameters = parameters;
  }

  protected void dumpAllThreads() {
    try {
      ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
      ThreadInfo[] threadInfos = threadBean.dumpAllThreads(true, true);
      StringBuilder sb = new StringBuilder();
      for (ThreadInfo info : threadInfos) {
        sb.append(info.toString()).append('\n');
      }
      LOGGER.warn("Thread dump:\n{}", sb.toString());
    } catch (Exception e) {
      LOGGER.warn("No se pudo volcar los hilos", e);
    }
  }

  @Override
  public int getContextSize() {
    return this.parameters.contextSize();
  }

  @Override
  public ModelParameters getParameters() {
    return parameters;
  }

  @Override
  public ModelType getModelType() {
    return this.parameters.getModelType();
  }

}
