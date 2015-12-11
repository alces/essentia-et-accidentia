---
layout: post
title: 'Search in the Environments of Jenkins Builds '
date: 2015-12-11 10:39
comments: true
categories: [jenkins, Groovy, scriptler]
---
![](http://uploads5.wikiart.org/images/gustave-dore/don-quixote-1.jpg)

Oftentimes, build servers become depots of the different versions of Ant, Gradle, Maven, JDK, and other tools running during our build process. How could we know which versions of these tools are actually used and which turn into a meaningless clutter? I belive, it's possible to answer this question (and the many other not less interesting questions) by looking into the environment variables which were set during our builds and the values they were set to. But, unfortunately, in Jenkins this information is scattered between an awful lot of pages, and I personally don't know an easy way to collect it in one place by Jenkins web interface. Also, we often need to know not all the values of all the environment variables, but only the values of the particular variable (and, maybe, we're interested only in the values of a special kind.) So, let's write a Groovy script intended to be run by [Scripler Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Scriptler+Plugin) making it possible, so to speak, to grep in the builds' environment variables.

The first thing we're going to do is writing a comment containing Scriptler metadata:

```
/*** BEGIN META {
	"name": "Search in the environment variables",
	"comment": "Search in the latest builds' environment variables",
	"parameters": ["envVarName", "envVarValueRegex"],
	"core": "1.300",
	"authors": [
		{name : "Alexey Zalesnyi"}
	]
} END META**/
```

The most important part of this comment is the list named **parameters** and consisting of the script parameters' names. **envVarName** is the name of the environment variable we're looking for, **envVarValueRegex** is a regular expression for searching in the values of this variable (you can set it to the 'matching everything' `.*` expression, in a case you want to see all the values.) If this kind of comment is found atop a Groovy file, Scriptler Plugin will know how to automatically add parameters to a script. The bad news is that actually Jenkins have to be restarted in order to read script's metadata from the comment (even **Reload Configuration fron Disk** doesn't help.)

I think, it would be wise to restrict our search to only the latest builds, so let's calculate what the date was a week before:

```
weekAgo = new GregorianCalendar()
weekAgo.add(Calendar.DAY_OF_MONTH, -7)
startDate = weekAgo.time
```

As you might be aware, using a bit ugly idiom `new GregorianCalendar().time` is the preffered way of getting the current date in Java. Writing more concise `new Date()` instead is deprecated since a long time. Don't ask me why.

The following closure searches for the matching values of a given environment variable from a given build. It would be possible to don't write it as a separate closure and put its code inline instead, but this way our script will be a bit neater:

```
envVars = {bld ->
  bld.environment.findAll {
    it.key == envVarName && it.value =~ envVarValueRegex
  }.collect {
    it.value
  }.join()
}
```

Next, let's make a flat list of all the builds in the last week:

```
hdsn = hudson.model.Hudson.instance

latestBuilds = hdsn.items.collect {
  it.builds.findAll {
    it.time > startDate
  }
}.findAll {
	it
}.flatten()
```

Then, we're going to filter out from this list those builds in which our environment variable wasn't set to values we're interested in:

```
buildsWithEnvs = latestBuilds.collect {
    [url: hdsn.rootUrl + it.url, env: envVars(it)]
}.findAll {
    it.env
}
```

By this point, all we have to do is to print out the remaining list in a human-readable form (build's URL and a matching value of the environment variable delimited by new lines): 

```
print buildsWithEnvs.collect {
    "$it.url: $it.env"
}.join('\n')
```

Having these script written, we could, for example, set the first parameter to `JAVA_HOME`, the second one to, say, `/j2sdk1\.4`, and find out whether one of our developer teams still builds their deliverables using such an ancient compiller.

The full source of the script can be found on [my github page](https://github.com/alces/essentia-et-accidentia/blob/master/code-samples/environmentVariablesFromBuilds.groovy), the official documentation on Jenkins API on [the project's site](http://javadoc.jenkins-ci.org/).

----

Tested against `Jenkins ver. 1.565.3`