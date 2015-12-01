/*** BEGIN META {
	"name": "Logs of the latest failed builds",
	"comment": "Creates a report on the latest failed builds",
	"parameters": ["HOURS_BACK", "LOG_LINES", "BUILD_NODE"],
	"core": "1.300",
	"authors": [
		{name : "Alexey Zalesnyi"}
	]
} END META**/

hoursBack = new Integer(HOURS_BACK)
logLines = new Integer(LOG_LINES)

// start time for our selection
startTm = new Date().time - hoursBack * 3600 * 10 ** 3

import hudson.model.*

hud = Hudson.instance

println hud.computers.findAll {
	! BUILD_NODE || it.name == BUILD_NODE
}.collect {
	it.builds.findAll {
		isRecent = it.timeInMillis + it.duration > startTm
		isRecent && ! it.inProgress && it.result == Result.FAILURE
	}.collect {
		"$hud.rootUrl$it.url\n\t" + it.getLog(logLines).join('\n\t')
	}
}.flatten().join('\n\n')