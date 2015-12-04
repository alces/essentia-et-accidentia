---
layout: post
title: 'Looking into Logs of the Latest Failed Builds in Jenkins'
date: 2015-11-18 17:38
comments: true
categories: [jenkins, Groovy, scriptler]
---
![](https://upload.wikimedia.org/wikipedia/commons/2/25/Don_Quixote_10.jpg)

When we're doing something significant on our Jenkins server (e.g., deploy new build node(s), upgrade software versions, move jobs' definitions from another server, etc.), mass builds' failures will likely happen. So, wouldn't it be convenient in a case of such sort of events to have a report containing the root causes of all builds' failures for, say, the last hour? It's not difficult to filter all the latest failed build through Jenkins web interface, but this list gives us no information about why all those builds failed. Did the causes of their failures have something in common? Typically, we can answer this question by looking into the last lines of failed builds' console logs, but I don't known how it's possible to view in one browser window more than one log at once. So, let's write a Groovy script printing for us hyperlinks to all the last failed builds along with final portions of their logs.

Our script will start with definition of two constants: how far we're going to dive in the past, and how much lines we want to see (yeah, we all love concise reports, but Java stack traces often becomes quite so long):

```
HOURS_BACK = 2
LOG_LINES = 50
```

If you're planning to run this script using [Scriptler Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Scriptler+Plugin), you'll likely create two script parameters with the same names, but don't forget that Scriplter doesn't know such a thing as an **Interger Parameter**, so you have to create the **String** ones and then convert their values to integer type using something like `new Integer(HOURS_BACK)` - or maybe even `LOG_LINES.toInteger()`

The next constant (or a potential parameter is case of Scriptler) has string value, so no type casting is needed. If you're interested in failed builds on a single node only, put its name here, otherwise assign `BUILD_NODE` to an empty string:

```
BUILD_NODE = 'node01'

import hudson.model.*

nodes = Hudson.instance.computers.findAll {
	! BUILD_NODE || it.name == BUILD_NODE
}
```

Also, let's calculate time where our selection starts in advance, simply for don't calculate it for each build separatelly:

```
startTm = new Date().time - HOURS_BACK * 3600 * 10 ** 3
```

For now, we're ready to get a list of all the recent failures:

```
failed = nodes.collect {
  it.builds.findAll {
    isRecent = it.timeInMillis + it.duration > startTm
  	isRecent && ! it.inProgress && it.result == Result.FAILURE
  }
}.flatten()
```

And, finally, let's print out this list in a form convenient to read by a human being:

```
println failed.collect {
  	hlink = Hudson.instance.rootUrl + it.url
    ([hlink] + it.getLog(LOG_LINES)).join('\n\t')
}.join('\n\n')
```

Full source code of this script lives on [my github page](https://github.com/alces/essentia-et-accidentia/blob/master/code-samples/lastFailedBuilds.groovy).

Javadoc on Jenkins API can be found [here](http://javadoc.jenkins-ci.org/).

----

Tested against `Jenkins ver. 1.565.3`