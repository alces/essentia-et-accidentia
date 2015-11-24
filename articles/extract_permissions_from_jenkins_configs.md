---
layout: post
title: 'How to Extract User's Permissions From Jenkins Configuration'
date: 2015-11-24 09:56
comments: true
categories: [xml, jenkins, Groovy, authorizationStrategy, XmlSlurper]
---
![](http://uploads5.wikiart.org/images/gustave-dore/don-quixote-3.jpg)

When **Project-based Matrix Authorization Strategy** is used to control users' rights in Jenkins (and in my opinion it's the only usable authorization strategy for medium/large Jenkins installations) an investigation of permissions any given user has can become quite a difficult task. Records about what a user can do are scattered around multiple jobs' configurations and the global one, so there's no convenient way to examine all this mess through web interface. Fortunately, Jenkins has Script Console and [Scriptler Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Scriptler+Plugin) both understanding a slightly outmoded - but still fully-functional - version of Groovy language. We're planning to solve our problem by parsing configuration files of Jenkins itself and of all its jobs. Both kind of files are written in XML and contains information about users' permissions stored in an almost identical format. Of course, Jenkins has [its own API](http://javadoc.jenkins-ci.org/) (fully accessible from scripts written in Groovy), but this API, convenient as it is for other purposes, in part of `hudson.security` module looks pretty enigmatically. Also, Groovy standard library contains excellent `XmlSlurper` turning extraction values from XML into a pretty easy business.

If you're planning to run the following snippets using Scriptler Plugin, a string parameter named **USERNAME** should be added on **Edit Script** page. Also, you've to add this line: 

```
import hudson.model.Hudson
```

to the top of your script. If you'd like to use Script Console instead, simply assign `USERNAME` variable to login a user under investigation has somewhere in the preamble of your script. The `import ...` line in this case becomes optional, because Script Console imports all the classes from a few basic Jenkins-relate modules (including `hudson.model`) automatically.

Jenkins stores data about permissions in an XML structure like this:

```
<hudson.security.AuthorizationMatrixProperty>
  <permission>hudson.model.Item.ExtendedRead:admin</permission>
  <permission>hudson.model.Item.ExtendedRead:anonymous</permission>
  <permission>hudson.model.Run.Update:admin</permission>
  <permission>hudson.model.Run.Delete:admin</permission>
  <permission>hudson.model.Item.Read:admin</permission>
  <permission>hudson.model.Item.Read:anonymous</permission>
  <permission>hudson.model.Item.Cancel:admin</permission>
  <permission>hudson.model.Item.Discover:admin</permission>
  <permission>hudson.model.Item.Configure:admin</permission>
</hudson.security.AuthorizationMatrixProperty>
```

The (intentionally oversimplified) example above is taken from a jobs' configuration file. As one can see, `anonymous` has Read and ExtendedRead permissions, while `admin` may also update, delete, cancel, and configure. Let's write first a function (strictly speaking, a closure) to convert this structure into a list of permissions a given user actually has:

```
xml2perms = {userName, xmlData ->
	xmlData.permission.collect {
		it.text().split(':')
	}.findAll {
		it.last() == userName
	}.collect {
		it.first()
	}
}
```

With this function under our belt, getting a list of global users' permissions will be as simple as that:

```
cfgFile = new File(Hudson.instance.root, 'config.xml')
xmlSlrp = new XmlSlurper()

globalPerms = xml2perms(USERNAME,
	xmlSlrp.parse(cfgFile).authorizationStrategy)
```

Working with jobs' configurations is a bit more complicated task (but only a bit). First, let's create a list of all existing configuration files:

```
jobsCfgs = new File(Hudson.instance.root, 'jobs').listFiles().findAll {
	it.directory
}.collect {
	new File(it, 'config.xml')
}.findAll {
	it.exists()
}
```

In the second place, we'll save jobs' names (which are exactly the names of directories containing our configuration files) and pass an interesting portion of these files to our closure:

```
jobsPerms = jobsCfgs.collect {
	[name: it.parentFile.name,
		perms: xml2perms(USERNAME, 
			xmlSlrp.parse(it).properties.'hudson.security.AuthorizationMatrixProperty')]
}
```

Finally, let's filter out those jobs on which our users has no permissions and convert the result to a more nice-looking form by doing something like this:

```
println jobPerms.findAll {
	it.perms
}.collect {
	"$it.name:\n\t${it.perms.join('\n\t')}"
}.join('\n')
```

A full-blown script displaying both the global and job-related permissions (intended to be used from Scriptler) can be found on [my gitlab page](https://github.com/alces/essentia-et-accidentia/blob/master/code-samples/listGlobalAndJobsPermissions.groovy).

----

Tested against `Jenkins ver. 1.565.3`