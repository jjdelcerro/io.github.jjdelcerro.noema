// scripts/addTask.groovy
import lib.PlanCore
return new PlanCore(agent,context).addTask(args ?: [])

