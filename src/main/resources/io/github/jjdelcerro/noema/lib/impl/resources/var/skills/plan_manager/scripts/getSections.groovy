// scripts/getSections.groovy
import lib.PlanCore
def titles = (args != null) ? args.collect { it.toString() } : []
return PlanCore.getSections(agent, titles)