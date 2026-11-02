// scripts/createPlan.groovy
import lib.PlanCore
def name = (args && args[0]) ? args[0].toString().trim() : null
return PlanCore.createPlan(agent, name)
