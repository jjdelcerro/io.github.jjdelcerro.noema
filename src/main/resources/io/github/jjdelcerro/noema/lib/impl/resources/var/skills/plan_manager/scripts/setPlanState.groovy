// scripts/setPlanState.groovy
import lib.PlanCore
def name  = (args && args[0]) ? args[0].toString().trim() : null
def state = (args && args[1]) ? args[1].toString().trim() : null
if (!name)  return "ERROR: args = ['nombre', 'running'|'stopped'|'creating'|'done']"
if (!state) return "ERROR: args = ['nombre', 'running'|'stopped'|'creating'|'done']"
return new PlanCore(agent,context).setPlanState(name, state)
