// scripts/getSections.groovy
import lib.PlanCore
def titles = (args != null) ? args.collect { it.toString() } : []
return new PlanCore(agent,context).getSections(titles)