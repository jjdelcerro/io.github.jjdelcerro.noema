// scripts/setTaskState.groovy
import lib.PlanCore
def id    = (args && args.size() > 0) ? args[0].toString() : null
def state = (args && args.size() > 1) ? args[1].toString() : null
if (id == null)    return "ERROR: args = ['id', '~'|'x'|'-'|' ']"
if (state == null) return "ERROR: args = ['id', '~'|'x'|'-'|' ']"
return new PlanCore(agent,context).setTaskState(id, state)
