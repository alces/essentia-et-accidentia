/**
 * create report on last failed builds
 *
 * Scripler parameters:
 * HOURS_BACK (String) - how many hours we're going to dive into past
 * LOG_LINES  (String) - how many last lines of each build log we want to see
 * BUILD_NODE (String) - name of build node we're intersting in (set to empty String for all nodes)
 *
 */

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